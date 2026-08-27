# AI 模型配置管理后端与前端对接设计

## 1. 背景

当前 AI 前端模块已经新增 `frontend/modules/ai/src/views/AiModelConfigManage.vue` 页面，支持阿里云百炼和自定义模型配置的本地管理。该页面目前使用 `localStorage` 持久化，适合前端原型验证，但无法满足后端统一管理、多端共享、智能体运行时读取模型配置等需求。

用户已确认采用方案 A：在 `module-ai-core` 中增加模型管理后端代码，并生成 Markdown 格式 API 文档，同时把 `ai-module-config-frontend` 前端页面改造成后端 API 驱动。

## 2. 目标

- 在 `modules/module-ai/module-ai-core` 中新增模型配置管理后端能力。
- 使用 MySQL 表 `ai_model_config` 持久化模型配置。
- 通过 `module-ai-autoconfig` 自动装配 Schema 初始化、Repository、Service、Controller。
- 对外暴露平台统一接口：`/api/biz/ai/model-configs`。
- 生成中文 API 文档：`modules/module-ai/module-ai-core/API_MODEL_CONFIG_SPEC.md`。
- 前端 `frontend/modules/ai/src/views/AiModelConfigManage.vue` 改为调用后端 API，不再以 `localStorage` 作为主存储。
- 保留现有前端交互：配置列表、模型模板、新建、编辑、复制、删除、连接测试、导入导出、环境筛选。
- API Key 作为敏感信息处理：允许写入，响应不返回明文。

## 3. 非目标

- 不修改前端开发端口 `15200`。
- 不创建独立前端应用。
- 不引入 SQLite、H2 或 Flyway。
- 不真实调用阿里云百炼接口做在线连通性验证，本期连接测试仍做后端模拟校验。
- 不把 API Key 明文写入文档、日志、提交信息或前端导出文件。
- 不改造智能体运行时模型选择逻辑，本期只完成配置管理和前端对接。

## 4. 总体架构

本能力作为 AI 模块的业务能力落在 `module-ai-core`，通过 `module-ai-autoconfig` 注册 Bean。

后端分层：

- Controller：负责 HTTP 路径、统一返回体、参数入口。
- Service：负责业务校验、密钥保留规则、复制、测试连接、导入导出数据处理。
- Repository：负责 MySQL 表读写。
- SchemaInitializer：启动时执行 `CREATE TABLE IF NOT EXISTS`，已存在表不重复初始化。
- DTO/VO：隔离请求体、查询参数、响应体，避免实体直接暴露。

前端分层：

- `src/api/model-config.js`：封装后端 API。
- `AiModelConfigManage.vue`：页面状态、表格、表单、导入导出调度。
- `src/model-config/bailianModels.js`：继续保留前端模板模型清单。
- `src/model-config/modelConfigStore.js`：保留为前端表单默认值、导出脱敏、导入数据规范化工具，不再负责主存储。
- `src/model-config/connectionTest.js`：改为删除或停止使用，连接测试走后端接口。

## 5. 数据库设计

新增 MySQL 表：`ai_model_config`。

字段设计：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT UNSIGNED AUTO_INCREMENT | 主键 |
| `config_name` | VARCHAR(128) | 配置名称 |
| `description` | VARCHAR(512) | 描述 |
| `provider` | VARCHAR(32) | 提供商：`bailian`、`custom` |
| `endpoint` | VARCHAR(512) | API Endpoint |
| `api_key` | VARCHAR(1024) | API Key 明文存储，本期不做加密扩展 |
| `model_id` | VARCHAR(128) | 模型 ID |
| `env` | VARCHAR(32) | 环境：`dev`、`staging`、`prod` |
| `enabled` | TINYINT UNSIGNED | 是否启用：0/1 |
| `temperature` | DECIMAL(4,2) | 温度参数 |
| `top_p` | DECIMAL(4,2) | Top P 参数 |
| `max_tokens` | INT | 最大 token 数 |
| `tags_json` | JSON | 标签数组 |
| `last_test_status` | VARCHAR(32) | 测试状态：`success`、`failed`、`untested` |
| `last_test_latency` | INT | 测试耗时毫秒 |
| `last_test_message` | VARCHAR(512) | 测试结果说明 |
| `last_tested_at` | DATETIME | 最近测试时间 |
| `is_deleted` | TINYINT UNSIGNED | 逻辑删除：0/1 |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |

建表要求：

- 使用 `CREATE TABLE IF NOT EXISTS ai_model_config`。
- 不使用 Flyway。
- 不使用 H2/SQLite。
- 启动时只确保表存在，不覆盖已有数据。
- Repository 查询统一追加 `is_deleted = 0`。

