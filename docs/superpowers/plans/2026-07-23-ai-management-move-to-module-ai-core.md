# AI 管理能力迁移到 module-ai-core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将智能体、技能和提示词模板管理能力迁移到 `module-ai-core`，DAO 改用 MyBatis-Plus，并让 `ai-agent-spring-boot-starter` 只保留通用 AI 能力和最小运行时契约。

**Architecture:** starter 新增 `AiAgentProfile` 和 `AiAgentProvider`，对话与渠道组件只依赖这两个契约。`module-ai-core` 接管管理 Controller、Service、模型、DTO、Entity、Mapper 和表初始化，`module-ai-autoconfig` 负责条件装配和 Mapper 扫描，依赖方向保持为 `module-ai-core -> ai-agent-spring-boot-starter`。

**Tech Stack:** Java 17、Spring Boot 3.4.5、Maven、MyBatis-Plus 3.5.9、MySQL、JUnit 5、Mockito、MockMvc、AssertJ。

## Global Constraints

- 遵守 `docs/rules/BACKEND_JAVA_COMMENT_RULES.md`：公共类、公共方法和实体字段使用完整中文 JavaDoc。
- 遵守 `docs/rules/CODE_SIZE_RULES.md`：方法有效代码不超过 80 行，Controller 不超过 400 行，Service 不超过 500 行，Mapper 不超过 200 行。
- Service、Controller 使用唯一 public 构造器注入，不使用字段注入、setter 注入或测试专用构造器。
- 只使用 MySQL；测试不得引入 H2、SQLite 或其他本地数据库。
- HTTP 路径、请求字段、响应 JSON、MySQL 表名和现有数据保持兼容。
- 业务 CRUD 使用 MyBatis-Plus；启动 DDL 继续由独立 SchemaInitializer 负责。
- 不复制参考 `application.yml` 中的数据源密码、AI Key、飞书凭据或其他敏感值。
- 保留工作区内与本任务无关的未提交和已暂存改动；提交使用精确路径和 `git commit --only`。
- 设计依据为提交 `4860d22d` 中的 `docs/superpowers/specs/2026-07-23-ai-management-move-to-module-ai-core-design.md`。

---

## 目标文件结构

```text
ai-agent-spring-boot-starter
└─ com.zimo.starter.ai
   ├─ agent/AiAgentProfile.java
   ├─ agent/AiAgentProvider.java
   ├─ AiAgentService.java
   └─ channel/*

module-ai-core
└─ com.zimo.module.ai
   ├─ controller/
   ├─ dto/
   ├─ entity/
   ├─ management/
   ├─ mapper/
   ├─ service/
   └─ support/
```

`AiAgentManagementService` 只管理智能体并实现 provider；技能业务拆为 `AiSkillManagementService`，避免原 586 行 Service 继续超过仓库上限。

---

### Task 1: 建立 starter 最小运行时契约

**Files:**

- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/agent/AiAgentProfile.java`
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/agent/AiAgentProvider.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/AiAgentService.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/channel/AiChannelHandler.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/channel/AiChannelIntentHandler.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfiguration.java`
- Modify temporarily: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedAgent.java`
- Test: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/channel/AiChannelHandlerTest.java`
- Test: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/AiAgentServiceTest.java`
- Test: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfigurationTest.java`

**Interfaces:**

- Produces: `AiAgentProfile#id()/#name()/#persona()/#model()`
- Produces: `AiAgentProvider#findDefaultAgentForChannel(String): Optional<AiAgentProfile>`
- Changes: `AiAgentService#chat(String, String, AiAgentProfile)`
- Changes: `AiChannelIntentHandler#handle(AiChannelMessage, AiAgentProfile)`

- [ ] **Step 1: Write failing Channel tests**

Add one test where a provider returns a profile and verify the profile reaches `AiAgentService#chat`; add another where provider is absent and verify `chat` receives `null`.

```java
AiAgentProfile profile = new TestAgentProfile("agent-1", "渠道智能体", "渠道助手", "qwen");
AiAgentProvider provider = channel -> Optional.of(profile);
AiChannelHandler handler = new AiChannelHandler(agentService, skillRegistry, provider, List.of());

handler.handle(new AiChannelMessage("feishu", "session-1", "你好", Map.of()));

verify(agentService).chat("你好", "session-1:agent-1", profile);
```

