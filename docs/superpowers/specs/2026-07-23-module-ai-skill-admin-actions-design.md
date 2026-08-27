# module-ai 技能编辑删除接口适配层设计

## 背景

`modules/module-ai` 当前是 AI 插件模块壳，主要负责声明插件身份、注册状态技能，并依赖 `ai-agent-spring-boot-starter` 获得智能体运行时和管理能力。技能创建、编辑、删除的核心逻辑已经在 `ai-agent-spring-boot-starter` 的 `AiAgentManagementService` 中实现，现有通用接口路径为 `/api/ai/skills`。

为了让 AI 模块符合平台业务模块接口风格，需要在 `module-ai` 中增加技能管理适配层，对外暴露 `/api/biz/ai/skills` 路径，并统一返回 `R<T>`。适配层只做模块边界、返回体、错误转换和权限标识声明，不重复实现技能业务逻辑。

## 目标

1. 在 `module-ai` 中增加技能相关编辑、删除接口。
2. 对外使用平台业务路径 `/api/biz/ai/skills`。
3. 返回体统一使用 `com.zimo.framework.common.ApiResponse`。
4. 复用 `AiAgentManagementService`，不复制技能创建、编辑、删除逻辑。
5. 自定义 API 技能允许创建、编辑、删除。
6. 内置 Bean 技能继续只读，禁止编辑和删除。
7. 删除 API 技能后继续复用 starter 逻辑，同步解除智能体绑定关系。
8. 补充 `module-ai` 范围内的 Controller 测试。

## 非目标

1. 不迁移或删除 `ai-agent-spring-boot-starter` 现有 `/api/ai/skills` 接口。
2. 不新增技能数据表或迁移脚本。
3. 不改变前端调用路径，前端是否切换到 `/biz/ai/skills` 另行处理。
4. 不允许编辑或删除内置 Bean 技能。
5. 不改变 MCP、A2A、飞书或智能体运行时调用协议。

## 方案选择

采用方案 A：在 `module-ai` 增加轻量管理适配 Controller。

原因：

- 保持 starter 承担通用能力，`module-ai` 承担平台插件模块边界。
- 兼容已有 `/api/ai/skills` 调用，不破坏现有前端。
- 新增路径符合平台业务模块风格，后续前端可以逐步切换。
- 改动范围小，测试边界清晰。

## API 设计

### 查询技能列表

```http
GET /api/biz/ai/skills
```

返回：

```json
{
  "code": 200,
  "msg": "success",
  "data": [
    {
      "name": "remote_quality_check",
      "description": "check quality",
      "readOnly": true,
      "referenceCount": 0,
      "promptTemplateId": null,
      "apiConfig": {
        "apiRegistryId": 1001,
        "enabled": true,
        "baseUrl": "https://api.example.com",
        "path": "/quality",
        "method": "POST",
        "headers": {},
        "timeoutMillis": 3000
      },
      "source": "api",
      "enabled": true,
      "agentId": null
    }
  ]
}
```

### 创建 API 技能

```http
POST /api/biz/ai/skills
```

请求体沿用 `AiManagedSkillRequest`。

返回 `R<AiManagedSkill>`。

### 编辑 API 技能

```http
PUT /api/biz/ai/skills/{name}
```

请求体沿用 `AiManagedSkillRequest`。

规则：

- `{name}` 是技能唯一标识。
- 请求体中的 `name` 允许为空或与路径一致。
- 请求体中的 `name` 不允许修改为其他值。
- 内置 Bean 技能返回业务错误。

### 编辑 API 配置

```http
PUT /api/biz/ai/skills/{name}/api-config
```

请求体沿用 `AiSkillApiConfigRequest`。

用于只更新 API 调用配置、`apiRegistryId`、请求头、超时时间等信息。

### 绑定技能提示词模板

```http
PUT /api/biz/ai/skills/{name}/prompt-template
```

请求体沿用 `AiSkillPromptTemplateRequest`。

