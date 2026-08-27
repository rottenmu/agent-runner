# AI 提示词模板管理设计说明

## 背景

`ai-agent-spring-boot-starter` 已提供智能体管理和技能管理能力，前端 AI 模块也已有智能体管理页面。为了让智能体使用的提示词可被统一维护，需要在智能体管理模块下新增提示词管理页面，提供提示词模板的新增、编辑、删除和按业务描述生成能力。

本设计只面向 MySQL 数据库。数据表结构必须先由用户确认后才能被代码和迁移脚本使用；本次已确认使用 `ai_prompt_template` 表，并且只保留主键索引。

## 目标

- 在 AI 前端模块新增“提示词管理”入口。
- 提供提示词模板全生命周期管理：新增、编辑、软删除、启停、列表查看。
- 所有模板统一遵循 CoSTAR 规范。
- 支持两种创建方式：
  - 手动编写 CoSTAR 六段内容。
  - 输入简短业务描述，由系统生成标准 CoSTAR 草稿。
- 生成结果先采用确定性规则生成，避免依赖外部模型 Key，保证功能稳定可测试。

## 非目标

- 本次不做提示词版本历史。
- 本次不做模板审批流。
- 本次不做多租户隔离和复杂权限控制。
- 本次不直接调用大模型生成提示词；后续可替换为 `AiChatClient` 驱动的生成器。

## 数据表

已确认的 MySQL 表结构如下：

```sql
CREATE TABLE ai_prompt_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    template_code VARCHAR(64) NOT NULL COMMENT '模板编码',
    template_name VARCHAR(100) NOT NULL COMMENT '模板名称',
    description VARCHAR(500) DEFAULT NULL COMMENT '模板说明',

    context_text TEXT NOT NULL COMMENT 'CoSTAR规范-上下文，说明业务背景、已有信息和约束',
    objective_text TEXT NOT NULL COMMENT 'CoSTAR规范-目标，说明希望智能体完成的任务',
    style_text TEXT NOT NULL COMMENT 'CoSTAR规范-风格，说明输出表达风格',
    tone_text TEXT NOT NULL COMMENT 'CoSTAR规范-语气，说明输出语气和态度',
    audience_text TEXT NOT NULL COMMENT 'CoSTAR规范-受众，说明回答面向的用户角色',
    response_text TEXT NOT NULL COMMENT 'CoSTAR规范-响应格式，说明输出结构、格式和限制',

    source_type VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT '创建方式：manual手动编写，generated系统生成',
    business_description VARCHAR(1000) DEFAULT NULL COMMENT '业务简述，用于系统生成提示词模板',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0停用',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：1已删除，0未删除',

    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建人ID',
    created_name VARCHAR(100) DEFAULT NULL COMMENT '创建人名称',
    updated_by VARCHAR(64) DEFAULT NULL COMMENT '更新人ID',
    updated_name VARCHAR(100) DEFAULT NULL COMMENT '更新人名称',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI提示词模板表';
```

## 后端设计

新增代码放在 `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management` 下，保持和现有智能体管理能力同一边界。

新增核心类型：

- `AiPromptTemplate`：提示词模板响应模型。
- `AiPromptTemplateRequest`：新增和编辑请求。
- `AiPromptTemplateGenerateRequest`：按业务描述生成请求。
- `AiPromptTemplateService`：模板管理服务。
- `AiPromptTemplateController`：REST 接口。
- `AiPromptTemplateGenerator`：CoSTAR 草稿生成器。
- `AiPromptTemplateSchemaInitializer`：MySQL 表结构初始化器。

REST 接口：

- `GET /api/ai/prompt-templates`：返回未删除模板列表。
- `POST /api/ai/prompt-templates`：新增模板。
- `PUT /api/ai/prompt-templates/{id}`：编辑模板。
- `DELETE /api/ai/prompt-templates/{id}`：软删除模板。
- `POST /api/ai/prompt-templates/generate`：按业务描述生成 CoSTAR 草稿。

校验规则：

- `template_name` 必填。
- `context_text`、`objective_text`、`style_text`、`tone_text`、`audience_text`、`response_text` 必填。
- `source_type` 只能为 `manual` 或 `generated`。
- 删除只更新 `deleted=1`。
- 列表默认过滤 `deleted=1`。

## 生成逻辑

用户输入简短业务描述后，系统生成 CoSTAR 六段草稿：

- Context：从业务描述提取业务背景，并说明需要结合当前系统数据和操作约束。
- Objective：描述智能体需要完成的主要任务。
- Style：默认使用结构清晰、步骤明确的业务说明风格。
- Tone：默认使用专业、克制、可执行的语气。
- Audience：默认面向业务管理员、智能体配置人员或具体业务使用者。
- Response：要求输出结构化内容，避免冗长叙述，并说明必要字段和限制。

生成接口只返回草稿，不直接保存。用户确认并点击保存后才创建模板。

## 前端设计

新增页面放在 `frontend/modules/ai/src/views/AiPromptTemplateManage.vue`。

新增 API 文件或扩展现有 `frontend/modules/ai/src/api/agent.js`：

- `listPromptTemplates`
- `createPromptTemplate`
- `updatePromptTemplate`
- `deletePromptTemplate`
- `generatePromptTemplate`

路由和菜单：

- 菜单标题：`提示词管理`
- 路由：`/ai/prompts`
- 页面名称：`AiPromptTemplateManage`

页面布局：

- 左侧沿用现有智能体管理的管理式布局风格。
- 主区域提供搜索、模板列表、新增按钮。
- 模板卡片展示模板名称、模板编码、创建方式、启用状态、更新时间。
- 右侧抽屉用于新增和编辑。
- 抽屉内提供创建方式切换：
  - 手动编写：直接填写 CoSTAR 六段。
  - 业务描述生成：填写业务描述，点击生成后自动填充六段，用户可继续编辑。

## 错误处理

- 后端参数校验失败返回 400。
- 编辑或删除不存在的模板返回 404。
- 生成业务描述为空返回 400。
- 前端保存失败时展示错误提示，并保留用户已输入内容。
- 删除前二次确认，避免误删。

## 测试设计

后端测试：

- Controller 测试覆盖列表、新增、编辑、删除、生成。
- Service 测试覆盖 CoSTAR 必填校验、软删除、生成草稿。
- 自动配置测试覆盖提示词管理服务和控制器注册。

前端测试：

- API 路径测试覆盖提示词模板接口。
- 页面静态测试覆盖菜单、路由、创建模式、CoSTAR 字段。

## 实施顺序

1. 编写后端失败测试，覆盖提示词模板管理接口和生成接口。
2. 实现后端模型、服务、控制器和自动配置。
3. 编写前端失败测试，覆盖菜单、路由、API 和页面关键文本。
4. 实现前端 API、路由、菜单和页面。
5. 增加 MySQL 表结构初始化或迁移脚本，严格使用已确认 DDL。
6. 运行最窄范围测试，再运行主应用打包验证。

## 风险与后续

- 当前确认只保留主键索引，模板数量变大后列表查询可能需要补充索引；后续新增索引仍需单独确认。
- 当前生成逻辑是确定性模板，不代表真实大模型生成质量；后续可接入 `AiChatClient`。
- 当前设计未做版本历史，模板覆盖更新后不能回溯；如要审计能力可后续新增版本表。
