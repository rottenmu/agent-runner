# AI 管理能力迁移至 module-ai Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将智能体、技能和提示词模板的管理接口、Service、DAO 从 `ai-agent-spring-boot-starter` 迁移到 `module-ai`，持久化改为 MyBatis-Plus，并同步修正管理页面 API。

**Architecture:** starter 只保留聊天、技能运行时、渠道、MCP/A2A 及中性的智能体运行时 SPI；`module-ai-core` 承担三组管理领域代码，`module-ai-autoconfig` 负责 Mapper 扫描、Schema 初始化和 Bean 装配。管理接口统一为 `/api/biz/ai/** + R<T> + BizException`，前端 API 层统一解包 `R.data`。

**Tech Stack:** Java 17、Spring Boot 3.4.5、MyBatis-Plus 3.5.9、MySQL、JUnit 5、Mockito、MockMvc、Vue 3、Axios、Node Test Runner、Maven。

## Global Constraints

- 设计依据：`docs/superpowers/specs/2026-07-23-ai-management-module-migration-design.md`。
- 数据库仅允许 MySQL；禁止 H2、SQLite 或其他本地数据库。
- `module-ai -> ai-agent-spring-boot-starter` 保持单向依赖，starter 禁止依赖 `module-ai`。
- 只迁移智能体、技能和提示词模板管理能力；MCP、A2A、Channel 和聊天运行时保留在 starter。
- 管理接口统一使用 `/api/biz/ai/**` 和 `R<T>`；旧 `/api/ai/agents|skills|prompt-templates` 删除。
- `/api/ai/mcp`、`/api/ai/a2a/**` 保持不变。
- DAO 使用 MyBatis-Plus `BaseMapper`；业务 CRUD 禁止使用 `JdbcTemplate`。
- Entity 使用 `@TableName`、`@TableId`、`@TableLogic`；结构化字段使用 JSON TypeHandler。
- Service、Controller Bean 使用唯一普通 public 构造器注入。
- Java 注释遵守 `docs/rules/BACKEND_JAVA_COMMENT_RULES.md`。
- 代码行数遵守 `docs/rules/CODE_SIZE_RULES.md`。
- 不输出、复制或提交任何凭据。
- 开始每个任务前执行 `git status --short` 和相关文件 `git diff`，保留工作区已有改动；不得覆盖、回退或提交无关改动。

---

## File Structure

### starter 新增

- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/agent/AiAgentProfile.java`
- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/agent/AiAgentProfileResolver.java`


`AiAgentProfile` 保留 ID、名称、模型、系统提示词和 `skillIds`；`skillIds` 是现有 `AiChannelIntentHandler` 限制项目管理技能路由所需的最小运行时字段，不包含 CRUD 或数据库语义。

### module-ai-core 新增

```text
com.zimo.module.ai
├─ agent/
│  ├─ controller/AiAgentAdminController.java
│  ├─ dto/AiAgentRequest.java
│  ├─ dto/AiAgentResponse.java
│  ├─ entity/AiAgentEntity.java
│  ├─ mapper/AiAgentMapper.java
│  ├─ service/AiAgentManagementService.java
│  ├─ support/AiManagedAgentContributor.java
│  └─ support/AiAgentConverter.java
├─ skill/
│  ├─ controller/AiSkillAdminController.java
│  ├─ dto/AiManagedSkill.java
│  ├─ dto/AiManagedSkillRequest.java
│  ├─ dto/AiSkillApiConfigRequest.java
│  ├─ dto/AiSkillApiConfigResponse.java
│  ├─ dto/AiSkillPromptTemplateRequest.java
│  ├─ entity/AiSkillConfigEntity.java
│  ├─ mapper/AiSkillConfigMapper.java
│  └─ service/AiSkillManagementService.java
└─ prompt/
   ├─ controller/AiPromptTemplateAdminController.java
   ├─ dto/AiPromptTemplateGenerateRequest.java
   ├─ dto/AiPromptTemplateRequest.java
   ├─ dto/AiPromptTemplateResponse.java
   ├─ entity/AiPromptTemplateEntity.java
   ├─ mapper/AiPromptTemplateMapper.java
   ├─ service/AiPromptTemplateService.java
   └─ support/AiPromptTemplateGenerator.java
```

### module-ai-autoconfig 新增

- `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiManagementAutoConfiguration.java`
- `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiManagementSchemaInitializer.java`

### starter 删除

- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/` 全目录
- `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/` 全目录

---

### Task 1: 用测试固定 starter 智能体运行时 SPI

**Files:**
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/agent/AiAgentProfile.java`
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/agent/AiAgentProfileResolver.java`

- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/AiAgentService.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/channel/AiChannelHandler.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/channel/AiChannelIntentHandler.java`
- Test: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/AiAgentServiceTest.java`
- Test: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/channel/AiChannelHandlerTest.java`

**Interfaces:**
- Produces: `AiAgentProfile`, `AiAgentProfileResolver#resolveDefaultForChannel(String)`。
- Consumes: existing `AiAgentProperties`, `AiChatClient`, `AiSkillRegistry`.