## 6. 后端文件设计

### 6.1 module-ai-core 新增文件

- `modelconfig.com.zimo.module.ai.AiModelConfigEntity`
  - 内部持久化实体，不直接返回给前端。
- `modelconfig.com.zimo.module.ai.AiModelConfigQuery`
  - 查询条件：`env`、`provider`、`status`、`keyword`。
- `modelconfig.com.zimo.module.ai.AiModelConfigRequest`
  - 新建/编辑请求体。
- `modelconfig.com.zimo.module.ai.AiModelConfigResponse`
  - 前端响应体，不包含 `apiKey` 明文，只包含 `apiKeyMasked`。
- `modelconfig.com.zimo.module.ai.AiModelConfigTestResponse`
  - 连接测试响应。
- `modelconfig.com.zimo.module.ai.AiModelConfigImportRequest`
  - 批量导入请求。
- `modelconfig.com.zimo.module.ai.AiModelConfigImportResponse`
  - 导入结果：成功数量、跳过数量。
- `modelconfig.com.zimo.module.ai.AiModelConfigRepository`
  - JDBC Repository，构造器接收 `JdbcOperations`。
- `modelconfig.com.zimo.module.ai.AiModelConfigService`
  - 业务服务。
- `com.zimo.module.ai.modelconfig.AiModelConfigSchemaInitializer`
  - MySQL Schema 初始化器。
- `controller.com.zimo.module.ai.AiModelConfigController`
  - REST Controller。

### 6.2 module-ai-autoconfig 新增/修改文件

- 新增 `AiModelConfigAutoConfiguration`
  - 条件：`plugin.ai.enabled=true` 且存在 `DataSource`。
  - 注册 `JdbcTemplate/JdbcOperations`、SchemaInitializer、Repository、Service、Controller。
- 修改 `AutoConfiguration.imports`
  - 增加 `AiModelConfigAutoConfiguration`。
- 修改 `AiModuleAutoConfigurationTest`
  - 验证有数据源时注册模型配置管理 Bean。

### 6.3 pom 依赖

`module-ai-core` 需要补充 JDBC 依赖：

- 推荐使用 `spring-jdbc`。
- 测试继续使用 `spring-boot-starter-test`。

## 7. 后端接口设计

统一返回体：`R<T>`，成功返回：

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```

前端 `request` 的 `baseURL` 是 `/api`，因此：

- 后端真实路径：`/api/biz/ai/model-configs`
- 前端调用路径：`/biz/ai/model-configs`

接口清单：

| 方法 | 后端路径 | 前端调用路径 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/biz/ai/model-configs` | `/biz/ai/model-configs` | 查询配置列表 |
| GET | `/api/biz/ai/model-configs/{id}` | `/biz/ai/model-configs/{id}` | 查询详情 |
| POST | `/api/biz/ai/model-configs` | `/biz/ai/model-configs` | 新建配置 |
| PUT | `/api/biz/ai/model-configs/{id}` | `/biz/ai/model-configs/{id}` | 编辑配置 |
| DELETE | `/api/biz/ai/model-configs/{id}` | `/biz/ai/model-configs/{id}` | 逻辑删除 |
| POST | `/api/biz/ai/model-configs/{id}/copy` | `/biz/ai/model-configs/{id}/copy` | 复制配置 |
| POST | `/api/biz/ai/model-configs/{id}/test` | `/biz/ai/model-configs/{id}/test` | 测试连接 |
| POST | `/api/biz/ai/model-configs/import` | `/biz/ai/model-configs/import` | 批量导入 |
| GET | `/api/biz/ai/model-configs/export` | `/biz/ai/model-configs/export` | 导出当前筛选配置 |

查询参数：

- `env`：`dev`、`staging`、`prod`。
- `provider`：`bailian`、`custom`。
- `status`：`enabled`、`disabled`。
- `keyword`：按名称和模型 ID 模糊搜索。

## 8. 业务规则

### 8.1 校验规则

- `configName` 必填，最大 128 字符。
- `provider` 必填，只允许 `bailian`、`custom`。
- `endpoint` 必填，最大 512 字符。
- `apiKey` 新建时必填。
- `modelId` 必填，最大 128 字符。
- `env` 必填，只允许 `dev`、`staging`、`prod`。
- `temperature` 范围：0 到 1。
- `topP` 范围：0 到 1。
- `maxTokens` 范围：256 到 8192。
- `tags` 最多 20 个，每个标签最大 32 字符。

### 8.2 API Key 响应规则

