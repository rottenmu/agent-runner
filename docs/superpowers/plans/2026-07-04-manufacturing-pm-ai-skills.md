# 项目管理 AI 技能接入实施计划

> **给智能体执行者：** 按任务逐项执行，遵循测试先行。每个任务先写失败测试，再实现最小代码，最后运行相关验证。

**目标：** 在 `xingju-project-mgmt-core` 中接入 `ai-agent-spring-boot-starter`，把项目管理核心能力发布为可被 MCP、飞书 AI 通道和其他 AI 调度入口调用的 `AiSkill`。

**架构：** 项目管理 core 直接依赖 AI starter，并在 `com.zimo.manufacturingpm.aiskill` 包下声明三个 `AiSkill` Spring Bean。技能只做参数解析、业务服务调用和结果封装，核心业务继续复用 `BusinessOrderExcelService`、`ProjectRulesService`、`ProjectService`。

**技术栈：** Java 17、Spring Boot 3.4.5、Maven 多模块、Apache POI、JUnit 5、AssertJ、Mockito、`ai-agent-spring-boot-starter`。

## 全局约束

- 新增或修改的 `.md` 文档使用中文。
- 不编辑 `target/`、`dist/` 等生成产物。
- 不引入 SQLite、H2 等本地数据库作为运行依赖。
- Controller、Service 需要注入 Bean 时使用普通 public 构造器注入，且每个类只保留一个 public 构造器。
- AI 技能 Bean 必须实现 `com.zimo.starter.ai.skill.AiSkill`，由 starter 的 `AiSkillRegistry` 自动收集。

---

### Task 1: Excel 业务订单纯解析入口

**文件：**
- 修改：`modules/xingju-project-mgmt-starter/xingju-project-mgmt-core/src/main/java/com/xingju/manufacturingpm/excel/BusinessOrderExcelService.java`
- 测试：`modules/xingju-project-mgmt-starter/xingju-project-mgmt-core/src/test/java/com/xingju/manufacturingpm/excel/BusinessOrderExcelServiceTest.java`

**接口：**
- 产出：`BusinessOrderExcelService#parseWorkbook(Workbook workbook, String sourceName, String sourceUrl): WorkflowResult`

- [x] 写失败测试：构造业务订单模板 workbook，调用 `parseWorkbook`，断言不调用 `ProjectService#createProject` / `updateProject`，并返回项目字段。
- [x] 运行测试确认失败。
- [x] 提取复用已有 `parseProject`、`parseCustom` 逻辑，新增纯解析入口。
- [x] 运行测试确认通过。

### Task 2: 项目管理 AI Skill 实现

**文件：**
- 修改：`modules/xingju-project-mgmt-starter/xingju-project-mgmt-core/pom.xml`
- 新建：`modules/xingju-project-mgmt-starter/xingju-project-mgmt-core/src/main/java/com/xingju/manufacturingpm/aiskill/ManufacturingPmAiSkillSupport.java`
- 新建：`modules/xingju-project-mgmt-starter/xingju-project-mgmt-core/src/main/java/com/xingju/manufacturingpm/aiskill/ManufacturingPmParseExcelAiSkill.java`
- 新建：`modules/xingju-project-mgmt-starter/xingju-project-mgmt-core/src/main/java/com/xingju/manufacturingpm/aiskill/ManufacturingPmGenerateProjectCodeAiSkill.java`
- 新建：`modules/xingju-project-mgmt-starter/xingju-project-mgmt-core/src/main/java/com/xingju/manufacturingpm/aiskill/ManufacturingPmQueryViewAiSkill.java`
- 测试：`modules/xingju-project-mgmt-starter/xingju-project-mgmt-core/src/test/java/com/xingju/manufacturingpm/aiskill/ManufacturingPmAiSkillTest.java`

**接口：**
- 产出：`manufacturing_pm_parse_excel`
- 产出：`manufacturing_pm_generate_project_code`
- 产出：`manufacturing_pm_query_view`

- [x] 写失败测试：Excel 技能从 base64 内容解析字段。
- [x] 写失败测试：项目编号技能返回 `ProjectRulesService#generateProjectCode` 内容。
- [x] 写失败测试：查看视图技能返回 `ProjectService#queryView` 内容。
- [x] 增加 `ai-agent-spring-boot-starter` 依赖。
- [x] 实现三个技能和公共参数工具。
- [x] 运行技能测试确认通过。

### Task 3: 模块联动验证

**文件：**
- 测试：`modules/xingju-project-mgmt-starter/xingju-project-mgmt-core/src/test/java/com/xingju/manufacturingpm/aiskill/ManufacturingPmAiSkillRegistryTest.java`

**接口：**
- 消费：Spring 组件扫描。
- 产出：业务技能作为 `AiSkill` Bean 被 `AiSkillRegistry` 自动收集。

- [x] 新增 Spring 上下文测试：三个项目管理 `AiSkill` Bean 可被组件扫描发现，并由 `AiSkillRegistry` 收集。
- [x] 补齐组件注解和依赖配置。
- [x] 运行注册器测试确认通过。
- [x] 运行 `mvn -pl modules/xingju-project-mgmt-starter/xingju-project-mgmt-core -am test`。
