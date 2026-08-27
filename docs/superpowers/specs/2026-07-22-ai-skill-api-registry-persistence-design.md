# 智能体技能 API 注册信息持久化设计

## 背景

智能体管理的技能配置页面已经支持从系统 API 注册表中搜索接口，并自动填充技能调用所需的 `path` 和 `method`。当前技能调用配置本身已经可以保存到数据库，包括基础地址、接口路径、HTTP 方法、请求头和超时时间。

本次优化的目标是：技能配置除了保存可直接执行的接口调用快照，还要保存它来自哪个已注册 API，方便后续编辑、追溯、前端回显和接口治理。

## 目标

- 创建或编辑技能时，支持保存所选 API 注册表记录的 ID。
- 技能详情、技能编辑、API 配置查询接口返回已关联的 API 注册表 ID。
- 已注册 API 被禁用、删除或重新扫描后，不影响已保存技能继续调用。
- 保持 AI 技能模块与系统 API 注册模块低耦合，避免强外键依赖。
- 保持现有手工配置外部 API 的能力，未选择注册接口时可以正常保存。

## 非目标

- 不在本次实现中重写技能执行引擎。
- 不要求运行时每次调用都重新查询 `api_registry`。
- 不新增复杂的 API 版本比对、差异提醒或自动同步机制。
- 不强制所有技能都必须绑定系统 API 注册表。

## 方案选择

采用方案 A：保存 `api_registry_id`，同时继续保存可执行调用快照。

技能真正执行时仍读取当前已保存的 `baseUrl`、`path`、`method`、`headers`、`timeoutMillis`。`api_registry_id` 只作为来源关联和编辑回显信息，不参与运行时强校验。

这个方案兼顾两个需求：

- 可追溯：知道技能配置来自哪个平台接口。
- 稳定执行：接口注册表变化不会导致已配置技能突然不可用。

## 数据库设计

在 `ai_agent_skill_config` 表中新增可空字段：

```sql
ALTER TABLE ai_agent_skill_config
  ADD COLUMN api_registry_id BIGINT NULL COMMENT '关联的 API 注册表 ID';
```

数据库初始化逻辑采用启动时兼容升级：

- 如果 `ai_agent_skill_config` 表不存在，创建完整表结构，包含 `api_registry_id`。
- 如果表已存在但缺少 `api_registry_id` 字段，只追加该字段。
- 不创建数据库外键，避免 AI starter 直接依赖 `module-sys` 的表结构生命周期。
- 历史数据默认 `api_registry_id = NULL`。

## 后端设计

### DTO 扩展

`AiSkillApiConfigRequest` 新增字段：

- `apiRegistryId: Long`

`AiSkillApiConfigResponse` 新增字段：

- `apiRegistryId: Long`

字段含义：当前技能 API 配置关联的系统 API 注册表记录 ID。为空时表示手工配置或历史数据。

### 领域对象扩展

`AiManagedSkillConfig` 增加 `apiRegistryId` 属性，用于数据库读写和接口响应回显。

### 仓储层扩展

`JdbcAiManagedSkillConfigRepository` 调整内容：

- 查询字段增加 `api_registry_id`。
- 插入语句增加 `api_registry_id`。
- 更新语句增加 `api_registry_id`。
- 行映射增加 `apiRegistryId`。

保存规则：

- 创建技能时，如果前端选择注册 API，则保存对应 ID。
- 编辑技能时，如果前端保留或重新选择注册 API，则保存对应 ID。
- 清空关联时，保存为 `NULL`。
- API 配置独立保存接口也同步处理 `apiRegistryId`。

### 服务层规则

`AiAgentManagementService` 在技能创建、技能编辑、API 配置保存、技能详情查询中统一处理 `apiRegistryId`。

运行时调用技能时不强依赖 `apiRegistryId`：

- `apiRegistryId` 为空：按手工配置执行。
- `apiRegistryId` 有值但注册表记录不存在：按已保存快照执行。
- `apiRegistryId` 有值但注册表记录已禁用：按已保存快照执行。

这保证“注册表用于选择和追溯，技能执行使用配置快照”。

## 前端设计

### 表单状态

技能表单的 `apiConfig` 增加字段：

- `apiRegistryId: null | number`

默认值为 `null`。

### 选择注册接口

在智能体管理的技能设置区域中，继续使用已新增的 API 注册表搜索选择控件。

选择某个 API 后：

- `apiConfig.apiRegistryId` 写入接口注册表记录 ID。
- `apiConfig.path` 写入接口路径。
- `apiConfig.method` 写入 HTTP 方法。
- `baseUrl`、`headers`、`timeoutMillis` 不被覆盖。

### 清空关联

用户清空 API 注册表选择后：

- `apiConfig.apiRegistryId` 置为 `null`。
- 保留当前 `path` 和 `method`，方便用户继续手工配置。

### 编辑回显

打开已有技能编辑弹窗时：

- 如果后端返回 `apiRegistryId`，选择控件显示已关联接口。
- 如果当前接口注册表中已经查不到该 ID，显示“已关联接口 #ID”的兜底文本。
- 用户可以重新搜索选择接口，也可以清空关联。

### 手工修改路径

如果用户选择注册 API 后又手工修改 `path` 或 `method`，不自动清空 `apiRegistryId`。

理由：这类修改可能是临时覆盖或兼容网关路径，保留来源信息更利于追溯。后续如需严格一致性校验，可再增加差异提示。

## 接口兼容性

现有接口保持路径和语义不变，只扩展响应字段和请求字段：

- 老前端不传 `apiRegistryId` 时，后端按 `NULL` 处理。
- 老数据没有 `apiRegistryId` 时，前端按手工配置显示。
- 新字段不会影响技能运行时 API 调用参数。

## 错误处理

- 数据库字段追加失败时，启动日志应输出清晰错误并阻止服务继续以不完整状态运行。
- 前端查询 API 注册表失败时，不阻断手工填写技能 API 配置。
- 已关联 API 不存在时，不阻断编辑和保存。
- 保存时只校验技能执行必需字段，不把 `apiRegistryId` 作为必填项。

## 测试策略

后端测试：

- 表不存在时初始化完整表结构。
- 表存在但缺少 `api_registry_id` 时自动补列。
- 创建技能时可保存 `apiRegistryId`。
- 编辑技能时可更新或清空 `apiRegistryId`。
- API 配置独立保存接口可保存 `apiRegistryId`。
- 查询技能详情和 API 配置时可返回 `apiRegistryId`。
- 历史数据 `apiRegistryId = NULL` 时仍可正常读取和执行。

前端测试：

- 选择注册 API 后自动填充 `apiRegistryId`、`path`、`method`。
- 清空选择后只清空 `apiRegistryId`，保留 `path`、`method`。
- 编辑已有技能时可回显已关联 ID。
- 注册接口查询失败时仍允许手工配置。
- 前端主壳构建通过。

## 交付范围

- 后端数据库初始化兼容升级。
- 后端 DTO、领域对象、仓储、服务层持久化链路。
- 前端智能体技能设置表单与 API 注册表选择控件保存链路。
- 覆盖关键保存、查询、兼容升级逻辑的测试。

## 风险与约束

- 不设置外键会牺牲数据库层面的引用完整性，但能保持模块解耦。
- 保留 `apiRegistryId` 而允许用户修改 `path`、`method`，可能出现“来源接口”和“实际调用接口”不完全一致的情况。本设计接受该情况，并把运行时快照作为真实执行依据。
- 如果未来要做接口变更提醒，可基于 `apiRegistryId` 再查询 `api_registry` 并和快照做差异比较。

