# AI 提示词模板分类与引用设计

## 背景

当前 `ai-agent-spring-boot-starter` 已提供提示词模板管理能力，模板统一遵循 CoSTAR 规范，前端已有提示词管理页面，智能体管理页面已有智能体与技能视图。新的需求是在现有管理能力上继续收敛边界：

- 提示词模板分为智能体提示词模板和技能提示词模板。
- 创建或编辑智能体时可以引用智能体提示词模板。
- 现有技能不新增创建能力，仅支持为已注册技能绑定技能提示词模板。
- 提示词管理不再作为 AI 模块一级菜单，而是放到智能体管理菜单下，以二级菜单或页内标签形式访问。

本设计选择“现有技能绑定模板”方案，不建设技能 CRUD。这样可以贴合当前 `AiSkillRegistry` 的只读技能注册模型，也能避免在技能生命周期尚未统一前引入额外数据表和管理边界。

## 目标

1. 提示词模板新增模板类型，区分 `agent` 和 `skill`。
2. 智能体创建、编辑表单支持选择 `agent` 类型提示词模板。
3. 技能列表支持为注册技能绑定 `skill` 类型提示词模板。
4. 智能体管理页面内提供“智能体 / 技能 / 提示词管理”的二级入口。
5. 后端接口支持按模板类型查询，前端无需拿全量数据再自行猜测用途。

## 非目标

- 不新增技能创建、编辑、删除功能。
- 不为技能绑定新增持久化数据表。技能绑定先跟随当前智能体管理的内存管理模型，后续如果智能体管理整体持久化，再统一迁移。
- 不改变 CoSTAR 字段规范，不降低现有模板必填校验。
- 不新增除主键外的数据库索引。
- 不引入 SQLite、H2 或本地数据库验证路径。

## 数据库设计

在现有 `ai_prompt_template` 表上新增模板类型字段：

```sql
ALTER TABLE ai_prompt_template
ADD COLUMN template_type VARCHAR(20) NOT NULL DEFAULT 'agent'
COMMENT '模板类型：agent智能体提示词模板，skill技能提示词模板'
AFTER description;
```

建表 SQL 同步包含该字段：

```sql
CREATE TABLE IF NOT EXISTS ai_prompt_template (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  template_code VARCHAR(64) NOT NULL COMMENT '模板编码',
  template_name VARCHAR(120) NOT NULL COMMENT '模板名称',
  description VARCHAR(500) DEFAULT NULL COMMENT '模板说明',
  template_type VARCHAR(20) NOT NULL DEFAULT 'agent' COMMENT '模板类型：agent智能体提示词模板，skill技能提示词模板',
  context_text TEXT NOT NULL COMMENT 'CoSTAR上下文',
  objective_text TEXT NOT NULL COMMENT 'CoSTAR目标',
  style_text TEXT NOT NULL COMMENT 'CoSTAR风格',
  tone_text TEXT NOT NULL COMMENT 'CoSTAR语气',
  audience_text TEXT NOT NULL COMMENT 'CoSTAR受众',
  response_text TEXT NOT NULL COMMENT 'CoSTAR响应格式',
  source_type VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT '创建方式：manual手动创建，generated系统生成',
  business_description VARCHAR(1000) DEFAULT NULL COMMENT '业务简述',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '启用状态：1启用，0停用',
  deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '删除状态：1已删除，0未删除',
  created_by VARCHAR(64) DEFAULT NULL COMMENT '创建人ID',
  created_name VARCHAR(120) DEFAULT NULL COMMENT '创建人名称',
  updated_by VARCHAR(64) DEFAULT NULL COMMENT '更新人ID',
  updated_name VARCHAR(120) DEFAULT NULL COMMENT '更新人名称',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id)
) COMMENT='AI提示词模板表';
```

兼容策略：

- 已存在表但缺少 `template_type` 字段时，启动初始化器补充该字段。
- 既有模板默认归类为 `agent`，保证历史数据仍可被智能体引用。
- 字段值只允许 `agent` 和 `skill`，请求为空时默认 `agent`。

## 后端设计

### 提示词模板

- `AiPromptTemplate` 增加 `templateType` 属性。
- 创建、更新、自动生成请求增加 `templateType`。
- `AiPromptTemplateService` 增加模板类型规范化与校验：
  - 空值默认 `agent`。
  - 非 `agent`、`skill` 返回明确业务错误。
- `GET /api/ai/prompt-templates` 增加可选查询参数 `templateType`：
  - 不传时返回全部启用且未删除模板。
  - 传 `agent` 时只返回智能体模板。
  - 传 `skill` 时只返回技能模板。
- Repository 查询按 `deleted = 0`、`enabled = 1` 和可选类型过滤。

### 智能体引用模板