- [ ] **Step 2: Verify the tests fail**

Run:

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiChannelHandlerTest,AiAgentServiceTest,AiAgentAutoConfigurationTest test
```

Expected: compilation fails because the new contracts and signatures do not exist.

- [ ] **Step 3: Implement the contracts**

```java
package com.zimo.starter.ai.agent;

/**
 * AI 智能体运行时视图，只暴露对话和渠道路由所需属性，不包含管理或持久化能力。
 *
 * @author xingju
 * @since 2026-07-23
 */
public interface AiAgentProfile {
    /** @return 智能体稳定标识 */
    String id();

    /** @return 智能体显示名称 */
    String name();

    /** @return 模型系统角色提示词 */
    String persona();

    /** @return 智能体指定的模型名称 */
    String model();
}
```

```java
package com.zimo.starter.ai.agent;

import java.util.Optional;

/**
 * 渠道默认智能体提供者，由业务模块实现。
 *
 * @author xingju
 * @since 2026-07-23
 */
public interface AiAgentProvider {
    /**
     * 查询指定渠道的默认启用智能体。
     *
     * @param channel 渠道编码，不允许为空白
     * @return 匹配的智能体；不存在时返回 Optional.empty()
     */
    Optional<AiAgentProfile> findDefaultAgentForChannel(String channel);
}
```

- [ ] **Step 4: Replace starter management dependencies**

Make the temporary `AiManagedAgent` implement `AiAgentProfile`. Change `AiAgentService` and `AiChannelIntentHandler` parameters to `AiAgentProfile`.

`AiChannelHandler` stores `AiAgentProvider` and resolves the profile:

```java
private AiAgentProfile defaultAgent(AiChannelMessage message) {
    if (agentProvider == null || message == null) {
        return null;
    }
    return agentProvider.findDefaultAgentForChannel(message.channel()).orElse(null);
}
```

Auto-configuration uses an optional provider:

```java
@Bean
@ConditionalOnMissingBean
public AiChannelHandler aiChannelHandler(
        AiAgentService aiAgentService,
        AiSkillRegistry skillRegistry,
        ObjectProvider<AiAgentProvider> agentProvider,
        List<AiChannelIntentHandler> intentHandlers) {
    return new AiChannelHandler(
            aiAgentService,
            skillRegistry,
            agentProvider.getIfAvailable(),
            intentHandlers);
}
```

- [ ] **Step 5: Run focused tests**

Run the Step 2 command. Expected: all selected tests pass.

- [ ] **Step 6: Commit**

Stage and commit only the Task 1 paths:

```text
refactor: 解耦 AI 智能体运行时契约
```

---

### Task 2: 将管理业务所有权迁到 module-ai-core

**Files:**

- Modify: `modules/module-ai/module-ai-core/pom.xml`
- Move Controllers to: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/controller/`
- Move seven request/response types to: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/dto/`
- Move `AiManagedAgent`, `AiManagedAgentContributor`, `AiManagedSkill`, `AiManagedSkillConfig`, `AiPromptTemplate` to: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/management/`
- Move Services to: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/service/`
- Move generator and both SchemaInitializer classes to: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/support/`
- Move three Repository interfaces and three JDBC implementations temporarily to: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/persistence/`
- Move all management tests to matching `module-ai-core` test packages
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfiguration.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfigurationTest.java`
- Modify: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfiguration.java`
- Modify: `modules/module-ai/module-ai-autoconfig/src/test/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfigurationTest.java`

**Interfaces:**

- Preserves all `/api/ai/agents`, `/api/ai/skills`, `/api/ai/prompt-templates` endpoints.
- Produces `management.com.zimo.module.ai.AiManagedAgent implements AiAgentProfile`.
- Repository/JDBC classes exist only as a compiling intermediate state until Task 4.

- [ ] **Step 1: Move tests first**

Use `git mv`, update packages/imports only, and keep assertion bodies unchanged. Controller tests import the new DTO/model/service packages; JDBC tests temporarily use `com.zimo.module.ai.persistence`.

- [ ] **Step 2: Verify moved tests fail**

```powershell
mvn -pl modules/module-ai/module-ai-core -am "-Dtest=AiAgentManagementControllerTest,AiPromptTemplateControllerTest,AiAgentManagementServiceTest,AiPromptTemplateServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation fails because production classes have not moved.

