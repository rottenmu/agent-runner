# AI 管理能力迁移至 module-ai 设计

## 1. 背景

`ai-agent-spring-boot-starter` 当前同时承担通用 AI 运行时和 AI 管理业务两类职责：

- 通用运行时：大模型对话、会话记忆、技能注册、渠道处理、MCP、A2A 和 AgentScope 状态检查；
- 管理业务：智能体、API 技能配置和提示词模板的 Controller、Service、Repository、JDBC 实现及 MySQL 表初始化。

智能体、技能和提示词模板的创建、编辑、删除、绑定与持久化属于 `module-ai` 业务插件，不应由可复用 starter 承担。当前 starter 的 `AiAgentService`、`AiChannelHandler` 又直接依赖管理包中的智能体模型和管理服务，若直接搬移现有类会形成 `ai-agent-spring-boot-starter` 与 `module-ai` 的循环依赖。

本次调整采用运行时 SPI 解耦：starter 只保留中性的智能体运行时契约，完整管理业务迁移到 `module-ai`，DAO 层统一改为 MyBatis-Plus，数据库只支持 MySQL。

## 2. 已确认决策

- 仅迁移智能体、技能、提示词模板的管理接口及其 Service、DAO。
- 对话、技能运行时、渠道、MCP 和 A2A 继续保留在 starter。
- 模块依赖保持单向：`module-ai -> ai-agent-spring-boot-starter`。
- 管理接口统一使用 `/api/biz/ai/**`。
- 管理接口统一返回 `R<T>`，业务异常统一使用 `BizException`。
- 删除 starter 原有 `/api/ai/agents`、`/api/ai/skills`、`/api/ai/prompt-templates` 管理接口。
- `/api/ai/mcp` 和 `/api/ai/a2a/**` 保持不变。
- 持久化统一使用 MyBatis-Plus 3.5.9，不保留 Spring JDBC Repository CRUD。
- 数据库只面向 MySQL，不引入 H2、SQLite 或其他本地数据库。
- 同步检查并修改智能体管理、技能管理和提示词模板管理页面的 API 调用。

## 3. 目标

- starter 只包含可被多个业务插件复用的 AI 基础能力和最小运行时 SPI。
- `module-ai-core` 接管三类管理业务的 Controller、DTO/VO、Service、Entity 和 Mapper。
- `module-ai-autoconfig` 接管 Mapper 扫描、管理 Bean 装配、运行时适配器和 MySQL Schema 初始化。
- 三张现有 MySQL 表的业务 CRUD 全部通过 MyBatis-Plus Mapper 完成。
- 现有 MySQL 表名、字段和数据保持兼容，不重建或清空表。
- 管理前端统一调用新平台路径并正确解包 `R.data`。
- starter 在没有 `module-ai` 时仍能独立提供对话、技能、渠道、MCP 和 A2A 能力。

## 4. 非目标

- 不迁移 MCP、A2A、Channel、聊天客户端或 AgentScope 运行时。
- 不实现模型自动选择技能、工具调用循环或 `maxIters` 调度。
- 不改变提示词模板生成器当前的确定性草稿生成行为。
- 不扩大提示词模板绑定在运行时的应用范围。
- 不改变智能体管理页面的视觉结构和交互布局。
- 不改变 MCP/A2A 工作台的请求和响应协议。
- 不为旧管理路径或旧 `com.zimo.starter.ai.management` 包保留兼容桥接类。
- 不引入 XML Mapper；现有查询使用 MyBatis-Plus 内置能力和 Wrapper 完成。
- 不复制、记录或提交数据库密码、AI Key、远程 API 凭据等敏感配置。

## 5. 方案比较

### 5.1 采用方案：运行时 SPI 解耦

starter 定义不包含 CRUD 和持久化语义的智能体运行时契约，`module-ai` 查询管理数据后实现该契约。starter 的对话和渠道组件只依赖 SPI。

优点：

- 不产生循环依赖；
- starter 可以脱离 `module-ai` 单独使用；
- 管理 DTO、Entity、Mapper、Service 和 Controller 全部归属业务插件；
- 后续替换管理实现不会侵入 AI 运行时。

### 5.2 未采用：管理数据类型继续保留在 starter

该方案改动较少，但会让管理领域继续泄漏到 starter，与完整迁移管理能力的目标冲突。

### 5.3 未采用：Channel 和对话服务一并迁入 module-ai

