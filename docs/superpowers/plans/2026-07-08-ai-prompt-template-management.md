# AI 提示词模板管理实施计划

> **给执行智能体的要求：** 必须按任务逐项执行本计划。推荐使用 Subagent-Driven 方式；如果不使用子智能体，也必须按清单顺序完成，每完成一个任务后运行对应验证。

**目标：** 在 `ai-agent-spring-boot-starter` 的智能体管理模块下新增提示词模板管理能力，并在前端新增提示词管理页面。模板统一遵循 CoSTAR 规范，支持新增、编辑、删除、列表查看，以及基于简短业务描述自动生成标准化模板草稿。

**架构：** 后端在 AI 管理边界内新增提示词模板领域模型、服务、仓储、控制器和 MySQL 表初始化器。生产环境仓储使用 Spring JDBC 访问用户已确认的 MySQL 表。前端在 AI 模块下新增 `/ai/prompts` 路由和管理页面，复用现有后台管理风格。

**技术栈：** Java 17、Spring Boot 3.4.5、Spring MVC、Spring JDBC、MySQL、Maven、Vue 3、Element Plus、Vite。

## 全局约束

- 数据库只能面向 MySQL，禁止引入 SQLite、H2 等本地数据库。
- 数据表使用用户已确认的 `ai_prompt_template`。
- 数据表只保留主键索引：`PRIMARY KEY (id)`；禁止新增唯一索引、普通索引、全文索引。
- 表注释和字段注释必须使用中文。
- Controller 层、Service 层 Bean 只使用一个 public 构造器注入。
- 不编辑 `target/`、`dist/` 等生成产物。
- 不回滚仓库中与本任务无关的既有改动。
- 前端页面保持后台管理工具风格，不做营销式落地页。

## 用户已确认的 DDL

实现表初始化器时必须使用以下结构，仅可将 `CREATE TABLE` 调整为 `CREATE TABLE IF NOT EXISTS`，不得增加任何索引。

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

## 预计文件

后端：

- 修改 `modules/ai-agent-spring-boot-starter/pom.xml`
- 修改 `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfiguration.java`
- 新增 `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplate.java`
- 新增 `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateRequest.java`
- 新增 `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateGenerateRequest.java`
- 新增 `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateRepository.java`
- 新增 `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/JdbcAiPromptTemplateRepository.java`
- 新增 `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateService.java`
- 新增 `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateGenerator.java`
- 新增 `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateController.java`
- 新增 `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateSchemaInitializer.java`
- 新增 `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiPromptTemplateServiceTest.java`
- 新增 `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiPromptTemplateControllerTest.java`
- 新增 `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiPromptTemplateSchemaInitializerTest.java`
- 修改 `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfigurationTest.java`

前端：

- 修改 `frontend/modules/ai/menus.js`
- 修改 `frontend/modules/ai/routes.js`
- 修改 `frontend/modules/ai/src/api/agent.js`
- 新增 `frontend/modules/ai/src/views/AiPromptTemplateManage.vue`
- 新增 `frontend/modules/ai/tests/prompt-template-static.test.mjs`

## 任务 1：后端领域模型、生成器和服务

**目标：** 先用不依赖数据库的测试锁定 CoSTAR 校验、手动创建、自动生成草稿和软删除行为。

- [ ] 新增 `AiPromptTemplateServiceTest`，使用内存假仓储，不使用 H2 或 SQLite。
- [ ] 测试 `create` 能保存手动模板，且六个 CoSTAR 字段完整。
- [ ] 测试缺少任一 CoSTAR 字段时抛出 `IllegalArgumentException`。
- [ ] 测试 `generate` 根据 `businessDescription` 返回 `sourceType=generated` 的草稿，并且不写入仓储。
- [ ] 测试 `delete` 调用仓储软删除。
- [ ] 运行以下命令，确认测试先失败：

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -am "-Dtest=AiPromptTemplateServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] 新增 `AiPromptTemplate`，字段使用 Java 驼峰命名，覆盖已确认表结构中的业务字段和审计字段。
- [ ] 新增 `AiPromptTemplateRequest`，用于新增和编辑请求。
- [ ] 新增 `AiPromptTemplateGenerateRequest`，包含 `businessDescription`。
- [ ] 新增 `AiPromptTemplateRepository` 接口，提供 `listActive`、`findById`、`save`、`softDelete`。
- [ ] 新增 `AiPromptTemplateGenerator`，使用确定性中文规则生成 CoSTAR 六段内容，暂不接入真实大模型。
- [ ] 新增 `AiPromptTemplateService`，使用单 public 构造器注入仓储和生成器。
- [ ] 服务层校验 `templateName`、`sourceType` 和 CoSTAR 六段内容。
- [ ] 再次运行服务测试，确认通过。
- [ ] 提交任务 1：

