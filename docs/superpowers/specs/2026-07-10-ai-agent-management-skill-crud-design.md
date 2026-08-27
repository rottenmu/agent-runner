# 智能体管理技能 CRUD 设计

## 背景

`ai-agent-spring-boot-starter` 已经提供智能体管理、技能列表、技能提示词模板绑定，以及运行时 API 技能注册能力。当前技能来源主要有两类：

- Java 内置技能：由 Spring Bean 注册，逻辑随代码发布，适合保持稳定和只读保护。
- 自定义 API 技能：由管理端配置远程 HTTP API，运行时注册到 `AiSkillRegistry`，适合作为平台可管理技能。

用户已澄清本次范围是“智能体管理”模块的通用技能新增、编辑、删除能力，不限定为项目管理智能体。

## 目标

在智能体管理模块中，为通用技能管理提供新增、编辑、删除能力。新增和编辑的对象限定为自定义 API 技能；Java 内置技能只允许查看、绑定提示词模板和被智能体引用。

## 非目标

- 不允许通过页面编辑或删除 Java 内置技能。
- 不实现任意脚本执行类技能。
- 不新增本地数据库方案，仅面向 MySQL。
- 不改变已有内置技能的业务逻辑。
- 不改变飞书 CardKit 卡片渲染协议。

## 功能范围

### 技能列表

技能列表展示智能体管理模块可用技能，包含：

- Java 内置技能。
- 启用且未删除的自定义 API 技能。

列表项需要展示技能名称、描述、只读状态、启用状态、引用智能体数量、提示词模板、技能来源。技能来源分为：

- `bean`：Java 内置技能。
- `api`：自定义 API 技能。

### 新增技能

新增技能入口放在智能体管理页面的“技能”页签中。新增技能默认作为通用自定义 API 技能，可被任意智能体绑定。表单字段包括：

- 技能名称。
- 技能描述。
- 适用智能体，允许为空；为空表示通用技能。
- 是否只读。
- 是否启用。
- API 基础地址。
- API 路径。
- HTTP 请求方法，支持 `GET`、`POST`、`PUT`、`PATCH`、`DELETE`。
- 请求头 JSON。
- 超时时间。
- 技能提示词模板。

新增成功后，后端将技能保存到 MySQL，并注册到运行时技能注册表，智能体可立即绑定和调用。

### 编辑技能

只允许编辑自定义 API 技能。编辑内容包括描述、适用智能体、只读状态、启用状态、API 调用配置、提示词模板。技能名称创建后不允许修改，避免破坏已有智能体绑定关系和调用入口。

内置 Java 技能的编辑按钮禁用或隐藏，只保留提示词模板绑定能力。

### 删除技能

只允许删除自定义 API 技能。删除采用软删除：

- 数据库记录设置 `deleted = 1`。
- 从运行时 API 技能注册表移除。
- 从所有智能体绑定列表中移除该技能。
- 保留历史记录，避免误删后无法追踪。

如果删除目标是 Java 内置技能，后端返回业务错误，前端提示“内置技能不可删除”。

## 数据模型

由于范围从“项目管理智能体”修正为“智能体管理”，表结构中的 `agent_id` 调整为可空字段：为空表示通用技能；填写智能体 ID 时表示该技能主要适用于某个智能体。表注释和字段注释均使用中文；只保留主键索引，不增加其他索引。

```sql
CREATE TABLE IF NOT EXISTS ai_agent_skill_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  agent_id VARCHAR(64) NULL COMMENT '适用智能体ID，为空表示通用技能',
  skill_name VARCHAR(128) NOT NULL COMMENT '技能名称',
  skill_description VARCHAR(512) NOT NULL COMMENT '技能描述',
  skill_type VARCHAR(32) NOT NULL COMMENT '技能类型',
  read_only TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否只读',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  base_url VARCHAR(512) NOT NULL COMMENT 'API基础地址',
  api_path VARCHAR(512) NOT NULL COMMENT 'API路径',
  http_method VARCHAR(16) NOT NULL DEFAULT 'POST' COMMENT 'HTTP请求方法',
  request_headers TEXT NULL COMMENT '请求头JSON',
  timeout_millis INT NOT NULL DEFAULT 3000 COMMENT '请求超时时间毫秒',
  prompt_template_id BIGINT NULL COMMENT '提示词模板ID',
  deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',
  created_by VARCHAR(64) NULL COMMENT '创建人',
  updated_by VARCHAR(64) NULL COMMENT '更新人',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI智能体技能配置表';
```