该方案隔离最彻底，但会扩大飞书、项目管理等业务插件的改动面，削弱 starter 的通用渠道能力，超出本次范围。

## 6. 模块边界

### 6.1 ai-agent-spring-boot-starter

保留：

- `AiAgentService`、`AiAgentProperties` 和回复模型；
- `AiChatClient`、OpenAI 兼容客户端和内存会话；
- `AiSkill`、`AiApiSkill`、`AiSkillRegistry` 和默认技能；
- `AiChannelHandler`、渠道消息模型和意图处理 SPI；
- MCP、A2A；
- AI 运行时状态和工厂；
- 新增的中性智能体运行时 SPI。

移除：

- `com.zimo.starter.ai.management` 下的全部管理代码；
- 管理 Controller、Service、Repository、JDBC 实现和管理 Schema 初始化 Bean；
- `AiAgentAutoConfiguration` 中所有管理业务装配；
- 迁移后仅由管理业务使用的 `spring-jdbc` 依赖。

### 6.2 module-ai-core

承接或新增：

- 智能体管理 Controller、DTO/VO、Service、Entity、Mapper；
- API 技能管理 Controller、DTO/VO、Service、Entity、Mapper；
- 提示词模板管理 Controller、DTO/VO、Service、生成器、Entity、Mapper；
- Entity 与 API/运行时模型之间的转换器；
- API 技能运行时注册、更新、移除和失败补偿逻辑；
- 智能体默认渠道查询和运行时 Profile 转换。
- 管理侧 `AiManagedAgentContributor` 扩展点，用于其他业务模块贡献完整预置智能体。

建议包结构：

```text
com.zimo.module.ai
├─ agent
│  ├─ controller
│  ├─ dto
│  ├─ entity
│  ├─ mapper
│  ├─ service
│  └─ support
├─ skill
│  ├─ controller
│  ├─ dto
│  ├─ entity
│  ├─ mapper
│  ├─ service
│  └─ support
└─ prompt
   ├─ controller
   ├─ dto
   ├─ entity
   ├─ mapper
   ├─ service
   └─ support
```

按业务能力组织代码，避免把所有 Controller、Entity 或 Mapper 堆入单一技术分层目录。

### 6.3 module-ai-autoconfig

负责：

- 使用 `@MapperScan` 扫描 `module-ai-core` 的三个 Mapper 包；
- 装配管理 Service、Controller、转换器和提示词生成器；
- 装配 `AiAgentProfileResolver` 的 MyBatis-Plus 实现；
- 在存在 MySQL `DataSource` 时执行 Schema 初始化；
- 在管理 Service 可用后恢复持久化的 API 技能到 starter 的 `AiSkillRegistry`；
- 继续装配 `AiPluginRegister` 和 `AiPluginStatusSkill`。

## 7. starter 运行时 SPI

starter 新增中性运行时模型：

```java
public record AiAgentProfile(
        String id,
        String name,
        String modelName,
        String systemPrompt,
        List<String> skillIds) {
}
```

starter 新增默认智能体解析接口：

```java
public interface AiAgentProfileResolver {
    Optional<AiAgentProfile> resolveDefaultForChannel(String channel);
}
```



使用关系：

- `AiAgentService#chat` 接收可选 `AiAgentProfile`，不再接收管理实体；
- `AiChannelHandler` 通过可选 `AiAgentProfileResolver` 查询渠道默认智能体；
- `AiChannelIntentHandler` 接收可选 `AiAgentProfile`；
- `module-ai` 查询启用且匹配默认渠道的智能体并转换为 Profile；
- `skillIds` 作为渠道意图路由所需的最小运行时字段保留在 Profile 中；
- 完整的 `AiManagedAgentContributor` 迁入 `module-ai-core`；
- 项目管理等需要贡献完整管理定义的业务模块使用“业务模块 -> module-ai-core -> starter”单向依赖，不会形成 starter 反向依赖。

starter 独立运行且没有 Resolver 时，渠道处理器直接使用 `AiAgentProperties` 中的默认名称、模型和系统提示词。

## 8. MyBatis-Plus 持久化设计

### 8.1 Entity

新增三类持久化实体：

- `AiAgentEntity` 对应 `ai_managed_agent`；
- `AiSkillConfigEntity` 对应 `ai_agent_skill_config`；
- `AiPromptTemplateEntity` 对应 `ai_prompt_template`。

映射约定：