```powershell
git add modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiPromptTemplateServiceTest.java `
  modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplate.java `
  modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateRequest.java `
  modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateGenerateRequest.java `
  modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateRepository.java `
  modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateService.java `
  modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateGenerator.java
git commit -m "feat: add ai prompt template service"
```

## 任务 2：MySQL 仓储、表初始化器和自动配置

**目标：** 接入 MySQL 持久化，并保证表结构严格符合用户确认版本。

- [ ] 新增 `AiPromptTemplateSchemaInitializerTest`，使用记录 SQL 的假 `DataSource`，不连接真实数据库。
- [ ] 测试 SQL 包含 `CREATE TABLE IF NOT EXISTS ai_prompt_template`。
- [ ] 测试 SQL 包含中文表注释 `COMMENT='AI提示词模板表'`。
- [ ] 测试 SQL 包含 `PRIMARY KEY (id)`。
- [ ] 测试 SQL 不包含 `UNIQUE KEY`、`CREATE INDEX`、` KEY idx_`。
- [ ] 修改 `AiAgentAutoConfigurationTest`，在存在 `DataSource` 时断言提示词模板相关 Bean 被注册。
- [ ] 运行以下命令，确认测试先失败：

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -am "-Dtest=AiPromptTemplateSchemaInitializerTest,AiAgentAutoConfigurationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] 在 `modules/ai-agent-spring-boot-starter/pom.xml` 增加 `org.springframework:spring-jdbc` 依赖。
- [ ] 新增 `JdbcAiPromptTemplateRepository`，构造器参数为 `DataSource`，内部使用 `JdbcTemplate`。
- [ ] `listActive` 查询 `deleted = 0` 的模板，并按 `id DESC` 排序。
- [ ] `findById` 只返回未删除模板。
- [ ] `save` 在 `id == null` 时插入，在 `id != null` 时更新。
- [ ] `softDelete` 执行逻辑删除：`deleted = 1`。
- [ ] 新增 `AiPromptTemplateSchemaInitializer`，执行用户已确认 DDL，且只包含主键索引。
- [ ] 修改 `AiAgentAutoConfiguration`，在存在 `DataSource` 时注册 JDBC 仓储和表初始化器。
- [ ] `AiPromptTemplateService`、`AiPromptTemplateController` 只在依赖 Bean 存在时注册。
- [ ] 再次运行任务 2 测试，确认通过。
- [ ] 提交任务 2：

```powershell
git add modules/ai-agent-spring-boot-starter/pom.xml `
  modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfiguration.java `
  modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/JdbcAiPromptTemplateRepository.java `
  modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateSchemaInitializer.java `
  modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiPromptTemplateSchemaInitializerTest.java `
  modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfigurationTest.java
git commit -m "feat: persist ai prompt templates in mysql"
```

## 任务 3：REST 接口

**目标：** 暴露前端所需的模板管理接口。

- [ ] 新增 `AiPromptTemplateControllerTest`，使用 `@WebMvcTest(AiPromptTemplateController.class)` 和 Mock Service。
- [ ] 覆盖 `GET /api/ai/prompt-templates`。
- [ ] 覆盖 `POST /api/ai/prompt-templates`。
- [ ] 覆盖 `PUT /api/ai/prompt-templates/{id}`。
- [ ] 覆盖 `DELETE /api/ai/prompt-templates/{id}`。
- [ ] 覆盖 `POST /api/ai/prompt-templates/generate`。
- [ ] 覆盖参数错误返回 400、更新或删除不存在返回 404。
- [ ] 运行以下命令，确认测试先失败：

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -am "-Dtest=AiPromptTemplateControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] 新增 `AiPromptTemplateController`，路径为 `/api/ai/prompt-templates`。
- [ ] Controller 使用单 public 构造器注入 `AiPromptTemplateService`。
- [ ] 实现列表、新增、编辑、删除、按业务描述生成草稿五个接口。
- [ ] 将 `IllegalArgumentException` 映射为 400。
- [ ] 将找不到模板的更新和删除映射为 404。
- [ ] 再次运行控制器测试，确认通过。
- [ ] 提交任务 3：

```powershell
git add modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiPromptTemplateControllerTest.java `
  modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateController.java
git commit -m "feat: expose ai prompt template api"
```

