# 智能体绑定飞书渠道设计

## 1. 背景

当前系统已经具备以下能力：

- `module-ai` 管理智能体、技能、提示词模板和模型配置；
- `module-feishu` 管理多条飞书应用配置，但同一时间只允许一条配置启用；
- 飞书消息通过 `FeishuAiChannelMessageHandler` 转换为 `AiChannelMessage`；
- AI 运行时目前只能按渠道编码 `feishu` 查找默认智能体。

现有 `defaultChannels` 只能表达“某个智能体是飞书渠道的全局默认智能体”，不能表达“某条飞书应用配置绑定哪个智能体”。本次增加独立管理页面，使每条飞书配置可以绑定一个智能体，并让绑定结果参与实际消息路由。

## 2. 目标

- 在 `frontend/modules/ai` 增加“飞书渠道绑定”页面。
- 支持按“飞书配置（应用）→ 智能体”建立一对一绑定。
- 每条飞书配置最多绑定一个智能体，一个智能体可以绑定多条飞书配置。
- 允许解除绑定。
- 飞书消息优先路由到当前启用飞书配置所绑定的智能体。
- 绑定缺失或绑定智能体不可用时，回退到现有飞书渠道默认智能体。
- 保持 `module-feishu` 与 `module-ai` 的模块边界，不建立两者之间的直接 Maven 依赖。
- 数据库结构只提供 MySQL 变更 SQL，不增加数据库初始化器。

## 3. 方案选择

### 3.1 采用方案

在 `ps_feishu_config` 增加 `agent_id` 字段，由飞书配置记录持有智能体标识。

选择该方案的原因：

- 绑定关系的基数与飞书配置一致，无需额外关联表；
- 启用飞书配置时可以直接获得绑定智能体标识；
- `module-feishu` 只保存和传递不透明的 `agent_id`，不需要依赖 `module-ai`；
- 前端页面可以分别调用飞书配置接口和智能体接口完成展示与选择。

### 3.2 未采用方案

#### 使用智能体 `defaultChannels`

该字段只能按 `feishu` 渠道类型匹配，无法区分多条飞书应用配置，因此不满足本次绑定粒度。

#### 在 `module-ai` 新建通用渠道绑定表

该方案扩展性更强，但需要同步飞书配置标识、租户信息和启用状态，并引入跨模块校验与查询，当前需求下复杂度偏高。

## 4. 数据模型

为 `ps_feishu_config` 增加可空字段：

```sql
ALTER TABLE ps_feishu_config
    ADD COLUMN agent_id VARCHAR(64) NULL COMMENT '绑定的智能体ID' AFTER enabled;
```

约束说明：

- 不建立数据库外键，避免飞书模块数据库结构依赖 AI 管理表；
- 智能体被停用或删除后，原绑定值可以保留，但运行时不得使用该智能体；
- 页面将失效绑定展示为异常状态，并允许重新绑定或解除绑定；
- 不增加 `CREATE TABLE` 或 `ALTER TABLE` 自动初始化逻辑。

## 5. 后端设计

### 5.1 飞书配置领域

在以下对象中增加 `agentId`：

- `FeishuConfigEntity`
- `FeishuConfigResponse`

普通飞书配置新增、编辑接口不修改现有绑定，避免用户编辑 App ID、密钥等信息时意外清空绑定。

新增绑定请求对象，仅包含：

```json
{
  "agentId": "a123"
}
```

`agentId` 允许为 `null` 或空白，表示解除绑定。

### 5.2 绑定接口

新增接口：

```text
PUT /api/biz/feishu/config/{id}/agent-binding
```

响应继续使用统一的 `R<FeishuConfigResponse>`。

接口职责：

- 校验飞书配置存在；
- 规范化 `agentId`，空白值保存为 `null`；
- 更新且仅更新当前飞书配置的绑定字段；
- 返回脱敏后的飞书配置数据。

飞书模块不直接校验智能体是否存在或启用。该校验由管理页面可选项和运行时解析共同保证，以避免 `module-feishu` 依赖 `module-ai`。

### 5.3 AI 运行时解析契约

将 `AiAgentProfileResolver` 扩展为可以根据完整 `AiChannelMessage` 解析智能体，同时保留原按渠道解析方法的兼容能力。

建议契约：

```java
Optional<AiAgentProfile> resolveForMessage(AiChannelMessage message);
```

兼容规则：

- 默认实现仍调用 `resolveDefaultForChannel(message.channel())`；
- 现有只实现按渠道解析的业务代码无需立即改造；
- `AiChannelHandler` 改为把完整消息交给解析器。

### 5.4 飞书消息上下文

飞书消息进入 AI 通道前，把当前启用飞书配置的 `agentId` 放入消息属性：