- 表名使用 `@TableName`；
- MySQL 自增主键使用 `@TableId(type = IdType.AUTO)`；
- 字符串智能体 ID 使用 `@TableId(type = IdType.INPUT)`；
- 逻辑删除字段使用 `@TableLogic`；
- 下划线字段按项目 MyBatis-Plus 全局配置映射；
- `skillIds`、`defaultChannels` 和请求头等结构化字段使用 MyBatis-Plus JSON TypeHandler；
- Entity 只负责数据库映射，不直接作为 Controller 响应。

### 8.2 Mapper

```java
public interface AiAgentMapper extends BaseMapper<AiAgentEntity> {
}

public interface AiSkillConfigMapper extends BaseMapper<AiSkillConfigEntity> {
}

public interface AiPromptTemplateMapper extends BaseMapper<AiPromptTemplateEntity> {
}
```

查询和更新使用：

- `selectById`
- `selectOne`
- `selectList`
- `insert`
- `updateById`
- `deleteById`
- `LambdaQueryWrapper`
- `LambdaUpdateWrapper`

Mapper 不包含业务校验、远程 API 调用或内存技能注册逻辑。

### 8.3 Service

Service 使用普通 public 构造器注入 Mapper，每个 Service Bean 只保留一个 public 构造器。Service 不使用字段注入，不新增测试专用构造器。

职责划分：

- `AiAgentService`：智能体 CRUD、启停、技能绑定、默认渠道解析、运行时 Profile 转换；
- `AiSkillManagementService`：Bean/API 技能列表、API 技能 CRUD、提示词绑定、敏感请求头处理、运行时注册表同步；
- `AiPromptTemplateService`：模板分类查询、CRUD、校验、草稿生成和逻辑删除。

涉及多次数据库写入的方法使用 `@Transactional`，例如删除 API 技能并清理所有智能体中的技能绑定。

### 8.4 Schema 初始化

MyBatis-Plus 不负责 DDL。现有 MySQL 建表和兼容补列逻辑迁入 `module-ai-autoconfig`，并补齐 `ai_managed_agent` 表初始化。

Schema 初始化约束：

- 只支持 MySQL DDL；
- 表已存在时只补齐明确需要的兼容列；
- 不删除、不清空、不重建现有表；
- 不通过 Mapper 执行启动 DDL；
- 业务 CRUD 不使用 `JdbcTemplate`；
- 初始化 Bean 只在存在 `DataSource` 且 `plugin.ai.enabled=true` 时装配；
- 管理 Service 在 Schema 初始化完成后才允许加载种子智能体和持久化 API 技能。

## 9. 管理 API