## 后端设计

### Schema 初始化

在 `ai-agent-spring-boot-starter` 中新增技能配置表初始化器，沿用当前提示词模板表的 MySQL 初始化风格。初始化器只负责创建表，不创建额外索引。

### 仓储层

新增技能配置仓储，负责：

- 查询未删除技能。
- 按 ID 查询技能。
- 按技能名称查询未删除技能。
- 新增技能。
- 更新技能。
- 软删除技能。

仓储使用 Spring JDBC，不引入新的 ORM。

### 服务层

扩展 `AiAgentManagementService`：

- 启动时加载数据库中的自定义 API 技能，并注册到 `AiSkillRegistry`。
- 创建技能时校验名称、描述、API 配置和同名冲突。
- 编辑技能时禁止修改技能名称。
- 删除技能时拒绝删除 Java 内置技能。
- 删除技能后同步清理运行时注册表和所有智能体绑定关系。
- 技能提示词模板绑定优先落库，内置技能仍沿用现有绑定逻辑。

### 接口层

沿用 `/api/ai/skills` 资源：

- `GET /api/ai/skills`：返回技能列表。
- `POST /api/ai/skills`：新增自定义 API 技能。
- `PUT /api/ai/skills/{name}/api-config`：兼容已有 API 配置更新。
- `PUT /api/ai/skills/{name}`：编辑自定义 API 技能基础信息和 API 配置。
- `DELETE /api/ai/skills/{name}`：软删除自定义 API 技能。
- `PUT /api/ai/skills/{name}/prompt-template`：绑定技能提示词模板。

响应对象增加 `source`、`enabled`、`agentId` 字段，用于前端区分内置技能和自定义技能。

## 前端设计

在 `frontend/modules/ai/src/views/AiAgentManage.vue` 的“技能”页签中增强：

- 增加“新建技能”按钮。
- 技能卡片展示来源、启用状态、只读状态、引用数量和提示词模板。
- 自定义 API 技能展示“编辑”“删除”操作。
- Java 内置技能不展示删除入口，编辑入口禁用或隐藏。
- 使用抽屉表单新增和编辑技能。
- 新增和编辑表单支持选择适用智能体，也允许为空作为通用技能。
- 删除前弹出确认。
- 保存后刷新技能列表，并同步智能体绑定表单中的技能候选项。

前端 API 文件 `frontend/modules/ai/src/api/agent.js` 增加技能创建、编辑、删除方法。

## 校验与错误处理

- 技能名称不能为空。
- 技能描述不能为空。
- 自定义 API 技能名称不能与 Java 内置技能重名。
- 同一未删除自定义 API 技能名称不能重复。
- API 基础地址不能为空。
- API 路径不能为空。
- HTTP 方法必须在允许列表内。
- 请求头 JSON 必须是对象结构。
- 超时时间必须大于 0。
- 删除内置技能返回错误。
- 编辑不存在或已删除的技能返回 404。

## 测试策略

后端测试：

- Schema 初始化器包含 `ai_agent_skill_config` 表和中文注释，且不包含额外索引。
- 仓储能新增、查询、更新、软删除自定义技能。
- 管理接口能新增、编辑、删除自定义 API 技能。
- 内置技能不可删除。
- 数据库技能能在启动后注册到 `AiSkillRegistry`。
- 删除自定义技能后会从所有智能体绑定列表中移除。

前端测试：

- 静态测试确认 `agent.js` 包含技能新增、编辑、删除 API。
- 静态测试确认技能页存在新建入口、编辑入口、删除入口和内置技能保护逻辑。

验证命令：

- `mvn -pl modules/ai-agent-spring-boot-starter -am test`
- `node frontend/modules/ai/tests/prompt-template-static.test.mjs`
- `cd frontend/web-shell && npm run build`

## 兼容性

已有内置技能继续由 Spring Bean 注册。已有 `/api/ai/skills` 列表接口保留，返回字段只做向后兼容扩展。已有提示词模板绑定接口继续可用。

## 自检

- 无占位项。
- 功能边界已从项目管理智能体修正为智能体管理模块通用技能 CRUD。
- 表结构随范围修正，`agent_id` 可为空表示通用技能。
- 内置技能保护、MySQL 持久化、中文注释、仅主键索引均已覆盖。