- [ ] **Step 1: 写失败测试，证明对话服务只需要运行时 Profile**

在 `AiAgentServiceTest` 新增：

```java
@Test
void usesRuntimeProfileForNameModelAndSystemPrompt() {
    AiAgentProfile profile = new AiAgentProfile(
            "project-management",
            "项目管理智能体",
            "qwen-max",
            "只处理项目管理请求",
            List.of("manufacturing_pm_query_view"));

    AiAgentReply reply = service(readyRuntime(), request -> {
        assertThat(request.agentName()).isEqualTo("项目管理智能体");
        assertThat(request.modelName()).isEqualTo("qwen-max");
        assertThat(request.systemPrompt()).isEqualTo("只处理项目管理请求");
        return AiChatResponse.ok("ok");
    }).chat("查询项目", "feishu:t1:c1:u1", profile);

    assertThat(reply.content()).isEqualTo("ok");
}
```

- [ ] **Step 2: 写失败测试，证明渠道可以在没有 Resolver 时降级**

在 `AiChannelHandlerTest` 新增两个场景：

```java
@Test
void chatsWithGlobalAgentWhenResolverIsMissing() {
    AiChannelHandler handler = new AiChannelHandler(agentService, skillRegistry, null, List.of());
    AiChannelReply reply = handler.handle(message("feishu", "查询库存"));
    assertThat(reply.content()).isEqualTo("global-reply");
}

@Test
void usesResolvedProfileAndPassesItToIntentHandlers() {
    AiAgentProfile profile = new AiAgentProfile(
            "inventory-agent", "库存管家", "qwen-plus", "关注库存风险", List.of("inventory_query"));
    AiAgentProfileResolver resolver = channel -> Optional.of(profile);
    AtomicReference<AiAgentProfile> captured = new AtomicReference<>();
    AiChannelIntentHandler intent = (message, resolved) -> {
        captured.set(resolved);
        return null;
    };
    AiChannelHandler handler = new AiChannelHandler(agentService, skillRegistry, resolver, List.of(intent));

    handler.handle(message("feishu", "查询库存"));

    assertThat(captured.get()).isEqualTo(profile);
}
```

- [ ] **Step 3: 运行测试确认失败**

Run:

```text
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentServiceTest,AiChannelHandlerTest test
```

Expected: FAIL，提示 `AiAgentProfile`、`AiAgentProfileResolver` 不存在或方法签名不匹配。

- [ ] **Step 4: 实现最小运行时契约**

`AiAgentProfile.java`：

```java
package com.zimo.starter.ai.agent;

import java.util.List;

public record AiAgentProfile(
        String id,
        String name,
        String modelName,
        String systemPrompt,
        List<String> skillIds) {
    public AiAgentProfile {
        skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
    }
}
```

`AiAgentProfileResolver.java`：

```java
package com.zimo.starter.ai.agent;

import java.util.Optional;

public interface AiAgentProfileResolver {
    Optional<AiAgentProfile> resolveDefaultForChannel(String channel);
}
```


将 `AiAgentService#chat` 的第三个参数改为 `AiAgentProfile`，将 `agent.name()/model()/persona()` 分别替换为 `profile.name()/modelName()/systemPrompt()`。

将 `AiChannelIntentHandler` 改为：

```java
public interface AiChannelIntentHandler {
    AiChannelReply handle(AiChannelMessage message, AiAgentProfile defaultAgent);
}
```

将 `AiChannelHandler` 中的 `AiAgentManagementService` 替换为可空 `AiAgentProfileResolver`，默认智能体查询改为：

```java
private AiAgentProfile defaultAgent(AiChannelMessage message) {
    if (profileResolver == null || message == null) {
        return null;
    }
    return profileResolver.resolveDefaultForChannel(message.channel()).orElse(null);
}
```

- [ ] **Step 5: 运行最窄测试确认通过**

Run:

```text
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentServiceTest,AiChannelHandlerTest test
```

Expected: PASS。

- [ ] **Step 6: 提交本任务**

```text
git add modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/agent
git add modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/AiAgentService.java
git add modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/channel
git add modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/AiAgentServiceTest.java
git add modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/channel/AiChannelHandlerTest.java
git commit -m "refactor: decouple ai agent runtime profile"
```

---

### Task 2: 建立 module-ai MyBatis-Plus Entity 与 Mapper

**Files:**
- Modify: `modules/module-ai/module-ai-core/pom.xml`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/agent/entity/AiAgentEntity.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/agent/mapper/AiAgentMapper.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skill/entity/AiSkillConfigEntity.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skill/mapper/AiSkillConfigMapper.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/prompt/entity/AiPromptTemplateEntity.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/prompt/mapper/AiPromptTemplateMapper.java`
- Test: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/persistence/AiManagementEntityMappingTest.java`