用于保持平台路径下的技能提示词模板绑定能力完整。

### 删除 API 技能

```http
DELETE /api/biz/ai/skills/{name}
```

返回：

```json
{
  "code": 200,
  "msg": "success",
  "data": null
}
```

规则：

- 仅允许删除 `source = api` 的自定义技能。
- 内置 Bean 技能返回业务错误。
- 技能不存在返回业务错误。
- 删除成功后由 `AiAgentManagementService.deleteApiSkill(name)` 同步解除智能体绑定。

## 组件设计

### AiSkillAdminController

位置：

`modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/controller/AiSkillAdminController.java`

职责：

- 暴露 `/api/biz/ai/skills` 系列接口。
- 将请求转发给 `AiAgentManagementService`。
- 使用 `R<T>` 包装成功返回。
- 将 starter 抛出的 `IllegalArgumentException` 转换为 `BizException`。
- 对不存在的删除结果转换为 `BizException(404, "API技能不存在")`。

注入方式：

- 使用单一 `public` 构造器注入 `AiAgentManagementService`。

权限边界：

- 本次不让 `module-ai` 反向依赖 `module-sys`，避免业务模块之间产生强耦合。
- 接口先通过 `/api/biz/ai/skills` 路径进入 API 注册表，后续由系统权限配置页面绑定权限标识。
- 如后续需要注解式权限，优先把通用权限注解下沉到 `framework`，再由各业务模块共同依赖。


### AiSkillAdminAutoConfiguration

位置：

`modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiSkillAdminAutoConfiguration.java`

职责：

- 在 `AiAgentManagementService` 存在时注册 `AiSkillAdminController`。
- 使用 `@AutoConfiguration(after = AiAgentAutoConfiguration.class)`，确保 starter 管理服务先注册。
- `AiModuleAutoConfiguration` 只保留插件基础 Bean，不再通过全包 `ComponentScan` 扫描 Controller，避免 module-ai 单独加载时因可选管理服务缺失导致上下文启动失败。
## 错误处理

Controller 不直接返回裸异常。

- `IllegalArgumentException` 转换为 `BizException(400, message)`。
- 删除返回 `false` 时转换为 `BizException(404, "API技能不存在")`。
- 其他异常交给 `admin-shell` 的 `GlobalExceptionHandler` 返回 `R.fail(500, "服务器内部错误")`。

前端可从统一返回体的 `msg` 字段读取错误信息。

## 测试设计

新增：

`modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/controller/AiSkillAdminControllerTest.java`

覆盖：

1. `GET /api/biz/ai/skills` 返回 `R` 包装后的技能列表。
2. `PUT /api/biz/ai/skills/{name}` 调用 `AiAgentManagementService.updateApiSkill` 并返回更新结果。
3. `DELETE /api/biz/ai/skills/{name}` 调用 `deleteApiSkill`，成功时返回 `R.ok()`。
4. 删除不存在技能时返回业务错误。
5. 编辑或删除内置技能时，starter 抛出的 `IllegalArgumentException` 能转换为业务错误。

测试使用 `MockMvcBuilders.standaloneSetup` 和 mock 的 `AiAgentManagementService`，避免启动完整上下文。

## 验收标准

1. `module-ai` 编译通过。
2. 新增 `/api/biz/ai/skills` 查询、创建、编辑、删除接口。
3. 所有接口返回 `R<T>`。
4. API 技能编辑、删除转调 starter 服务。
5. 内置技能编辑、删除返回业务错误。
6. 删除不存在技能返回业务错误。
7. 新增 Controller 测试通过。
8. 不影响现有 `/api/ai/skills` 接口。

## 自检

- 没有新增数据库结构，符合“不使用本地数据库”的仓库规则。
- 没有修改前端端口或后端端口。
- 没有重复实现技能注册、持久化和绑定解除逻辑。
- 设计范围集中在 `module-ai` 后端适配层，适合后续单独实施计划。