管理接口统一如下：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/biz/ai/agents` | 查询智能体 |
| POST | `/api/biz/ai/agents` | 新增智能体 |
| PUT | `/api/biz/ai/agents/{id}` | 更新智能体 |
| DELETE | `/api/biz/ai/agents/{id}` | 逻辑删除智能体 |
| GET | `/api/biz/ai/skills` | 查询 Bean/API 技能 |
| POST | `/api/biz/ai/skills` | 新增 API 技能 |
| PUT | `/api/biz/ai/skills/{name}` | 更新 API 技能 |
| PUT | `/api/biz/ai/skills/{name}/api-config` | 更新 API 调用配置 |
| PUT | `/api/biz/ai/skills/{name}/prompt-template` | 绑定或清空技能模板 |
| DELETE | `/api/biz/ai/skills/{name}` | 逻辑删除 API 技能 |
| GET | `/api/biz/ai/prompt-templates` | 查询模板，可按 `templateType` 筛选 |
| POST | `/api/biz/ai/prompt-templates` | 新增模板 |
| PUT | `/api/biz/ai/prompt-templates/{id}` | 更新模板 |
| DELETE | `/api/biz/ai/prompt-templates/{id}` | 逻辑删除模板 |
| POST | `/api/biz/ai/prompt-templates/generate` | 生成未入库的 CoSTAR 草稿 |

契约约定：

- 成功响应统一使用 `R<T>`；
- 删除成功返回 `R<Void>`；
- 参数错误抛出业务码 `400` 的 `BizException`；
- 智能体、技能或模板不存在时抛出业务码 `404` 的 `BizException`；
- MyBatis-Plus 数据访问异常交由项目全局异常处理器；
- Controller 只负责 HTTP 参数适配和统一返回，不复制 Service 业务逻辑。

旧管理路径和 starter 管理 Controller 直接删除，不保留双路径。

## 10. 前端接口检查与调整

### 10.1 当前状态

`frontend/web-shell/src/api/request.js` 的 Axios `baseURL` 为 `/api`。因此 `frontend/modules/ai/src/api/agent.js` 当前使用的 `/ai/agents` 等相对路径，实际请求为 `/api/ai/**`。

当前页面存在以下兼容问题：

- `AiAgentManage.vue` 把 `listAgents()` 和 `listSkills()` 的返回值直接当数组；
- 创建或更新后把完整响应直接当智能体或技能对象；
- `AiPromptTemplateManage.vue` 局部使用 `unwrapData`，解包策略分散；
- `AiSkillAdminController` 已返回 `R<T>`，但管理页面仍调用 starter 旧路径，因此新适配 Controller 没有真正承接页面流量。

### 10.2 调整方案

`frontend/modules/ai/src/api/agent.js` 统一：

- 路径改为 `/biz/ai/agents`、`/biz/ai/skills`、`/biz/ai/prompt-templates`；
- API 函数统一解包 `R.data`；
- 删除接口只等待成功，不向页面暴露响应包装；
- 页面层只接收业务对象、数组或空值。

`AiAgentManage.vue`：

- 按业务数组加载智能体和技能；
- 创建、更新和绑定后直接使用解包后的业务对象；
- 保留现有请求字段：`promptTemplateId`、`skillIds`、`defaultChannels`、`agentId`、`apiConfig`；
- 保留技能删除后刷新或清理智能体绑定的页面行为。

`AiPromptTemplateManage.vue`：

- 移除页面内重复的通用 `R.data` 解包；
- 保留列表兼容函数只处理业务分页或列表结构；
- 生成、保存、删除全部使用 API 层已解包结果。

`AiWorkbench.vue`：

- `/api/ai/mcp` 保持不变；
- `/api/ai/a2a/message` 保持不变。

## 11. 数据流

### 11.1 管理请求

```text
管理页面
  -> /api/biz/ai/**
  -> module-ai Controller
  -> module-ai Service
  -> MyBatis-Plus Mapper
  -> MySQL
```

### 11.2 渠道消息

```text
渠道消息
  -> starter AiChannelHandler
  -> starter AiAgentProfileResolver
  -> module-ai MyBatis-Plus Resolver 实现
  -> starter AiAgentService
  -> OpenAI 兼容模型接口
```

### 11.3 API 技能管理

```text
技能管理请求
  -> module-ai AiSkillManagementService
  -> AiSkillConfigMapper 持久化
  -> starter AiSkillRegistry 注册、更新或移除运行时技能
```

## 12. 一致性与安全

- 新增 API 技能时先完成配置校验，再持久化并注册运行时技能；注册失败时抛出异常并回滚数据库事务。
- 更新 API 技能前保存原运行时配置；更新失败时恢复原配置。
- 删除 API 技能时逻辑删除技能配置，同一事务内清理智能体技能绑定，再从运行时注册表移除。
- Bean 技能只允许查询和绑定提示词，不允许编辑或删除。
- 敏感请求头返回前按 authorization、token、secret、key 等名称脱敏为 `[redacted]`。
- 更新请求中的 `[redacted]` 表示保留原请求头值。
- 远程 API 技能调用异常继续由 starter 转换为 `AiSkillResult.fail`。
- 不在异常、日志、文档、测试数据或提交信息中输出真实凭据。

## 13. 自动配置顺序

推荐顺序：

1. starter 创建对话、技能注册表、渠道、MCP/A2A 等基础 Bean；
2. `module-ai-autoconfig` 在存在 MySQL `DataSource` 时完成三张表初始化；
3. MyBatis-Plus 扫描并创建 Mapper；
4. 创建三个管理 Service；
5. 加载默认智能体、`AiManagedAgentContributor` 贡献的智能体和持久化 API 技能；
6. 注册 `AiAgentProfileResolver`；
7. 注册 `/api/biz/ai/**` 管理 Controller。

通过显式 Bean 依赖或独立自动配置类的 `before`、`after` 顺序保证 Service 不会在表初始化前查询数据库。

## 14. 测试策略

### 14.1 starter

- 验证不再包含或装配管理 Controller、Service、Repository、Mapper 和 Schema 初始化器；
- 验证没有 Resolver 时普通对话和渠道处理仍可工作；
- 验证存在 Resolver 时渠道默认智能体的名称、模型和系统提示词生效；
- 验证 `AiChannelIntentHandler` 使用新的 `AiAgentProfile`；
- 保留 MCP、A2A、聊天客户端和技能注册测试；
- 验证 starter POM 不依赖 `module-ai-core`。

### 14.2 module-ai-core

- Entity 元数据测试：表名、主键类型、逻辑删除、JSON TypeHandler；
- Service 单元测试：CRUD、字段校验、逻辑删除、绑定关系；
- API 技能运行时注册、更新、删除和失败补偿测试；
- 敏感请求头脱敏与 `[redacted]` 保留语义测试；
- 默认渠道智能体解析测试；
- Controller MockMvc 测试：路径、请求体、`R<T>`、业务码 `400/404`；
- 不使用 H2、SQLite；
- Mapper 单元测试使用 Mock，不把内置 CRUD 重复实现为自定义 SQL。

### 14.3 module-ai-autoconfig

- 验证 Mapper 扫描；
- 验证三个管理 Service、Resolver 和 Controller 被装配；
- 验证禁用 `plugin.ai` 后管理 Bean 不装配；
- 验证不存在 DataSource 时不会创建依赖持久化的管理 Bean；
- 验证 Schema 初始化先于数据加载；
- 验证启动时恢复持久化 API 技能。

### 14.4 前端

- 静态测试确保管理 API 全部使用 `/biz/ai/**`；
- 测试 `agent.js` 统一返回 `R.data`；
- 测试智能体、技能和模板列表加载的数据结构；
- 测试创建、更新、删除、模板绑定的数据结构；
- 静态测试确保 `AiWorkbench.vue` 的 MCP/A2A 路径未改变；
- 运行 AI 前端模块测试和 `web-shell` 构建。

### 14.5 受影响模块

- 项目管理模块的渠道意图处理改用新的智能体运行时 SPI；
- 项目管理模块依赖 `module-ai-core`，通过迁移后的 `AiManagedAgentContributor` 贡献完整管理定义；
- 飞书模块验证 `AiChannelHandler` 新构造依赖和默认智能体路由；
- `admin-shell` 验证 `module-ai-autoconfig` 组合装配。

## 15. 验证顺序

1. `mvn -pl modules/ai-agent-spring-boot-starter test`
2. `mvn -pl modules/module-ai/module-ai-core -am test`
3. `mvn -pl modules/module-ai/module-ai-autoconfig -am test`
4. 运行项目管理和飞书受影响模块的最窄测试
5. 运行 `frontend/modules/ai/tests` 下的相关 Node 测试
6. `npm run build` 验证 `frontend/web-shell`
7. `mvn -pl admin-shell -am package`
8. 共享契约受影响范围超出上述模块时，再运行 `mvn clean verify`

所有数据库相关验证只针对 MySQL，不启动 H2 或 SQLite。

## 16. 实施边界

- 先以测试固定 starter 新运行时 SPI，再迁移管理实现。
- 三组管理能力按智能体、技能、提示词模板分别迁移，完成一组即执行对应测试。
- 现有 `AiSkillAdminController` 调整为直接依赖 `module-ai` 的技能管理 Service，不再适配 starter 管理服务。
- 测试与生产代码同步迁移，避免同时存在两套管理实现。
- 最后删除 starter 的 `management` 包、旧自动配置和无用依赖。
- 同步更新中文模块文档和管理接口清单。
- 不覆盖、不回退、不提交工作区内与本次任务无关的改动。

## 17. 验收标准

- starter 中不存在 `com.zimo.starter.ai.management`。
- starter 不装配任何管理 CRUD、管理数据库或管理 HTTP Bean。
- starter 不依赖 `module-ai-core`，模块依赖不存在环。
- `module-ai-core` 提供三组完整管理能力。
- 三张管理表的业务 CRUD 全部通过 MyBatis-Plus Mapper 完成。
- `ai_managed_agent`、`ai_agent_skill_config`、`ai_prompt_template` 表及现有数据兼容。
- 管理 API 全部位于 `/api/biz/ai/**` 并返回 `R<T>`。
- 旧 `/api/ai/agents|skills|prompt-templates` 不再存在。
- `/api/ai/mcp` 和 `/api/ai/a2a/**` 行为保持不变。
- 智能体管理页面、技能管理页面、提示词模板页面调用新接口并正确解包响应。
- 未引入 H2、SQLite，未提交任何凭据。
- starter、module-ai、受影响业务模块、前端构建和 admin-shell 组合验证通过。