- [ ] **Step 3: Add temporary JDBC compile dependency**

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-jdbc</artifactId>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 4: Move production classes**

Use `git mv`, update package declarations and imports. Keep class behavior unchanged. The moved record declaration is:

```java
public record AiManagedAgent(
        String id,
        String name,
        String desc,
        String persona,
        String model,
        Long promptTemplateId,
        List<String> skillIds,
        boolean enabled,
        String userId,
        String userName,
        List<String> defaultChannels) implements AiAgentProfile {
}
```

- [ ] **Step 5: Transfer current Bean wiring**

Remove all management Bean methods and imports from `AiAgentAutoConfiguration`. Add equivalent JDBC-backed Bean methods to `AiModuleAutoConfiguration`, including both SchemaInitializer beans with `initMethod = "initialize"`. This keeps the application operational during the intermediate commit.

```java
@Bean
@ConditionalOnBean(DataSource.class)
@ConditionalOnMissingBean(AiManagedAgentRepository.class)
public AiManagedAgentRepository aiManagedAgentRepository(DataSource dataSource) {
    return new JdbcAiManagedAgentRepository(dataSource);
}
```

- [ ] **Step 6: Run starter and module tests**

```powershell
mvn -pl modules/ai-agent-spring-boot-starter,modules/module-ai/module-ai-core,modules/module-ai/module-ai-autoconfig -am "-Dtest=AiAgentAutoConfigurationTest,AiModuleAutoConfigurationTest,AiAgentManagementControllerTest,AiPromptTemplateControllerTest,AiAgentManagementServiceTest,AiPromptTemplateServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: selected tests pass and management Bean ownership belongs to `module-ai-autoconfig`.

- [ ] **Step 7: Commit**

```text
refactor: 迁移 AI 管理能力到业务模块
```

---

### Task 3: 建立 MyBatis-Plus Entity、Mapper 和转换器

**Files:**

- Modify: `modules/module-ai/module-ai-core/pom.xml`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/entity/AiManagedAgentEntity.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/entity/AiManagedSkillConfigEntity.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/entity/AiPromptTemplateEntity.java`
- Create: three Mapper interfaces under `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/mapper/`
- Create: three converters under `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/support/`
- Test: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/mapper/AiMapperMetadataTest.java`
- Test: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/support/AiEntityConverterTest.java`

**Interfaces:**

- `AiManagedAgentMapper extends BaseMapper<AiManagedAgentEntity>`
- `AiManagedSkillConfigMapper extends BaseMapper<AiManagedSkillConfigEntity>`
- `AiPromptTemplateMapper extends BaseMapper<AiPromptTemplateEntity>`
- Each converter provides `toEntity` and `toModel`.

- [ ] **Step 1: Write failing metadata and round-trip tests**

```java
assertThat(TableInfoHelper.getTableInfo(AiManagedAgentEntity.class).getTableName())
        .isEqualTo("ai_managed_agent");
assertThat(TableInfoHelper.getTableInfo(AiManagedSkillConfigEntity.class).isWithLogicDelete())
        .isTrue();
assertThat(TableInfoHelper.getTableInfo(AiPromptTemplateEntity.class).isWithLogicDelete())
        .isTrue();
```

Round-trip tests cover JSON list fields, request headers, nullable foreign keys, Boolean defaults and audit timestamps.

- [ ] **Step 2: Verify tests fail**

```powershell
mvn -pl modules/module-ai/module-ai-core -Dtest=AiMapperMetadataTest,AiEntityConverterTest test
```

Expected: compilation fails because mapping classes do not exist.

- [ ] **Step 3: Add MyBatis-Plus compile dependency**

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 4: Implement entities**

Use `@Data`, `@TableName`, `@TableId` and `@TableLogic`. The exact persisted properties are:

```java
@Data
@TableName("ai_managed_agent")
public class AiManagedAgentEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    @TableField("agent_name")
    private String agentName;
    @TableField("agent_desc")
    private String agentDescription;
    private String persona;
    @TableField("model_name")
    private String modelName;
    private Long promptTemplateId;
    private String skillIds;
    private Boolean enabled;
    private String userId;
    private String userName;
    private String defaultChannels;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`AiManagedSkillConfigEntity` contains the existing fields `id`, `agentId`, `skillName`, `skillDescription`, `skillType`, `readOnly`, `enabled`, `baseUrl`, `apiPath`, `httpMethod`, `requestHeaders`, `timeoutMillis`, `apiRegistryId`, `promptTemplateId`, `deleted`, `createdBy`, `updatedBy`, `createdAt`, `updatedAt`.

`AiPromptTemplateEntity` contains all current prompt-template properties and uses `@TableId(type = IdType.AUTO)` plus `@TableLogic` on `deleted`.

- [ ] **Step 5: Implement Mappers**

```java
@Mapper
public interface AiManagedAgentMapper extends BaseMapper<AiManagedAgentEntity> {
    /**
     * 统计全部历史记录，包含逻辑删除数据。
     *
     * @return 历史记录总数；空表返回 0
     */
    @Select("SELECT COUNT(*) FROM ai_managed_agent")
    long countAllIncludingDeleted();
}
```

The other two Mappers only extend `BaseMapper`. All Mapper classes receive Chinese JavaDoc describing module, table and data boundary.

- [ ] **Step 6: Implement converters**

```java
public AiManagedAgentEntity toEntity(AiManagedAgent source);
public AiManagedAgent toModel(AiManagedAgentEntity source);

public AiManagedSkillConfigEntity toEntity(AiManagedSkillConfig source);
public AiManagedSkillConfig toModel(AiManagedSkillConfigEntity source);

public AiPromptTemplateEntity toEntity(AiPromptTemplate source);
public AiPromptTemplate toModel(AiPromptTemplateEntity source);
```

Agent converter owns Jackson serialization for `skillIds` and `defaultChannels`. Convert `deleted` between `0/1` and boolean.

- [ ] **Step 7: Run tests and commit**

Run the Step 2 command. Expected: tests pass without starting a database.

Commit:

```text
feat: 建立 AI 管理 MyBatis-Plus 映射
```

---

### Task 4: 用 Mapper 重写 Service 并原子替换自动配置

**Files:**

- Modify: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/service/AiAgentManagementService.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/service/AiSkillManagementService.java`
- Modify: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/service/AiPromptTemplateService.java`
- Modify: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/controller/AiAgentManagementController.java`
- Delete: all six classes under `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/persistence/`
- Modify: `modules/module-ai/module-ai-autoconfig/pom.xml`
- Modify: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfiguration.java`
- Modify: `modules/module-ai/module-ai-autoconfig/src/test/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfigurationTest.java`
- Rewrite: three Service tests and the management Controller test
- Delete: three JDBC Repository tests

**Interfaces:**

- `AiAgentManagementService implements AiAgentProvider`
- `AiSkillManagementService` owns all skill CRUD/configuration methods.
- `AiPromptTemplateService` uses `AiPromptTemplateMapper`.
- Controller injects agent and skill Services through one public constructor.

- [ ] **Step 1: Rewrite Service tests against mocked Mappers**

```java
@Mock
private AiManagedAgentMapper agentMapper;

