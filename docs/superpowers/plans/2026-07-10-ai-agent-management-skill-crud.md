# AI Agent Management Skill CRUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在智能体管理模块中实现通用自定义 API 技能的新增、编辑、删除、MySQL 持久化和前端管理入口。

**Architecture:** Java 内置技能继续由 Spring Bean 注册并保持只读保护；自定义 API 技能落库到 `ai_agent_skill_config`，启动时加载并注册到 `AiSkillRegistry`。前端在现有智能体管理页的“技能”页签中增加新建、编辑、删除操作，并通过后端返回的 `source`、`enabled`、`agentId` 区分技能类型。

**Tech Stack:** Java 17、Spring Boot 3.4.5、Spring JDBC、JUnit 5、MockMvc、Vue 3、Element Plus、Node 静态测试。

## Global Constraints

- 只面向 MySQL，不使用 SQLite、H2 或其他本地数据库。
- 新增数据表必须使用用户已确认的 `ai_agent_skill_config` 结构。
- 数据表注释和字段注释均使用中文。
- `ai_agent_skill_config` 只保留 `PRIMARY KEY (id)`，不创建其他索引。
- Java 内置技能不可编辑、不可删除。
- 自定义 API 技能名称创建后不可修改。
- Service 层和 Controller 层 Bean 只保留一个 public 构造器，使用构造器注入。
- 不编辑 `target/`、`dist/` 等生成产物。

---

## File Structure

- Create `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkillConfig.java`：数据库技能配置实体。
- Create `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkillConfigRepository.java`：自定义 API 技能配置仓储接口。
- Create `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/JdbcAiManagedSkillConfigRepository.java`：Spring JDBC 仓储实现。
- Create `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkillConfigSchemaInitializer.java`：MySQL 表初始化器。
- Modify `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkill.java`：增加 `source`、`enabled`、`agentId`。
- Modify `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkillRequest.java`：增加 `agentId`、`enabled`、完整 API 配置字段承载。
- Modify `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiAgentManagementService.java`：接入仓储、加载数据库技能、实现持久化 CRUD。
- Modify `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiAgentManagementController.java`：补齐通用编辑接口和错误语义。
- Modify `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfiguration.java`：注册 schema initializer 和 repository。
- Modify `frontend/modules/ai/src/api/agent.js`：新增 `createSkill`、`updateSkill`、`deleteSkill`。
- Modify `frontend/modules/ai/src/views/AiAgentManage.vue`：技能页新增新建、编辑、删除和抽屉表单。
- Modify `frontend/modules/ai/tests/prompt-template-static.test.mjs`：扩展为智能体管理静态测试，覆盖技能 CRUD 入口。

---

### Task 1: 后端技能配置表和仓储

**Files:**
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkillConfig.java`
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkillConfigRepository.java`
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/JdbcAiManagedSkillConfigRepository.java`
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkillConfigSchemaInitializer.java`
- Test: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiManagedSkillConfigSchemaInitializerTest.java`
- Test: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/JdbcAiManagedSkillConfigRepositoryTest.java`

**Interfaces:**
- Produces: `AiManagedSkillConfigRepository.listActive(): List<AiManagedSkillConfig>`
- Produces: `AiManagedSkillConfigRepository.findActiveByName(String skillName): Optional<AiManagedSkillConfig>`
- Produces: `AiManagedSkillConfigRepository.save(AiManagedSkillConfig config): AiManagedSkillConfig`
- Produces: `AiManagedSkillConfigRepository.softDelete(String skillName): boolean`
- Produces: `AiManagedSkillConfigSchemaInitializer.initialize(): void`

- [ ] **Step 1: Write failing schema test**

Add `AiManagedSkillConfigSchemaInitializerTest` with assertions:

```java
@Test
void initializeCreatesMysqlTableWithOnlyPrimaryKeyIndex() {
    RecordingDataSource dataSource = new RecordingDataSource();
    AiManagedSkillConfigSchemaInitializer initializer = new AiManagedSkillConfigSchemaInitializer(dataSource);

    initializer.initialize();

    String sql = dataSource.singleSql();
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS ai_agent_skill_config");
    assertThat(sql).contains("COMMENT='AI智能体技能配置表'");
    assertThat(sql).contains("COMMENT '主键ID'");
    assertThat(sql).contains("agent_id VARCHAR(64) NULL COMMENT '适用智能体ID，为空表示通用技能'");
    assertThat(sql).contains("PRIMARY KEY (id)");
    assertThat(sql).doesNotContain("UNIQUE KEY");
    assertThat(sql).doesNotContain("CREATE INDEX");
    assertThat(sql).doesNotContain(" KEY idx_");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiManagedSkillConfigSchemaInitializerTest test`

Expected: FAIL because `AiManagedSkillConfigSchemaInitializer` does not exist.

- [ ] **Step 3: Implement schema initializer**

Create `AiManagedSkillConfigSchemaInitializer` with the confirmed MySQL DDL:

```java
public class AiManagedSkillConfigSchemaInitializer {
    private static final String CREATE_TABLE_SQL = """
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
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI智能体技能配置表'
            """;
}
```