## 任务 4：前端 API、菜单、路由和页面

**目标：** 在智能体管理模块下新增提示词管理页面，支持手动编写和业务描述生成两种创建方式。

- [ ] 新增 `frontend/modules/ai/tests/prompt-template-static.test.mjs`。
- [ ] 静态测试断言菜单包含 `提示词管理`。
- [ ] 静态测试断言路由包含 `/ai/prompts` 和 `AiPromptTemplateManage`。
- [ ] 静态测试断言 API 文件包含 `listPromptTemplates`、`createPromptTemplate`、`updatePromptTemplate`、`deletePromptTemplate`、`generatePromptTemplate`。
- [ ] 静态测试断言页面包含 `CoSTAR` 和六个字段：`contextText`、`objectiveText`、`styleText`、`toneText`、`audienceText`、`responseText`。
- [ ] 运行以下命令，确认测试先失败：

```powershell
node frontend/modules/ai/tests/prompt-template-static.test.mjs
```

- [ ] 修改 `frontend/modules/ai/src/api/agent.js`，新增提示词模板五个接口函数。
- [ ] 修改 `frontend/modules/ai/menus.js`，新增 `提示词管理` 菜单。
- [ ] 修改 `frontend/modules/ai/routes.js`，新增 `/ai/prompts` 路由。
- [ ] 新增 `AiPromptTemplateManage.vue`。
- [ ] 页面提供模板列表、搜索、新增、编辑、删除入口。
- [ ] 表单提供手动编写和业务描述生成两种模式。
- [ ] 表单完整展示 CoSTAR 六段字段。
- [ ] 点击生成时调用 `generatePromptTemplate`，将返回草稿填入表单，不立即保存。
- [ ] 删除前使用确认弹窗。
- [ ] 页面文本、按钮和状态提示使用中文。
- [ ] 运行前端静态测试，确认通过。
- [ ] 构建前端主壳，确认通过：

```powershell
cd frontend/web-shell
npm run build
```

- [ ] 提交任务 4：

```powershell
git add frontend/modules/ai/tests/prompt-template-static.test.mjs `
  frontend/modules/ai/menus.js `
  frontend/modules/ai/routes.js `
  frontend/modules/ai/src/api/agent.js `
  frontend/modules/ai/src/views/AiPromptTemplateManage.vue
git commit -m "feat: add ai prompt template management page"
```

## 任务 5：整体验证

**目标：** 确认后端、前端和主应用组合可用。

- [ ] 运行提示词模板相关后端测试：

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -am "-Dtest=AiPromptTemplateServiceTest,AiPromptTemplateControllerTest,AiPromptTemplateSchemaInitializerTest,AiAgentAutoConfigurationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] 运行 AI starter 模块测试：

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -am test
```

- [ ] 运行前端 AI 静态测试：

```powershell
node frontend/modules/ai/tests/prompt-template-static.test.mjs
```

- [ ] 构建前端主壳：

```powershell
cd frontend/web-shell
npm run build
```

- [ ] 构建后端主应用：

```powershell
mvn -pl admin-shell -am -DskipTests package
```

- [ ] 扫描新增实现，确认没有引入禁止内容：

```powershell
rg "UNIQUE KEY|CREATE INDEX| KEY idx_|H2|SQLite" modules/ai-agent-spring-boot-starter frontend/modules/ai
```

- [ ] 如果整体验证中需要修复代码，单独提交验证修复；如果没有改动，不创建空提交。

## 验收标准

- 后端提供提示词模板列表、新增、编辑、删除和业务描述生成草稿接口。
- 模板数据字段完整遵循 CoSTAR 六段结构。
- MySQL 表结构和中文注释与用户确认版本一致。
- `ai_prompt_template` 只包含主键索引。
- 前端智能体管理模块下可以进入 `提示词管理` 页面。
- 页面支持手动编写模板和业务描述生成模板草稿。
- 相关后端测试、前端静态测试、前端构建和后端主应用构建通过。