@BeforeEach
void setUp() {
    service = new AiAgentManagementService(
            agentMapper,
            List.of(),
            new AiManagedAgentEntityConverter());
}
```

Cover list ordering, create, update-not-found, delete result, default-channel selection, historical-row seed behavior and skill-binding removal. Skill tests preserve registry rollback, header redaction and prompt binding. Prompt tests preserve validation, list filters and timestamp behavior.

- [ ] **Step 2: Verify tests fail**

```powershell
mvn -pl modules/module-ai/module-ai-core -Dtest=AiAgentManagementServiceTest,AiSkillManagementServiceTest,AiPromptTemplateServiceTest test
```

Expected: compilation or assertion failures while Services still use Repository.

- [ ] **Step 3: Rewrite agent Service**

Constructor:

```java
public AiAgentManagementService(
        AiManagedAgentMapper agentMapper,
        List<AiManagedAgentContributor> contributors,
        AiManagedAgentEntityConverter converter) {
    this.agentMapper = Objects.requireNonNull(agentMapper, "agentMapper must not be null");
    this.contributors = contributors == null ? List.of() : List.copyOf(contributors);
    this.converter = Objects.requireNonNull(converter, "converter must not be null");
    loadManagedAgents();
}
```

Provider method:

```java
@Override
public synchronized Optional<AiAgentProfile> findDefaultAgentForChannel(String channel) {
    return agents.values().stream()
            .filter(AiManagedAgent::enabled)
            .filter(agent -> agent.isDefaultForChannel(channel))
            .map(AiAgentProfile.class::cast)
            .findFirst();
}
```

Use `selectList` with `orderByAsc(createdAt, id)`, `selectById`, `insert`, `updateById`, `deleteById` and `countAllIncludingDeleted`. Update the in-memory map only after Mapper success.

- [ ] **Step 4: Extract skill Service**

Required public API:

```java
public synchronized List<AiManagedSkill> listSkills();
public synchronized AiManagedSkill bindSkillPromptTemplate(String name, Long promptTemplateId);
public synchronized AiManagedSkill createApiSkill(AiManagedSkillRequest request);
public synchronized AiManagedSkill updateApiSkill(String name, AiManagedSkillRequest request);
public synchronized AiManagedSkill updateApiSkillConfig(String name, AiSkillApiConfigRequest request);
public synchronized boolean deleteApiSkill(String name);
```

Inject `AiSkillRegistry`, `AiManagedSkillConfigMapper`, `AiAgentManagementService` and converter. Use Lambda Wrapper for `skillName`; preserve registry rollback and sensitive-header redaction.

- [ ] **Step 5: Rewrite prompt Service**

Use:

```java
templateMapper.selectList(
        Wrappers.<AiPromptTemplateEntity>lambdaQuery()
                .orderByDesc(AiPromptTemplateEntity::getId));
```

Typed list adds `enabled = true` and normalized `templateType`. Create uses `insert`, update uses `selectById` plus `updateById`, and delete uses `deleteById`.

- [ ] **Step 6: Update Controller**

```java
public AiAgentManagementController(
        AiAgentManagementService agentManagementService,
        AiSkillManagementService skillManagementService) {
    this.agentManagementService =
            Objects.requireNonNull(agentManagementService, "agentManagementService must not be null");
    this.skillManagementService =
            Objects.requireNonNull(skillManagementService, "skillManagementService must not be null");
}
```

Agent endpoints delegate to agent Service; skill endpoints delegate to skill Service. Paths and responses remain unchanged.

- [ ] **Step 7: Atomically replace auto-configuration and delete JDBC CRUD**

Add the non-optional MyBatis-Plus starter dependency to `module-ai-autoconfig/pom.xml`. Remove broad `@ComponentScan`. Use a database-conditional nested Mapper configuration:

```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean({DataSource.class, SqlSessionFactory.class})
@MapperScan("com.zimo.module.ai.mapper")
static class AiManagementMapperConfiguration {
}
```

Replace Repository Bean methods with converter, agent Service, skill Service, prompt Service and Controller Bean methods. Only after `AiModuleAutoConfigurationTest` passes, delete all six Repository/JDBC classes and their tests.

- [ ] **Step 8: Run tests**

```powershell
mvn -pl modules/module-ai/module-ai-core,modules/module-ai/module-ai-autoconfig -am "-Dtest=AiAgentManagementServiceTest,AiSkillManagementServiceTest,AiPromptTemplateServiceTest,AiAgentManagementControllerTest,AiPromptTemplateControllerTest,AiMapperMetadataTest,AiEntityConverterTest,AiModuleAutoConfigurationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: all selected tests pass and no auto-configuration import references deleted Repository types.

- [ ] **Step 9: Check size limits and commit**

```powershell
Get-ChildItem 'modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/service' -Filter '*.java' |
    ForEach-Object { [pscustomobject]@{ Name = $_.Name; Lines = (Get-Content $_.FullName).Count } } |
    Format-Table -AutoSize
```

Expected: each Service is below 500 physical lines and each business method is below 80 effective lines.

Commit:

```text
refactor: 使用 MyBatis-Plus 管理 AI 数据
```

---

### Task 5: 完善表初始化、条件装配和配置

**Files:**