**Interfaces:**
- Produces: three MyBatis-Plus `BaseMapper` interfaces.
- Consumes: existing MySQL tables `ai_managed_agent`, `ai_agent_skill_config`, `ai_prompt_template`.

- [ ] **Step 1: 写 Entity 映射失败测试**

测试使用 `TableInfoHelper` 和 `MybatisConfiguration`，不连接 H2/MySQL：

```java
@Test
void mapsAgentEntityToExistingTable() {
    TableInfo table = TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            AiAgentEntity.class);
    assertThat(table.getTableName()).isEqualTo("ai_managed_agent");
    assertThat(table.getKeyProperty()).isEqualTo("id");
    assertThat(table.isWithLogicDelete()).isTrue();
}

@Test
void mapsAllManagementMappersToBaseMapper() {
    assertThat(BaseMapper.class).isAssignableFrom(AiAgentMapper.class);
    assertThat(BaseMapper.class).isAssignableFrom(AiSkillConfigMapper.class);
    assertThat(BaseMapper.class).isAssignableFrom(AiPromptTemplateMapper.class);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -Dtest=AiManagementEntityMappingTest test
```

Expected: FAIL，相关 Entity/Mapper 不存在。

- [ ] **Step 3: 增加 MyBatis-Plus 依赖**

在 `module-ai-core/pom.xml` 增加：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
</dependency>
```

- [ ] **Step 4: 实现三个 Entity**

`AiAgentEntity` 的关键映射：

```java
@TableName(value = "ai_managed_agent", autoResultMap = true)
public class AiAgentEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String agentName;
    private String agentDesc;
    private String persona;
    private String modelName;
    private Long promptTemplateId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> skillIds;
    private boolean enabled;
    private String userId;
    private String userName;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> defaultChannels;
    @TableLogic
    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`AiSkillConfigEntity` 使用 `IdType.AUTO`，映射当前技能配置表全部字段；`requestHeaders` 使用 `Map<String, String>` 和 `JacksonTypeHandler`。

`AiPromptTemplateEntity` 使用 `IdType.AUTO`，映射模板编码、名称、类型、CoSTAR 六段、来源、启用、逻辑删除和审计字段。

- [ ] **Step 5: 实现三个 Mapper**

每个 Mapper 只继承 `BaseMapper<T>`，不增加 XML 或自定义 SQL：

```java
public interface AiAgentMapper extends BaseMapper<AiAgentEntity> {
}
```

- [ ] **Step 6: 运行映射测试**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -Dtest=AiManagementEntityMappingTest test
```

Expected: PASS。

- [ ] **Step 7: 提交本任务**

```text
git add modules/module-ai/module-ai-core/pom.xml
git add modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/agent
git add modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skill/entity
git add modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skill/mapper
git add modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/prompt
git add modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/persistence
git commit -m "feat: add mybatis plus ai management mappings"
```

---

### Task 3: 迁移提示词模板管理 Service

**Files:**
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/prompt/dto/AiPromptTemplateRequest.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/prompt/dto/AiPromptTemplateGenerateRequest.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/prompt/dto/AiPromptTemplateResponse.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/prompt/service/AiPromptTemplateService.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/prompt/support/AiPromptTemplateGenerator.java`
- Test: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/prompt/service/AiPromptTemplateServiceTest.java`

**Interfaces:**
- Consumes: `AiPromptTemplateMapper`.
- Produces: list/create/update/delete/generate methods returning API response DTOs.

- [ ] **Step 1: 迁移并改写原 Service 测试**

从 starter 的 `AiPromptTemplateServiceTest` 迁移场景，Mapper 使用 Mockito：

```java
@Test
void listsOnlyRequestedTemplateType() {
    when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity("skill")));
    List<AiPromptTemplateResponse> result = service.list("skill");
    assertThat(result).extracting(AiPromptTemplateResponse::templateType).containsExactly("skill");
}

@Test
void softDeletesExistingTemplate() {
    when(mapper.deleteById(7L)).thenReturn(1);
    assertThat(service.delete(7L)).isTrue();
    verify(mapper).deleteById(7L);
}
```

保留原有校验：`templateType` 只能为 `agent/skill`，`sourceType` 只能为 `manual/generated`，CoSTAR 六段必填。

- [ ] **Step 2: 运行测试确认失败**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -Dtest=AiPromptTemplateServiceTest test
```

Expected: FAIL，DTO、Service、Generator 尚不存在。

- [ ] **Step 3: 实现 DTO、转换和生成器**

`AiPromptTemplateResponse` 使用 record，字段保持现有前端契约；Generator 保留现有确定性生成规则，不调用大模型。

- [ ] **Step 4: 使用 Mapper 实现 Service**

Service 签名：

```java
public List<AiPromptTemplateResponse> list(String templateType);
public AiPromptTemplateResponse create(AiPromptTemplateRequest request);
public AiPromptTemplateResponse update(Long id, AiPromptTemplateRequest request);
public boolean delete(Long id);
public AiPromptTemplateResponse generate(AiPromptTemplateGenerateRequest request);
```