- 后端保存 `api_key`。
- 所有响应不返回 `apiKey` 明文。
- 响应字段为 `apiKeyMasked`，规则为：
  - 空值返回空字符串。
  - 长度小于等于 8 返回 `***`。
  - 长度大于 8 返回前 4 位 + `****` + 后 4 位。

### 8.3 编辑保留密钥规则

编辑接口中：

- `apiKey` 为空：保留原密钥。
- `apiKey` 等于 `***`：保留原密钥。
- `apiKey` 为其他非空值：覆盖原密钥。

### 8.4 复制规则

复制配置时：

- 复制原配置全部可用字段。
- 名称追加 ` 副本`。
- 创建新 ID。
- 测试结果重置为 `untested`。

### 8.5 连接测试规则

本期后端模拟连接测试，不真实请求模型服务。

成功条件：

- Endpoint、API Key、Model ID 均存在。
- 配置未被逻辑删除。

测试后更新：

- `last_test_status`
- `last_test_latency`
- `last_test_message`
- `last_tested_at`

## 9. 前端对接设计

新增文件：

- `frontend/modules/ai/src/api/model-config.js`

导出函数：

- `listModelConfigs(params)`
- `getModelConfig(id)`
- `createModelConfig(data)`
- `updateModelConfig(id, data)`
- `deleteModelConfig(id)`
- `copyModelConfig(id)`
- `testModelConfig(id)`
- `importModelConfigs(data)`
- `exportModelConfigs(params)`

修改页面：

- `frontend/modules/ai/src/views/AiModelConfigManage.vue`

页面调整：

- `onMounted` 调用 `listModelConfigs` 加载列表。
- 环境、提供商、状态、关键字变化后重新查询。
- 保存时调用 create/update。
- 删除、复制、连接测试调用后端接口后刷新列表。
- 导入时把 JSON 解析后提交后端 `/import`。
- 导出时调用后端 `/export`，前端再下载 JSON；导出数据中的 API Key 保持脱敏。
- 页面不再使用 `loadModelConfigState`、`saveModelConfigState`、`runModelConnectionTest` 作为主流程。

保留前端工具：

- `bailianModels.js` 保留，负责模板展示。
- `modelConfigStore.js` 可保留默认值、导入导出前端格式化函数，但命名需避免误导为主存储；如改动较大，可改名为 `modelConfigClientUtils.js`。

## 10. API 文档设计

新增文档：

- `modules/module-ai/module-ai-core/API_MODEL_CONFIG_SPEC.md`

文档内容：

- 接口统一返回体说明。
- 字段说明。
- 查询、新建、编辑、删除、复制、测试、导入、导出接口示例。
- API Key 脱敏与编辑保留规则。
- 前端调用路径示例。

## 11. 测试计划

后端最小测试：

- `AiModelConfigControllerTest`
  - 验证统一返回体。
  - 验证路径契约。
  - 验证请求转调 Service。
- `AiModelConfigServiceTest`
  - 验证参数校验。
  - 验证 API Key 脱敏。
  - 验证编辑保留密钥。
  - 验证复制重置测试结果。
- `AiModelConfigRepositoryTest`
  - 使用 fake/stub JDBC，不使用 H2。
  - 验证 SQL 包含 MySQL 表名和逻辑删除条件。
- `AiModelConfigSchemaInitializerTest`
  - 验证 `CREATE TABLE IF NOT EXISTS ai_model_config`。
- `AiModelConfigAutoConfigurationTest`
  - 验证有 DataSource 时 Bean 自动注册。

前端最小测试：

- 新增或修改 `frontend/modules/ai/tests/model-config-static.test.mjs`。
- 验证新增 `src/api/model-config.js`。
- 验证前端调用 `/biz/ai/model-configs`。
- 验证页面不再调用 `localStorage` 主存储方法。
- 验证导出仍脱敏。

构建验证：

- `mvn -pl modules/module-ai/module-ai-core -am test`
- `mvn -pl modules/module-ai/module-ai-autoconfig -am test`
- `node --test frontend/modules/ai/tests/*.test.mjs`
- `cd frontend/web-shell && npm run build`

## 12. 风险与处理

- 风险：API Key 明文存储存在安全风险。
  - 处理：本期只满足配置管理闭环；响应、导出、日志均不返回明文。后续可增加加密器或接入密钥管理服务。
- 风险：前端从 localStorage 切换到后端后，旧本地数据无法自动迁移。
  - 处理：保留 JSON 导入能力，用户可手动导出旧数据后导入后端。
- 风险：Schema 初始化与已有表结构冲突。
  - 处理：只执行 `CREATE TABLE IF NOT EXISTS`，不做破坏性 DDL。
- 风险：现有工作区已有未提交改动。
  - 处理：实施时只修改本设计列出的文件，不回滚无关改动。