- [ ] **Step 4: Write failing repository tests**

Add repository tests covering:

```java
@Test
void saveListUpdateAndSoftDeleteSkillConfig() {
    InMemorySkillConfigRepository repository = new InMemorySkillConfigRepository();
    AiManagedSkillConfig config = new AiManagedSkillConfig();
    config.setAgentId(null);
    config.setSkillName("remote_supplier_lookup");
    config.setSkillDescription("查询供应商");
    config.setSkillType("api");
    config.setReadOnly(true);
    config.setEnabled(true);
    config.setBaseUrl("https://api.example.com");
    config.setApiPath("/supplier");
    config.setHttpMethod("POST");
    config.setRequestHeaders("{\"X-App-Id\":\"demo\"}");
    config.setTimeoutMillis(3000);

    AiManagedSkillConfig saved = repository.save(config);

    assertThat(repository.listActive()).extracting(AiManagedSkillConfig::getSkillName)
            .containsExactly("remote_supplier_lookup");
    saved.setSkillDescription("查询供应商详情");
    repository.save(saved);
    assertThat(repository.findActiveByName("remote_supplier_lookup"))
            .hasValueSatisfying(item -> assertThat(item.getSkillDescription()).isEqualTo("查询供应商详情"));
    assertThat(repository.softDelete("remote_supplier_lookup")).isTrue();
    assertThat(repository.listActive()).isEmpty();
}
```

- [ ] **Step 5: Implement entity and JDBC repository**

Implement `AiManagedSkillConfig`, `AiManagedSkillConfigRepository`, and `JdbcAiManagedSkillConfigRepository` using Spring JDBC and soft delete.

- [ ] **Step 6: Run task tests**

Run: `mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiManagedSkillConfigSchemaInitializerTest,JdbcAiManagedSkillConfigRepositoryTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

Commit: `feat: persist managed ai skills`

---

### Task 2: 后端管理接口接入持久化技能 CRUD

**Files:**
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkill.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkillRequest.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiAgentManagementService.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiAgentManagementController.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfiguration.java`
- Test: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiAgentManagementControllerTest.java`
- Test: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/autoconfig/AiAgentAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `AiManagedSkillConfigRepository`
- Produces: `POST /api/ai/skills`
- Produces: `PUT /api/ai/skills/{name}`
- Produces: `DELETE /api/ai/skills/{name}`
- Produces: `AiManagedSkill(String name, String description, boolean readOnly, long referenceCount, Long promptTemplateId, AiSkillApiConfigResponse apiConfig, String source, boolean enabled, String agentId)`

- [ ] **Step 1: Write failing controller tests**

Extend `AiAgentManagementControllerTest`:

```java
@Test
void createsUpdatesAndDeletesManagedApiSkill() throws Exception {
    mockMvc.perform(post("/api/ai/skills")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "name": "remote_supplier_lookup",
                              "description": "查询供应商",
                              "agentId": null,
                              "readOnly": true,
                              "apiConfig": {
                                "enabled": true,
                                "baseUrl": "https://api.example.com",
                                "path": "/supplier",
                                "method": "POST",
                                "headers": {"X-App-Id": "demo"},
                                "timeoutMillis": 3000
                              }
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("remote_supplier_lookup"))
            .andExpect(jsonPath("$.source").value("api"))
            .andExpect(jsonPath("$.enabled").value(true));

    mockMvc.perform(put("/api/ai/skills/remote_supplier_lookup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "name": "remote_supplier_lookup",
                              "description": "查询供应商详情",
                              "agentId": "a1",
                              "readOnly": false,
                              "apiConfig": {
                                "enabled": false,
                                "baseUrl": "https://api.example.com",
                                "path": "/supplier/detail",
                                "method": "GET",
                                "headers": {},
                                "timeoutMillis": 5000
                              }
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("查询供应商详情"))
            .andExpect(jsonPath("$.agentId").value("a1"))
            .andExpect(jsonPath("$.enabled").value(false));

    mockMvc.perform(delete("/api/ai/skills/remote_supplier_lookup"))
            .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/ai/skills"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name").value(not(hasItem("remote_supplier_lookup"))));
}