查询使用 `Wrappers.<AiPromptTemplateEntity>lambdaQuery()`；保存使用 `insert/updateById/deleteById`。不创建 Repository 包装层。

- [ ] **Step 5: 运行测试确认通过**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -Dtest=AiPromptTemplateServiceTest test
```

Expected: PASS。

- [ ] **Step 6: 提交本任务**

```text
git add modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/prompt
git add modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/prompt
git commit -m "feat: move ai prompt management to module ai"
```

---

### Task 4: 迁移智能体管理 Service 并实现运行时 Resolver

**Files:**
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/agent/dto/AiAgentRequest.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/agent/dto/AiAgentResponse.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/agent/service/AiAgentManagementService.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/agent/support/AiManagedAgentContributor.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/agent/support/AiAgentConverter.java`
- Test: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/agent/service/AiAgentManagementServiceTest.java`

**Interfaces:**
- Consumes: `AiAgentMapper`, `List<AiManagedAgentContributor>`。
- Produces: CRUD Service and `AiAgentProfileResolver`.

- [ ] **Step 1: 写失败测试**

覆盖：

```java
@Test
void resolvesEnabledDefaultAgentForChannel() {
    AiAgentEntity entity = agent("project-management", true, List.of("feishu"));
    when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));

    Optional<AiAgentProfile> profile = service.resolveDefaultForChannel("FEISHU");

    assertThat(profile).get()
            .extracting(AiAgentProfile::id, AiAgentProfile::name)
            .containsExactly("project-management", "项目管理智能体");
}

@Test
void removesDeletedSkillFromEveryAgentInOneServiceOperation() {
    when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            agentWithSkills("a1", "remote_check", "echo"),
            agentWithSkills("a2", "remote_check")));
    service.removeSkillBinding("remote_check");
    verify(mapper, times(2)).updateById(any(AiAgentEntity.class));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -Dtest=AiAgentManagementServiceTest test