- Modify: both SchemaInitializer classes under `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/support/`
- Modify: both SchemaInitializer tests under `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/support/`
- Modify: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfiguration.java`
- Modify: `modules/module-ai/module-ai-autoconfig/src/test/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfigurationTest.java`
- Modify only if missing keys: `admin-shell/src/main/resources/application.yml`

**Interfaces:**

- Management Beans exist only with `DataSource` and `SqlSessionFactory`.
- Plugin register and module skill remain available under the existing plugin condition.
- DDL remains MySQL-only and separate from Mapper CRUD.

- [ ] **Step 1: Add conditional-context tests**

Test three contexts:

1. database infrastructure present: Mappers, Services, Controllers, provider and initializers exist;
2. database infrastructure absent: plugin register and module skill exist, management Beans do not;
3. `plugin.ai.enabled=false`: neither plugin nor management Beans exist.

- [ ] **Step 2: Verify the new assertions fail, then finalize conditions**

```powershell
mvn -pl modules/module-ai/module-ai-autoconfig -Dtest=AiModuleAutoConfigurationTest test
```

Expected before implementation: at least one conditional branch fails. Keep Mapper scanning in the nested configuration from Task 4 and apply `@ConditionalOnBean({DataSource.class, SqlSessionFactory.class})` to management Bean methods.

- [ ] **Step 3: Verify MyBatis-Plus configuration**

Confirm the existing main application block contains:

```yaml
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

Add only missing MyBatis-Plus keys. Do not alter datasource values or copy the referenced material module configuration.

- [ ] **Step 4: Run configuration and SchemaInitializer tests**

```powershell
mvn -pl modules/module-ai/module-ai-core,modules/module-ai/module-ai-autoconfig -am "-Dtest=AiModuleAutoConfigurationTest,AiManagedSkillConfigSchemaInitializerTest,AiPromptTemplateSchemaInitializerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: tests pass without H2 or SQLite.

- [ ] **Step 5: Commit**

```text
refactor: 完善 AI 管理模块条件装配
```

---

### Task 6: 更新项目管理等调用方

**Files:**

- Modify: `modules/xingju-project-mgmt-starter/xingju-project-mgmt-core/pom.xml`
- Modify: `ProjectManagementAiAgentContributor.java`
- Modify: `ProjectManagementAiChannelIntentHandler.java`
- Modify: `ProjectManagementAiAgentIntegrationTest.java`
- Modify every additional source returned by the old-package search.

**Interfaces:**

- Contributor imports models and SPI from `module-ai-core`.
- Channel intent handler receives starter `AiAgentProfile`.
- Integration test imports the moved management Service.

- [ ] **Step 1: Find every caller**

```powershell
rg -n 'com\.xingju\.starter\.ai\.management|AiAgentManagementService|AiManagedAgent' modules admin-shell --glob '*.java' --glob '!**/target/**'
```

- [ ] **Step 2: Change the integration test first and verify failure**

Use:

```java
import com.zimo.module.ai.service.AiAgentManagementService;
```

Run:

```powershell
mvn -pl modules/xingju-project-mgmt-starter/xingju-project-mgmt-core -am "-Dtest=ProjectManagementAiAgentIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation fails until production imports/dependency change.

- [ ] **Step 3: Update dependency and production imports**

Add:

```xml
<dependency>
    <groupId>com.zimo</groupId>
    <artifactId>module-ai-core</artifactId>
</dependency>
```

Contributor imports `management.com.zimo.module.ai.AiManagedAgent` and `AiManagedAgentContributor`. Channel intent methods use `com.zimo.starter.ai.agent.AiAgentProfile`.

- [ ] **Step 4: Run affected tests and old-package search**

```powershell
mvn -pl modules/xingju-project-mgmt-starter/xingju-project-mgmt-core -am "-Dtest=ProjectManagementAiAgentIntegrationTest,*AiChannel*" "-Dsurefire.failIfNoSpecifiedTests=false" test
rg -n 'com\.xingju\.starter\.ai\.management' modules admin-shell --glob '*.java' --glob '!**/target/**'
```

Expected: tests pass and search returns no matches.

- [ ] **Step 5: Commit**

```text
refactor: 更新 AI 管理模块调用方
```

---

### Task 7: 删除 starter 管理残留并增加边界测试

**Files:**