@Test
void refusesToDeleteBeanSkill() throws Exception {
    mockMvc.perform(delete("/api/ai/skills/summarize"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("内置技能不可删除"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentManagementControllerTest test`

Expected: FAIL because response fields and `PUT /skills/{name}` are missing or not persisted.

- [ ] **Step 3: Implement service/controller behavior**

Update `AiAgentManagementService` to:

- Accept optional `AiManagedSkillConfigRepository` in its single public constructor.
- Load repository skills in constructor and register them into `AiSkillRegistry`.
- Persist create/update/delete for custom API skills.
- Return `source = "bean"` for Java skills and `source = "api"` for custom API skills.
- Return `enabled` from API config and `true` for Java skills.
- Return `agentId` from persisted config.
- Throw `IllegalArgumentException("内置技能不可删除")` when deleting bean skills.

- [ ] **Step 4: Register auto-configuration beans**

Add beans:

```java
@Bean(initMethod = "initialize")
@ConditionalOnBean(DataSource.class)
@ConditionalOnMissingBean
public AiManagedSkillConfigSchemaInitializer aiManagedSkillConfigSchemaInitializer(DataSource dataSource) {
    return new AiManagedSkillConfigSchemaInitializer(dataSource);
}

@Bean
@ConditionalOnBean(DataSource.class)
@ConditionalOnMissingBean(AiManagedSkillConfigRepository.class)
public AiManagedSkillConfigRepository aiManagedSkillConfigRepository(DataSource dataSource) {
    return new JdbcAiManagedSkillConfigRepository(dataSource);
}
```

- [ ] **Step 5: Run task tests**

Run: `mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentManagementControllerTest,AiAgentAutoConfigurationTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

Commit: `feat: manage custom ai skills`

---

### Task 3: 前端技能 CRUD 页面

**Files:**
- Modify: `frontend/modules/ai/src/api/agent.js`
- Modify: `frontend/modules/ai/src/views/AiAgentManage.vue`
- Modify: `frontend/modules/ai/tests/prompt-template-static.test.mjs`

**Interfaces:**
- Consumes: `createSkill(data): Promise`
- Consumes: `updateSkill(name, data): Promise`
- Consumes: `deleteSkill(name): Promise`
- Produces: 技能页“新建技能”“编辑”“删除”操作。

- [ ] **Step 1: Write failing static frontend tests**

Extend `prompt-template-static.test.mjs`:

```javascript
assertIncludes(agentApiSource, 'export function createSkill(data)', 'agent api should create skills')
assertIncludes(agentApiSource, 'export function updateSkill(name, data)', 'agent api should update skills')
assertIncludes(agentApiSource, 'export function deleteSkill(name)', 'agent api should delete skills')
assertIncludes(agentManageSource, '新建技能', 'skill tab should expose create action')
assertIncludes(agentManageSource, 'openCreateSkill', 'skill tab should open create drawer')
assertIncludes(agentManageSource, 'openEditSkill', 'skill tab should open edit drawer')
assertIncludes(agentManageSource, 'removeSkill', 'skill tab should expose delete action')
assertIncludes(agentManageSource, "skill.source === 'api'", 'bean skills should be protected from custom actions')
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node frontend/modules/ai/tests/prompt-template-static.test.mjs`

Expected: FAIL because skill CRUD UI/API symbols do not exist.

- [ ] **Step 3: Implement frontend API methods**

Add:

```javascript
export function createSkill(data) {
  return request.post('/ai/skills', data)
}

export function updateSkill(name, data) {
  return request.put(`/ai/skills/${encodeURIComponent(name)}`, data)
}

export function deleteSkill(name) {
  return request.delete(`/ai/skills/${encodeURIComponent(name)}`)
}
```

- [ ] **Step 4: Implement skill drawer and actions**

Enhance `AiAgentManage.vue`:

- Add “新建技能” button when `activeTab === 'skills'`.
- Add card actions for API skills only.
- Add drawer fields for skill name, description, agent, readOnly, enabled, baseUrl, path, method, headers JSON, timeout, prompt template.
- Disable skill name field in edit mode.
- Validate headers JSON as object before submit.
- Call `createSkill`, `updateSkill`, `deleteSkill`.
- Refresh skills after save/delete.

- [ ] **Step 5: Run task tests**

Run: `node frontend/modules/ai/tests/prompt-template-static.test.mjs`

Expected: PASS.

- [ ] **Step 6: Commit**

Commit: `feat: add skill management ui`

---

### Task 4: 集成验证和修复

**Files:**
- Modify only files touched by Tasks 1-3 if verification finds issues.

**Interfaces:**
- Consumes: all previous tasks.
- Produces: verified backend and frontend build.

- [ ] **Step 1: Run focused backend tests**

Run: `mvn -pl modules/ai-agent-spring-boot-starter -am test`

Expected: PASS.

- [ ] **Step 2: Run frontend static test**

Run: `node frontend/modules/ai/tests/prompt-template-static.test.mjs`

Expected: PASS.

- [ ] **Step 3: Run frontend build**

Run: `cd frontend/web-shell && npm run build`

Expected: PASS. Existing Rollup chunk warnings are acceptable if unchanged.

- [ ] **Step 4: Fix verification failures with TDD**

For each failure, write or update the smallest failing test that reproduces the problem before changing production code.

- [ ] **Step 5: Commit**

Commit: `test: verify ai skill management`

---

## Self-Review

- Spec coverage: 通用智能体管理范围、MySQL 表、中文注释、仅主键索引、内置技能保护、自定义 API 技能 CRUD、前端入口和验证命令均已覆盖。
- Placeholder scan: 无 `TBD`、`TODO`、`implement later`。
- Type consistency: `AiManagedSkillConfigRepository`、`AiManagedSkillConfig`、`AiManagedSkill` 扩展字段在后端与前端任务中一致。