- `AiManagedAgent` 和 `AiManagedAgentRequest` 增加 `promptTemplateId`。
- 智能体创建、编辑时保存该字段。
- 后端不在本阶段强制校验模板 ID 一定存在，原因是当前智能体管理也属于 starter 级内存管理能力，避免引入跨服务强依赖；但前端选择器只展示有效的 `agent` 类型模板，降低误填概率。
- 后续如果智能体持久化落库，再增加数据库外键或业务一致性校验。

### 技能绑定模板

- `AiManagedSkill` 增加 `promptTemplateId`。
- 新增技能模板绑定请求，例如：

```json
{
  "promptTemplateId": 12
}
```

- 新增接口：

```http
PUT /api/ai/skills/{name}/prompt-template
```

- 接口行为：
  - `{name}` 必须是 `AiSkillRegistry` 中已注册技能。
  - 存在时保存绑定并返回更新后的技能信息。
  - 不存在时返回明确错误。
  - `promptTemplateId` 允许为空，用于取消绑定。
- 绑定关系先保存在 `AiAgentManagementService` 的内存映射中，与当前管理模块的生命周期保持一致。

## 前端设计

### 菜单

- AI 模块一级菜单保留：
  - 智能体对话
  - 智能体管理
- 移除提示词管理的一级菜单入口。
- 智能体管理页面内新增二级入口：
  - 智能体
  - 技能
  - 提示词管理

如果路由系统仍保留 `/ai/prompts`，该路由仅作为兼容深链，不再显示在一级菜单中。主入口以 `/ai/agents` 页面内二级标签为准。

### 提示词管理

- 提示词模板表单增加模板类型选择：
  - 智能体提示词模板
  - 技能提示词模板
- 自动生成模板时也带上模板类型。
- 列表提供类型筛选或展示类型标识，便于用户区分模板用途。
- 页面继续复用现有 CoSTAR 编辑抽屉，不改变手动创建与业务描述生成的双路径。

### 智能体管理

- 智能体创建、编辑抽屉增加“提示词模板”选择器。
- 选择器只加载 `templateType=agent` 的模板。
- 选择为空时表示智能体不绑定模板，继续使用表单内 persona 等已有字段。

### 技能管理

- 技能列表增加“提示词模板”列或绑定控件。
- 绑定控件只加载 `templateType=skill` 的模板。
- 用户选择模板后调用技能绑定接口，成功后刷新技能列表。
- 已绑定模板在列表中展示模板名称或模板 ID；如果模板被删除或停用，列表仍保留绑定值，但选择器不再提供该模板作为新选项。

## 数据流

1. 管理员进入智能体管理页面。
2. 页面加载智能体、技能，以及按类型过滤后的提示词模板。
3. 创建或编辑智能体时，前端提交 `promptTemplateId`。
4. 技能绑定模板时，前端调用技能模板绑定接口。
5. 提示词管理页内新增或编辑模板时，后端保存 `templateType`，后续智能体或技能选择器按类型读取。

## 错误处理

- 模板类型非法：返回参数错误，提示“模板类型仅支持 agent 或 skill”。
- 技能不存在：绑定接口返回业务错误，提示“技能不存在或未注册”。
- 模板 ID 为空：智能体表示不绑定模板，技能绑定接口表示取消绑定。
- 数据库初始化失败：保持现有启动失败策略，暴露明确 SQL 或字段初始化错误，避免静默降级。

## 测试策略

后端测试：

- 模板类型默认值、合法值、非法值校验。
- 按 `templateType` 查询模板。
- 建表 SQL 包含中文注释和 `template_type` 字段。
- 已存在表缺少 `template_type` 时会补充字段。
- 表结构只包含主键索引，不新增普通索引或唯一索引。
- 智能体创建、编辑可以保存 `promptTemplateId`。
- 技能绑定接口可以绑定、取消绑定，并能处理不存在的技能。

前端测试：

- AI 一级菜单不再展示提示词管理。
- 智能体管理页面存在提示词管理二级入口。
- 提示词模板表单包含模板类型。
- 智能体表单只引用智能体提示词模板。
- 技能列表只引用技能提示词模板。

构建验证：

- 运行 `mvn -pl modules/ai-agent-spring-boot-starter -am test`。
- 运行 AI 前端静态测试。
- 如前端结构变动影响主壳加载，运行 `frontend/web-shell` 构建。

## 验收标准

- 管理员可以创建 `agent` 与 `skill` 两类 CoSTAR 提示词模板。
- 创建或编辑智能体时可以选择智能体提示词模板。
- 技能列表中可以为已注册技能绑定技能提示词模板。
- 提示词管理入口位于智能体管理页内，不再出现在 AI 一级菜单中。
- 数据库表注释和字段注释均为中文，且只保留主键索引。
- 原有未分类模板自动视为智能体提示词模板，旧数据不丢失。