- Delete any remaining files under `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/`
- Delete any remaining management tests under starter
- Modify: `modules/ai-agent-spring-boot-starter/pom.xml`
- Create: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/AiStarterBoundaryTest.java`
- Modify: `modules/module-ai/module-ai-core/pom.xml`

**Interfaces:**

- Enforces no starter management package.
- Enforces no `starter -> module-ai-core` dependency.
- Enforces no starter JDBC CRUD dependency.

- [ ] **Step 1: Write the failing boundary test**

```java
@Test
void starterContainsNoManagementProductionPackage() throws Exception {
    Path sourceRoot = Path.of("src", "main", "java", "com", "xingju", "starter", "ai");
    try (Stream<Path> files = Files.walk(sourceRoot)) {
        assertThat(files.filter(Files::isRegularFile)
                .map(Path::toString)
                .filter(path -> path.contains(File.separator + "management" + File.separator)))
                .isEmpty();
    }
}

@Test
void starterPomDoesNotDependOnBusinessModuleOrJdbcCrud() throws Exception {
    String pom = Files.readString(Path.of("pom.xml"));
    assertThat(pom).doesNotContain("<artifactId>module-ai-core</artifactId>");
    assertThat(pom).doesNotContain("<artifactId>spring-jdbc</artifactId>");
}
```

- [ ] **Step 2: Verify failure**

```powershell
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiStarterBoundaryTest test
```

- [ ] **Step 3: Remove leftovers and unused dependencies**

Delete the old management directories only after repository-wide imports are clean. Remove `spring-jdbc` from starter. Remove `spring-boot-starter-validation` only if:

```powershell
rg -n 'jakarta\.validation|javax\.validation' modules/ai-agent-spring-boot-starter/src/main/java
```

returns no matches.

Remove the temporary explicit `spring-jdbc` dependency from `module-ai-core` only if compilation proves SchemaInitializer compile types remain available; otherwise retain it solely for DDL initialization.

- [ ] **Step 4: Run starter and AI module tests**

```powershell
mvn -pl modules/ai-agent-spring-boot-starter test
mvn -pl modules/module-ai/module-ai-core,modules/module-ai/module-ai-autoconfig -am test
```

Expected: BUILD SUCCESS for both commands.

- [ ] **Step 5: Commit**

```text
refactor: 收紧 AI starter 模块边界
```

---

### Task 8: 最终验证

**Files:**

- Verify only; modify production files only when a failing test proves an in-scope defect.

- [ ] **Step 1: Verify removed types**

```powershell
rg -n 'com\.xingju\.starter\.ai\.management|JdbcAi.*Repository|interface Ai.*Repository' modules admin-shell --glob '*.java' --glob '!**/target/**'
```

Expected: no matches for the old management package or JDBC Repository types.

- [ ] **Step 2: Verify MyBatis-Plus mappings**

```powershell
rg -n '@Mapper|extends BaseMapper|@TableName|@TableLogic' modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai
```

Expected: three Mappers, three table entities and three logical-delete fields.

- [ ] **Step 3: Verify forbidden databases**

```powershell
rg -n 'sqlite|h2database|jdbc:h2|jdbc:sqlite' modules/module-ai modules/ai-agent-spring-boot-starter --glob 'pom.xml' --glob '*.java' --glob '*.yml' --glob '*.yaml'
```

Expected: no matches.

- [ ] **Step 4: Run affected tests**

```powershell
mvn -pl modules/ai-agent-spring-boot-starter,modules/module-ai/module-ai-core,modules/module-ai/module-ai-autoconfig,modules/xingju-project-mgmt-starter/xingju-project-mgmt-core -am test
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Package the main application**

```powershell
mvn -pl admin-shell -am package
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Inspect task diff**

```powershell
git status --short
git diff --check
git diff -- modules/ai-agent-spring-boot-starter modules/module-ai modules/xingju-project-mgmt-starter/xingju-project-mgmt-core admin-shell/src/main/resources/application.yml
```

Expected: no whitespace errors, credentials changes, generated artifacts or unrelated staged files from this task.

- [ ] **Step 7: Run shared-contract verification**

Because `AiChannelIntentHandler` is a shared Java contract:

```powershell
mvn clean verify
```

Expected: BUILD SUCCESS. If unrelated pre-existing worktree changes fail, record the exact failing module and rerun the narrow affected-module command to distinguish task defects.

- [ ] **Step 8: Commit only verification fixes**

If verification required in-scope fixes:

```text
fix: 完成 AI 管理模块迁移验证
```

If no fixes were needed, do not create an empty commit.