```

Expected: FAIL。

- [ ] **Step 3: 实现智能体 CRUD 和 Resolver**

类声明：

```java
@Transactional(readOnly = true)
public class AiAgentManagementService implements AiAgentProfileResolver {
```

写操作使用方法级 `@Transactional`。`resolveDefaultForChannel` 只返回启用且 `defaultChannels` 包含目标渠道的第一条记录，比较时忽略大小写。

智能体 ID 延续现有 `"a" + timestamp + sequence` 策略；更新不存在返回 `null`，删除不存在返回 `false`。

- [ ] **Step 4: 处理贡献 Profile 和默认数据**

在 `module-ai-core` 定义 `AiManagedAgentContributor#agents()`，返回完整 `AiAgentRequest` 列表。表无历史记录时将贡献请求转换为种子记录，并补充采购、库存、排程智能体；表已有历史记录时不覆盖。

- [ ] **Step 5: 运行测试确认通过**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -Dtest=AiAgentManagementServiceTest test
```

Expected: PASS。

- [ ] **Step 6: 提交本任务**

```text
git add modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/agent
git add modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/agent
git commit -m "feat: move ai agent management to module ai"
```

---

### Task 5: 迁移 API 技能管理 Service

**Files:**
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skill/dto/AiManagedSkill.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skill/dto/AiManagedSkillRequest.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skill/dto/AiSkillApiConfigRequest.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skill/dto/AiSkillApiConfigResponse.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skill/dto/AiSkillPromptTemplateRequest.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skill/service/AiSkillManagementService.java`
- Test: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/skill/service/AiSkillManagementServiceTest.java`

**Interfaces:**
- Consumes: `AiSkillConfigMapper`, `AiAgentManagementService`, starter `AiSkillRegistry`.
- Produces: Bean/API 技能查询、API 技能 CRUD、模板绑定和启动恢复。

- [ ] **Step 1: 迁移技能管理测试并改用 Mapper Mock**

必须覆盖：

- Bean 技能不可编辑/删除；
- API 技能名称创建后不可修改；
- 请求头敏感字段返回 `[redacted]`；
- 更新传入 `[redacted]` 时保留原值；
- 注册失败时恢复原运行时技能；
- 删除技能时调用 `AiAgentManagementService#removeSkillBinding`；
- `loadConfiguredApiSkills` 将未删除配置注册到 `AiSkillRegistry`。

示例：

```java
@Test
void keepsExistingSecretWhenRequestContainsRedactedValue() {
    when(mapper.selectOne(any())).thenReturn(configWithHeaders(
            Map.of("Authorization", "Bearer secret-token")));

    AiManagedSkill result = service.updateApiSkill(
            "remote_check",
            request(Map.of("Authorization", "[redacted]")));

    assertThat(capturedEntity().getRequestHeaders())
            .containsEntry("Authorization", "Bearer secret-token");
    assertThat(result.apiConfig().headers())
            .containsEntry("Authorization", "[redacted]");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -Dtest=AiSkillManagementServiceTest test
```

Expected: FAIL。

- [ ] **Step 3: 实现 DTO 和 Service**

Service 对外方法：

```java
public List<AiManagedSkill> listSkills();
public AiManagedSkill createApiSkill(AiManagedSkillRequest request);
public AiManagedSkill updateApiSkill(String name, AiManagedSkillRequest request);
public AiManagedSkill updateApiSkillConfig(String name, AiSkillApiConfigRequest request);
public AiManagedSkill bindSkillPromptTemplate(String name, Long promptTemplateId);
public boolean deleteApiSkill(String name);
public void loadConfiguredApiSkills();
```

数据库操作使用 `AiSkillConfigMapper`；运行时调用继续复用 starter 的 `AiSkillRegistry#registerApiSkill/removeApiSkill`。

- [ ] **Step 4: 增加事务和失败补偿**

新增/更新/删除标注 `@Transactional`。更新运行时注册失败时重新注册旧的 `AiApiSkillConfig` 后抛出异常，确保调用方收到失败。

- [ ] **Step 5: 运行测试确认通过**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -Dtest=AiSkillManagementServiceTest test
```

Expected: PASS。

- [ ] **Step 6: 提交本任务**

```text
git add modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skill
git add modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/skill
git commit -m "feat: move ai skill management to module ai"
```

---

### Task 6: 迁移三组管理 Controller 到平台接口

**Files:**
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/agent/controller/AiAgentAdminController.java`
- Modify: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/controller/AiSkillAdminController.java`
- Move: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/controller/AiSkillAdminController.java` to `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/skill/controller/AiSkillAdminController.java`
- Create: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/prompt/controller/AiPromptTemplateAdminController.java`
- Test: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/agent/controller/AiAgentAdminControllerTest.java`
- Move/Modify: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/controller/AiSkillAdminControllerTest.java`
- Test: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/prompt/controller/AiPromptTemplateAdminControllerTest.java`

**Interfaces:**
- Consumes: three module-ai management Services.
- Produces: `/api/biz/ai/agents|skills|prompt-templates`.

- [ ] **Step 1: 写三个 Controller 的失败契约测试**

每组验证成功响应：

```java
mockMvc.perform(get("/api/biz/ai/agents"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data[0].id").value("a1"));
```

并直接调用 Controller 验证不存在资源抛出：

```java
assertThatThrownBy(() -> controller.update("missing", request))
        .isInstanceOf(BizException.class)
        .extracting("code")
        .isEqualTo(404);
```

- [ ] **Step 2: 运行 Controller 测试确认失败**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -Dtest='*AdminControllerTest' test
```

Expected: FAIL，智能体和模板 Controller 不存在，技能 Controller 仍依赖 starter 管理 Service。

- [ ] **Step 3: 实现 Controller**

类级路径：

```java
@RequestMapping("/api/biz/ai/agents")
@RequestMapping("/api/biz/ai/skills")
@RequestMapping("/api/biz/ai/prompt-templates")
```

所有成功方法返回 `R.ok(data)` 或 `R.ok()`；不存在资源抛出 `new BizException(404, "...不存在")`；Service 的 `IllegalArgumentException` 转换为 `new BizException(400, message)`。

- [ ] **Step 4: 运行 Controller 测试确认通过**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -Dtest='*AdminControllerTest' test
```

Expected: PASS。

- [ ] **Step 5: 提交本任务**

```text
git add modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai
git add modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai
git commit -m "feat: expose ai management platform apis"
```

---

### Task 7: 迁移 Schema 初始化与自动配置

**Files:**
- Modify: `modules/module-ai/module-ai-autoconfig/pom.xml`
- Modify: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfiguration.java`
- Delete: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiSkillAdminAutoConfiguration.java`
- Create: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiManagementAutoConfiguration.java`
- Create: `modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiManagementSchemaInitializer.java`
- Modify: `modules/module-ai/module-ai-autoconfig/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `modules/module-ai/module-ai-autoconfig/src/test/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfigurationTest.java`
- Create: `modules/module-ai/module-ai-autoconfig/src/test/java/com/xingju/module/ai/autoconfig/AiManagementSchemaInitializerTest.java`

**Interfaces:**
- Consumes: `DataSource`, three Mapper、starter `AiSkillRegistry`.
- Produces: management Services、Resolver、Controllers and schema-ready ordering.

- [ ] **Step 1: 先审计现有未提交改动**

Run:

```text
git diff -- modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfiguration.java
git diff -- modules/module-ai/module-ai-autoconfig/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
git diff -- modules/module-ai/module-ai-autoconfig/src/test/java/com/xingju/module/ai/autoconfig/AiModuleAutoConfigurationTest.java
```

Expected: 明确现有用户改动并在其基础上合并，不覆盖。

- [ ] **Step 2: 写自动配置失败测试**

测试断言：

```java
assertThat(context).hasSingleBean(AiAgentManagementService.class);
assertThat(context).hasSingleBean(AiSkillManagementService.class);
assertThat(context).hasSingleBean(AiPromptTemplateService.class);
assertThat(context).hasSingleBean(AiAgentProfileResolver.class);
assertThat(context).hasSingleBean(AiAgentAdminController.class);
assertThat(context).hasSingleBean(AiSkillAdminController.class);
assertThat(context).hasSingleBean(AiPromptTemplateAdminController.class);
```

另一个上下文不提供 `DataSource`，断言上述持久化管理 Bean 不创建。

- [ ] **Step 3: 写 Schema 初始化顺序失败测试**

使用 Mockito `DataSource/Connection/Statement` 捕获 SQL，断言包含：

```text
CREATE TABLE IF NOT EXISTS ai_managed_agent
CREATE TABLE IF NOT EXISTS ai_agent_skill_config
CREATE TABLE IF NOT EXISTS ai_prompt_template
```

并断言只出现 MySQL `ENGINE=InnoDB`，不出现 H2/SQLite 方言。

- [ ] **Step 4: 实现统一 Schema 初始化器**

将 starter 两个初始化器中的 MySQL DDL 移入新类，并补齐 `ai_managed_agent`。初始化器只执行 DDL，不承载 CRUD。

- [ ] **Step 5: 实现自动配置**

`AiManagementAutoConfiguration` 使用：

```java
@AutoConfiguration(after = AiAgentAutoConfiguration.class)
@ConditionalOnProperty(
        prefix = "plugin.ai",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnBean(DataSource.class)
@MapperScan(basePackageClasses = {
        AiAgentMapper.class,
        AiSkillConfigMapper.class,
        AiPromptTemplateMapper.class
})
```

Schema 初始化 Bean 作为三个 Service Bean 的显式依赖参数，保证查询数据库前完成 DDL。

- [ ] **Step 6: 更新 imports 和测试**

`AutoConfiguration.imports` 只保留有效配置类，并确保基础插件配置先于管理配置加载。

- [ ] **Step 7: 运行自动配置测试**

Run:

```text
mvn -pl modules/module-ai/module-ai-autoconfig -am -Dtest=AiModuleAutoConfigurationTest,AiManagementSchemaInitializerTest test
```

Expected: PASS。

- [ ] **Step 8: 提交本任务**

只暂存本任务实际修改且已核对的文件：

```text
git add modules/module-ai/module-ai-autoconfig/pom.xml
git add modules/module-ai/module-ai-autoconfig/src/main/java/com/xingju/module/ai/autoconfig
git add modules/module-ai/module-ai-autoconfig/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
git add modules/module-ai/module-ai-autoconfig/src/test/java/com/xingju/module/ai/autoconfig
git commit -m "feat: autoconfigure ai management persistence"
```

---

### Task 8: 从 starter 删除管理业务和 JDBC 依赖

**Files:**
- Modify: `modules/ai-agent-spring-boot-starter/pom.xml`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfiguration.java`
- Delete: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/**`
- Delete: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/**`
- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfigurationTest.java`

**Interfaces:**
- Produces: management-free starter.
- Consumes: `AiAgentProfileResolver` optionally via `ObjectProvider`.

- [ ] **Step 1: 先修改自动配置测试表达目标边界**

删除所有 starter management 类型断言，新增：

```java
assertThat(context).doesNotHaveBean("aiAgentManagementService");
assertThat(context).doesNotHaveBean("aiAgentManagementController");
assertThat(context).doesNotHaveBean("aiPromptTemplateController");
assertThat(context).hasSingleBean(McpController.class);
assertThat(context).hasSingleBean(A2aController.class);
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```text
mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentAutoConfigurationTest test
```

Expected: FAIL，starter 仍装配管理 Bean。

- [ ] **Step 3: 清理 `AiAgentAutoConfiguration`**

删除管理 Bean 方法及 imports。`AiChannelHandler` Bean 改为接收：

```java
ObjectProvider<AiAgentProfileResolver> profileResolver
```

并传入 `profileResolver.getIfAvailable()`。

- [ ] **Step 4: 删除 management 生产和测试目录**

删除前先运行：

```text
rg -n "com\\.xingju\\.starter\\.ai\\.management" modules admin-shell -g '*.java' -g '!target'
```

确认剩余引用已在后续任务清单中。删除整个 starter management 包，避免双实现。

- [ ] **Step 5: 从 starter POM 移除 Spring JDBC**

删除：

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-jdbc</artifactId>
</dependency>
```

- [ ] **Step 6: 运行 starter 全部测试**

Run:

```text
mvn -pl modules/ai-agent-spring-boot-starter test
```

Expected: PASS。

- [ ] **Step 7: 提交本任务**

```text
git add modules/ai-agent-spring-boot-starter/pom.xml
git add modules/ai-agent-spring-boot-starter/src/main/java
git add modules/ai-agent-spring-boot-starter/src/test/java
git commit -m "refactor: remove ai management from starter"
```

---

### Task 9: 修复项目管理等 Java 调用方

**Files:**
- Modify: `modules/xingju-project-mgmt-starter/xingju-project-mgmt-core/src/main/java/com/xingju/manufacturingpm/aiskill/ProjectManagementAiAgentContributor.java`
- Modify: `modules/xingju-project-mgmt-starter/xingju-project-mgmt-core/src/main/java/com/xingju/manufacturingpm/aiskill/ProjectManagementAiChannelIntentHandler.java`
- Modify: `modules/xingju-project-mgmt-starter/xingju-project-mgmt-core/src/test/java/com/xingju/manufacturingpm/aiskill/ProjectManagementAiAgentIntegrationTest.java`
- Modify: `modules/xingju-project-mgmt-starter/xingju-project-mgmt-core/pom.xml`
- Modify: all remaining Java files reported by `rg "com\\.xingju\\.starter\\.ai\\.management"`.

**Interfaces:**
- Consumes: starter `AiAgentProfile` and module-ai `AiManagedAgentContributor`。
- Produces: no references to deleted starter management package.

- [ ] **Step 1: 运行引用扫描并保存清单**

Run:

```text
rg -n "com\\.xingju\\.starter\\.ai\\.management" modules admin-shell -g '*.java' -g '!target'
```

Expected: 只出现需要迁移的调用方；完成任务后输出为空。

- [ ] **Step 2: 修改项目管理贡献器**

在项目管理 core POM 增加 `module-ai-core` 依赖。贡献器实现 `AiManagedAgentContributor` 并返回完整管理请求：

```java
public class ProjectManagementAiAgentContributor implements AiManagedAgentContributor {
    @Override
    public List<AiAgentRequest> agents() {
        AiAgentRequest request = new AiAgentRequest();
        request.setName("项目管理智能体");
        request.setDescription("负责项目创建、编号生成、视图查询和 Excel 解析导入");
        request.setModel("qwen-plus");
        request.setPersona(PROJECT_MANAGEMENT_SYSTEM_PROMPT);
        request.setSkillIds(List.of(
                ProjectManagementAiChannelIntentHandler.PROJECT_EXCEL_SKILL,
                ProjectManagementAiChannelIntentHandler.PROJECT_IMPORT_EXCEL_SKILL,
                ProjectManagementAiChannelIntentHandler.PROJECT_CODE_SKILL,
                ProjectManagementAiChannelIntentHandler.PROJECT_VIEW_SKILL));
        request.setEnabled(true);
        request.setDefaultChannels(List.of("feishu"));
        return List.of(request);
    }
}
```

- [ ] **Step 3: 修改项目管理意图处理器**

将参数类型改为 `AiAgentProfile`，`skillIds()` 和 `id()` 调用保持语义不变。

- [ ] **Step 4: 修改集成测试**

集成测试不再直接获取已删除的 starter 管理 Service；改为验证：

- 贡献器返回完整 `AiAgentRequest`；
- Feishu 渠道 Resolver 返回项目管理 Profile；
- Channel Intent Handler 按 Profile 技能列表路由。

- [ ] **Step 5: 运行受影响测试**

Run:

```text
mvn -pl modules/xingju-project-mgmt-starter/xingju-project-mgmt-core -am test
```

Expected: PASS。

- [ ] **Step 6: 确认没有旧包引用**

Run:

```text
rg -n "com\\.xingju\\.starter\\.ai\\.management" modules admin-shell -g '*.java' -g '!target'
```

Expected: 无输出。

- [ ] **Step 7: 提交本任务**

```text
git add modules/xingju-project-mgmt-starter/xingju-project-mgmt-core
git commit -m "refactor: migrate ai management consumers"
```


---

### Task 10: 修改智能体管理页面 API 与响应解包

**Files:**
- Modify: `frontend/modules/ai/src/api/agent.js`
- Modify: `frontend/modules/ai/src/views/AiAgentManage.vue`
- Modify: `frontend/modules/ai/src/views/AiPromptTemplateManage.vue`
- Create: `frontend/modules/ai/tests/management-api-contract.test.mjs`
- Modify: `frontend/modules/ai/tests/skill-edit-delete-actions.test.mjs`
- Modify: `frontend/modules/ai/tests/prompt-template-static.test.mjs`

**Interfaces:**
- Consumes: `/api/biz/ai/**` responses shaped as `{ code, msg, data }`.
- Produces: API functions returning only `data`.

- [ ] **Step 1: 写失败的前端 API 静态测试**

`management-api-contract.test.mjs`：

```javascript
test('management api uses module-ai platform paths', () => {
  const source = readFileSync(apiFile, 'utf8')
  assert.match(source, /request\.get\('\/biz\/ai\/agents'\)/)
  assert.match(source, /request\.get\('\/biz\/ai\/skills'\)/)
  assert.match(source, /request\.get\('\/biz\/ai\/prompt-templates'/)
  assert.doesNotMatch(source, /request\.(get|post|put|delete)\('\/ai\/(agents|skills|prompt-templates)/)
})

test('workbench keeps runtime protocol paths', () => {
  const source = readFileSync(workbenchFile, 'utf8')
  assert.match(source, /fetch\('\/api\/ai\/mcp'/)
  assert.match(source, /fetch\('\/api\/ai\/a2a\/message'/)
})
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```text
node --test frontend/modules/ai/tests/management-api-contract.test.mjs
```

Expected: FAIL，`agent.js` 仍使用 `/ai/**`。

- [ ] **Step 3: 在 API 层统一路径和解包**

`agent.js` 增加：

```javascript
function dataOf(response) {
  return response && typeof response === 'object' && 'data' in response
    ? response.data
    : response
}
```

每个方法使用 `return request.xxx(...).then(dataOf)`，路径统一改为 `/biz/ai/**`。

- [ ] **Step 4: 清理页面层重复解包**

`AiAgentManage.vue` 保持直接使用 API 返回的数组/对象。

`AiPromptTemplateManage.vue` 删除只用于 `R<T>` 的 `unwrapData`；`normalizeTemplateList` 只处理业务列表、`records/list/content`。

- [ ] **Step 5: 更新现有静态测试**

技能和提示词测试断言新路径、`R.data` 解包以及页面不再直接解析平台包装。

- [ ] **Step 6: 运行 AI 前端全部测试**

Run:

```text
node --test frontend/modules/ai/tests/*.test.mjs
```

Expected: PASS。

- [ ] **Step 7: 构建 web-shell**

Run:

```text
npm run build
```

Workdir: `frontend/web-shell`

Expected: PASS。

- [ ] **Step 8: 提交本任务**

```text
git add frontend/modules/ai/src/api/agent.js
git add frontend/modules/ai/src/views/AiAgentManage.vue
git add frontend/modules/ai/src/views/AiPromptTemplateManage.vue
git add frontend/modules/ai/tests/management-api-contract.test.mjs
git add frontend/modules/ai/tests/skill-edit-delete-actions.test.mjs
git add frontend/modules/ai/tests/prompt-template-static.test.mjs
git commit -m "fix: align ai management frontend api"
```

---

### Task 11: 更新文档并执行组合验证

**Files:**
- Modify: `modules/ai-agent-spring-boot-starter/README.md`
- Modify: `modules/CONTROLLER_API.md`
- Create: `modules/module-ai/README.md` if it does not exist
- Modify: any module-local Chinese API document that still claims management endpoints live in starter.

**Interfaces:**
- Produces: documented final module boundary and API list.
- Consumes: completed code from Tasks 1–10.

- [ ] **Step 1: 更新中文文档**

starter README 仅保留：

- 对话、技能运行时、渠道、MCP/A2A；
- `AiAgentProfileResolver` SPI；
- 明确管理能力由 `module-ai` 提供。

`module-ai/README.md` 记录：

- 三组 `/api/biz/ai/**` 管理接口；
- MyBatis-Plus + MySQL 持久化；
- 表名和逻辑删除；
- 管理前端对应页面。

- [ ] **Step 2: 执行静态边界检查**

Run:

```text
rg -n "com\\.xingju\\.starter\\.ai\\.management" modules admin-shell frontend -g '!target' -g '!dist'
rg -n "/api/ai/(agents|skills|prompt-templates)" modules frontend -g '!target' -g '!dist'
rg -n "JdbcAi|JdbcTemplate" modules/ai-agent-spring-boot-starter modules/module-ai -g '*.java' -g '!target'
rg -n "h2|sqlite" modules/module-ai -g 'pom.xml' -g '*.java' -g '*.yml' -g '*.yaml' -g '!target'
```

Expected:

- 无旧 management 包引用；
- 旧管理路径只允许出现在迁移历史文档中，不出现在运行代码；
- starter 和 module-ai 业务 CRUD 无 `JdbcAi*`；
- module-ai 无 H2/SQLite 依赖。

- [ ] **Step 3: 运行后端模块验证**

Run:

```text
mvn -pl modules/ai-agent-spring-boot-starter test
mvn -pl modules/module-ai/module-ai-core -am test
mvn -pl modules/module-ai/module-ai-autoconfig -am test
mvn -pl modules/xingju-project-mgmt-starter/xingju-project-mgmt-core -am test
mvn -pl modules/module-feishu/module-feishu-autoconfig -am test
```

Expected: 全部 PASS。

- [ ] **Step 4: 运行前端验证**

Run:

```text
node --test frontend/modules/ai/tests/*.test.mjs
npm run build
```

第二条命令 Workdir: `frontend/web-shell`。

Expected: PASS。

- [ ] **Step 5: 运行主应用组合打包**

Run:

```text
mvn -pl admin-shell -am package
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 6: 检查变更范围**

Run:

```text
git status --short
git diff --check
git diff --stat
```

Expected: 没有空白错误；无关工作区改动仍保持原状且未被暂存。

- [ ] **Step 7: 提交文档与最终修正**

```text
git add modules/ai-agent-spring-boot-starter/README.md
git add modules/CONTROLLER_API.md
git add modules/module-ai/README.md
git commit -m "docs: document ai management module boundary"
```