```text
agentId
```

`module-feishu` 只负责取得并传递该值，不读取 AI 管理数据。

### 5.5 智能体解析顺序

`module-ai` 中的解析器按以下顺序处理：

1. 当渠道为 `feishu` 且消息属性包含 `agentId` 时，按 ID 查找智能体；
2. 仅当智能体存在且处于启用状态时返回该智能体；
3. 未绑定、绑定记录失效或智能体停用时，查找 `defaultChannels` 包含 `feishu` 的启用智能体；
4. 仍未找到时保持现有无指定智能体的默认聊天行为。

此降级策略保证历史数据尚未绑定或绑定对象失效时，飞书消息仍可使用原有路径。数据库变更 SQL 仍须在部署新版应用前执行。

## 6. 前端页面设计

### 6.1 路由与菜单

- 路由：`/ai/feishu-bindings`
- 路由名称：`AiFeishuBindingManage`
- 菜单名称：`飞书渠道绑定`
- 页面文件：`frontend/modules/ai/src/views/AiFeishuBindingManage.vue`

### 6.2 页面结构

页面顶部提供：

- 标题和简要说明；
- 飞书配置名称或 App ID 搜索；
- 刷新按钮。

主体使用表格展示：

- 配置名称；
- App ID；
- 租户名称；
- 配置启用状态；
- 凭据状态；
- 当前绑定智能体；
- 绑定状态；
- 操作。

操作区使用可搜索下拉菜单列出已启用智能体，并提供“解除绑定”操作。

### 6.3 数据来源

页面调用：

```text
GET /biz/feishu/config/page
GET /biz/ai/agents
PUT /biz/feishu/config/{id}/agent-binding
```

智能体下拉菜单只展示 `enabled=true` 的智能体。

如果飞书配置返回的 `agentId` 不在当前智能体列表中，页面显示“绑定已失效”，不把失效值伪装为正常选项。

### 6.4 交互规则

- 保存绑定前要求用户选择智能体；
- 修改绑定后立即刷新当前行；
- 解除绑定需要二次确认；
- 启用中的飞书配置在页面中突出显示；
- 请求失败时保留原页面数据并展示后端错误信息；
- 页面不展示飞书密钥、验证令牌或加密密钥。

## 7. 模块边界

依赖方向保持如下：

```text
module-feishu ──传递 agentId──> ai-agent-spring-boot-starter
                                      │
                                      ▼
                               module-ai 解析器
```

- `module-feishu` 不引用 `module-ai` 的 Service、DAO 或实体；
- `module-ai` 不引用 `module-feishu` 的实体或 Mapper；
- 前端 AI 页面可以调用两个业务模块已经公开的 HTTP API；
- starter 仅承载通用消息解析契约，不承载飞书业务字段。

## 8. 异常与兼容处理

- 未执行 SQL：应用访问新增字段时会由 MySQL 明确报错，部署前必须先执行变更 SQL；
- 飞书配置未绑定：使用 `feishu` 渠道默认智能体；
- 绑定智能体不存在、已删除或停用：使用 `feishu` 渠道默认智能体；
- 飞书渠道默认智能体也不存在：保持现有通用 AI 聊天降级行为；
- 多条飞书配置存在：每条配置独立保存绑定关系，只有当前启用配置参与运行时路由；
- 切换启用配置：下一条进入的消息自动使用新启用配置的绑定，无需重启。

## 9. 测试范围

### 9.1 后端测试

- 飞书配置绑定接口能够保存、替换和解除 `agentId`；
- 编辑飞书配置不会清空原绑定；
- 飞书配置响应包含 `agentId`，且敏感字段仍保持脱敏；
- 飞书消息把当前配置的 `agentId` 传入 `AiChannelMessage.attributes`；
- AI 解析器优先使用消息指定智能体；
- 指定智能体不存在、删除或停用时回退渠道默认智能体；
- 非飞书渠道保持原有解析行为；
- starter 兼容只按渠道实现的解析器。

### 9.2 前端测试

- 菜单与路由正确注册；
- 页面正确请求飞书配置和智能体列表；
- 下拉菜单只展示已启用智能体；
- 能保存绑定并解除绑定；
- 能识别并展示失效绑定；
- 页面不渲染任何飞书敏感凭据。

## 10. 验证命令

优先执行最窄范围验证：

```text
mvn -pl modules/module-feishu/module-feishu-core,modules/ai-agent-spring-boot-starter,modules/module-ai/module-ai-core -am test
node --test frontend/modules/ai/tests/*.test.mjs
```

契约改动验证通过后，再执行：

```text
mvn -pl admin-shell -am package
npm run build
```

前端构建命令在 `frontend/web-shell` 目录执行。
