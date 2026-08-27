# AI Prompt Template Scope And Binding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `ai-agent-spring-boot-starter` 中把提示词模板分为智能体模板和技能模板，并让智能体管理页引用这些模板。

**Architecture:** 后端在现有提示词模板表与服务上增加 `templateType`，智能体管理继续使用当前内存模型，在智能体记录和注册技能视图上增加 `promptTemplateId`。前端把提示词管理作为智能体管理页内二级入口，智能体表单只加载 `agent` 模板，技能卡片只绑定 `skill` 模板。

**Tech Stack:** Java 17、Spring Boot 3.4.5、JUnit 5、Spring MockMvc、Vue 3、Element Plus、Node.js 静态测试。

## Global Constraints

- 数据库只面向 MySQL，不使用 SQLite、H2 或本地数据库。
- `ai_prompt_template` 只保留 `PRIMARY KEY (id)`，不新增普通索引或唯一索引。
- 数据库表注释和字段注释使用中文。
- Service 层、Controller 层 Bean 只保留一个 public 构造器，使用构造器注入。
- 不编辑 `target/`、`dist/` 等生成产物。
- 不回滚工作区已有无关改动。
- 新增或修改的 Markdown 文档使用中文。
- 生产代码必须先有失败测试，再写最小实现。

---

## File Structure

- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplate.java`
  - 增加 `templateType` 字段、getter、setter。
- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateRequest.java`
  - 接收创建和编辑模板时的 `templateType`。
- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateGenerateRequest.java`
  - 接收生成草稿时的 `templateType`。
- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateService.java`
  - 校验模板类型，支持 `list(String templateType)`，生成草稿时回填类型。
- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateRepository.java`
  - 增加按类型查询接口。
- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/JdbcAiPromptTemplateRepository.java`
  - SQL 映射新增 `template_type`。
- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateController.java`
  - `GET /api/ai/prompt-templates` 增加可选 `templateType` 参数。
- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateSchemaInitializer.java`
  - 建表 SQL 增加 `template_type`，初始化时补充缺失列。
- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedAgent.java`
  - 返回 `promptTemplateId`。
- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedAgentRequest.java`
  - 接收 `promptTemplateId`。
- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkill.java`
  - 返回 `promptTemplateId`。
- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiSkillPromptTemplateRequest.java`
  - 新增技能绑定请求体。
- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiAgentManagementService.java`
  - 保存智能体模板引用，维护技能模板绑定映射。
- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiAgentManagementController.java`
  - 新增 `PUT /api/ai/skills/{name}/prompt-template`。
- `frontend/modules/ai/menus.js`
  - 移除提示词管理一级菜单。
- `frontend/modules/ai/src/api/agent.js`
  - 增加技能模板绑定 API，保留模板列表参数。
- `frontend/modules/ai/src/views/AiAgentManage.vue`
  - 增加提示词管理二级页、智能体模板选择器、技能模板绑定控件。
- `frontend/modules/ai/src/views/AiPromptTemplateManage.vue`
  - 支持传入嵌入模式，增加类型筛选与模板类型字段。
- `frontend/modules/ai/src/components/AiPromptTemplateDrawer.vue`
  - 增加模板类型选择，生成和保存时带上 `templateType`。
- `frontend/modules/ai/tests/prompt-template-static.test.mjs`
  - 调整菜单、页面、API 和模板类型静态断言。

---

### Task 1: 提示词模板类型与 MySQL 表结构

**Files:**
- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiPromptTemplateServiceTest.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiPromptTemplateControllerTest.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiPromptTemplateSchemaInitializerTest.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplate.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateRequest.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateGenerateRequest.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateService.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateRepository.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/JdbcAiPromptTemplateRepository.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateController.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiPromptTemplateSchemaInitializer.java`

**Interfaces:**
- Consumes: existing `AiPromptTemplateService#create`, `update`, `generate`, `list`.
- Produces: `AiPromptTemplateService#list(String templateType)`, `AiPromptTemplate#getTemplateType()`, request JSON field `templateType`.

- [ ] **Step 1: Write failing service tests**

Add tests that describe default type, explicit skill type, type filtering, invalid type, and generated draft type:

```java
@Test
void createDefaultsTemplateTypeToAgentWhenRequestOmitsType() {
    InMemoryPromptTemplateRepository repository = new InMemoryPromptTemplateRepository();
    AiPromptTemplateService service = new AiPromptTemplateService(repository, new AiPromptTemplateGenerator());

    AiPromptTemplate saved = service.create(completeRequest());

    assertThat(saved.getTemplateType()).isEqualTo("agent");
}

@Test
void createSavesSkillTemplateType() {
    InMemoryPromptTemplateRepository repository = new InMemoryPromptTemplateRepository();
    AiPromptTemplateService service = new AiPromptTemplateService(repository, new AiPromptTemplateGenerator());
    AiPromptTemplateRequest request = completeRequest();
    request.setTemplateType("skill");

    AiPromptTemplate saved = service.create(request);

    assertThat(saved.getTemplateType()).isEqualTo("skill");
}

@Test
void listDelegatesOptionalTemplateTypeFilterToRepository() {
    InMemoryPromptTemplateRepository repository = new InMemoryPromptTemplateRepository();
    AiPromptTemplateService service = new AiPromptTemplateService(repository, new AiPromptTemplateGenerator());

    service.list("skill");

    assertThat(repository.lastTemplateType()).isEqualTo("skill");
}

@Test
void createRejectsUnsupportedTemplateType() {
    AiPromptTemplateService service = new AiPromptTemplateService(
            new InMemoryPromptTemplateRepository(),
            new AiPromptTemplateGenerator());
    AiPromptTemplateRequest request = completeRequest();
    request.setTemplateType("workflow");

    assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("templateType");
}
```

- [ ] **Step 2: Run tests to verify RED**

Run: `mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiPromptTemplateServiceTest test`

Expected: FAIL because `getTemplateType`, `setTemplateType`, and `list(String)` do not exist.

- [ ] **Step 3: Write failing controller and schema tests**

Add controller assertion:

```java
@Test
void listsPromptTemplatesByTemplateType() throws Exception {
    when(service.list("skill")).thenReturn(List.of(template(5L, "技能模板", "manual")));

    mockMvc.perform(get("/api/ai/prompt-templates").param("templateType", "skill"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(5));

    verify(service).list("skill");
}
```

Add schema assertions:

```java
assertThat(sql).contains("template_type VARCHAR(20) NOT NULL DEFAULT 'agent'");
assertThat(sql).contains("COMMENT '模板类型：agent智能体提示词模板，skill技能提示词模板'");
```

- [ ] **Step 4: Run tests to verify RED**

Run: `mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiPromptTemplateControllerTest,AiPromptTemplateSchemaInitializerTest test`

Expected: FAIL because controller has no query parameter and schema SQL has no `template_type`.

- [ ] **Step 5: Implement minimal backend template type support**

Implement:

```java
private String templateType = "agent";

public String getTemplateType() {
    return templateType;
}

public void setTemplateType(String templateType) {
    this.templateType = templateType;
}
```

Add normalization:

```java
private String normalizeTemplateType(String templateType) {
    String normalized = StringUtils.hasText(templateType) ? templateType.trim() : "agent";
    if (!"agent".equals(normalized) && !"skill".equals(normalized)) {
        throw new IllegalArgumentException("templateType只能为agent或skill");
    }
    return normalized;
}
```

Add repository interface:

```java
List<AiPromptTemplate> listActive(String templateType);
```

Keep existing `listActive()` delegating to unfiltered behavior, and add `list(String templateType)` in service.

- [ ] **Step 6: Implement minimal MySQL SQL changes**

Add `template_type` to create SQL, insert SQL, update SQL, select SQL, and row mapper. In schema initializer, after create table run a column-existence query against `information_schema.columns`; when count is zero execute:

```sql
ALTER TABLE ai_prompt_template
ADD COLUMN template_type VARCHAR(20) NOT NULL DEFAULT 'agent'
COMMENT '模板类型：agent智能体提示词模板，skill技能提示词模板'
AFTER description
```

- [ ] **Step 7: Run tests to verify GREEN**

Run: `mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiPromptTemplateServiceTest,AiPromptTemplateControllerTest,AiPromptTemplateSchemaInitializerTest test`

Expected: PASS.

- [ ] **Step 8: Commit**

Run:

```bash
git add modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management \
  modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management
git commit --only modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management \
  modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management \
  -m "feat: scope prompt templates by type"
```

---

### Task 2: 智能体与技能绑定提示词模板

**Files:**
- Modify: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiAgentManagementControllerTest.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedAgent.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedAgentRequest.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiManagedSkill.java`
- Create: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiSkillPromptTemplateRequest.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiAgentManagementService.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management/AiAgentManagementController.java`

**Interfaces:**
- Consumes: registered skills from `AiSkillRegistry`.
- Produces: `AiManagedAgent.promptTemplateId`, `AiManagedSkill.promptTemplateId`, `PUT /api/ai/skills/{name}/prompt-template`.

- [ ] **Step 1: Write failing agent and skill binding tests**

Extend create/update payload:

```json
"promptTemplateId": 101
```

Assert:

```java
.andExpect(jsonPath("$.promptTemplateId").value(101))
```

Add skill binding test:

```java
@Test
void bindsAndClearsPromptTemplateForRegisteredSkill() throws Exception {
    mockMvc.perform(put("/api/ai/skills/summarize/prompt-template")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"promptTemplateId\":202}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("summarize"))
            .andExpect(jsonPath("$.promptTemplateId").value(202));

    mockMvc.perform(put("/api/ai/skills/summarize/prompt-template")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"promptTemplateId\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.promptTemplateId").doesNotExist());
}
```

- [ ] **Step 2: Run tests to verify RED**

Run: `mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentManagementControllerTest test`

Expected: FAIL because request field, response field, request class, and endpoint do not exist.

- [ ] **Step 3: Implement minimal agent field support**

Add `Long promptTemplateId` to record/request and pass it through `toAgent`.

Seeded agents and contributed agents use `null` unless already provided.

- [ ] **Step 4: Implement minimal skill binding endpoint**

Add request class:

```java
package com.zimo.starter.ai.management;

public class AiSkillPromptTemplateRequest {
    private Long promptTemplateId;

    public Long getPromptTemplateId() {
        return promptTemplateId;
    }

    public void setPromptTemplateId(Long promptTemplateId) {
        this.promptTemplateId = promptTemplateId;
    }
}
```

Add service method:

```java
public synchronized AiManagedSkill bindSkillPromptTemplate(String name, Long promptTemplateId)
```

It returns `null` when no registered skill matches `name`, removes binding when `promptTemplateId == null`, and otherwise stores the binding.

- [ ] **Step 5: Run tests to verify GREEN**

Run: `mvn -pl modules/ai-agent-spring-boot-starter -Dtest=AiAgentManagementControllerTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management \
  modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiAgentManagementControllerTest.java
git commit --only modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/management \
  modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/management/AiAgentManagementControllerTest.java \
  -m "feat: bind prompt templates to agents and skills"
```

---

### Task 3: 前端二级入口、类型选择与绑定控件

**Files:**
- Modify: `frontend/modules/ai/tests/prompt-template-static.test.mjs`
- Modify: `frontend/modules/ai/menus.js`
- Modify: `frontend/modules/ai/src/api/agent.js`
- Modify: `frontend/modules/ai/src/views/AiAgentManage.vue`
- Modify: `frontend/modules/ai/src/views/AiPromptTemplateManage.vue`
- Modify: `frontend/modules/ai/src/components/AiPromptTemplateDrawer.vue`

**Interfaces:**
- Consumes: `listPromptTemplates({ templateType })`, `bindSkillPromptTemplate(name, { promptTemplateId })`.
- Produces: UI supports internal tabs `agents`, `skills`, `prompts`; prompt payload contains `templateType`.

- [ ] **Step 1: Write failing frontend static assertions**

Change menu assertion:

```js
assert.doesNotMatch(menus, /\/ai\/prompts/, '提示词管理不应再作为 AI 一级菜单')
```

Add page assertions:

```js
const agentPage = readModuleFile('src', 'views', 'AiAgentManage.vue')
assert.match(agentPage, /activeTab === 'prompts'/, '智能体管理页应包含提示词管理二级入口')
assert.match(agentPage, /AiPromptTemplateManage/, '智能体管理页应嵌入提示词管理页面')
assert.match(agentPage, /templateType:\s*'agent'/, '智能体表单应加载智能体模板')
assert.match(agentPage, /templateType:\s*'skill'/, '技能绑定应加载技能模板')
assert.match(api, /bindSkillPromptTemplate/, 'API 应导出技能提示词模板绑定函数')
assert.match(drawer, /templateType/, '提示词抽屉应提交模板类型')
```

- [ ] **Step 2: Run test to verify RED**

Run: `node frontend/modules/ai/tests/prompt-template-static.test.mjs`

Expected: FAIL because menu still has `/ai/prompts` and page has no prompts tab or binding API.

- [ ] **Step 3: Implement API and menu changes**

In `menus.js`, remove `{ path: '/ai/prompts', title: '提示词管理' }`.

In `agent.js`, add:

```js
export function bindSkillPromptTemplate(name, data) {
  return request.put(`/ai/skills/${encodeURIComponent(name)}/prompt-template`, data)
}
```

- [ ] **Step 4: Implement prompt type controls**

In drawer default form add:

```js
templateType: 'agent'
```

In payload add:

```js
templateType: form.templateType
```

In generator emit object:

```js
emit('generate', {
  businessDescription: form.businessDescription,
  templateType: form.templateType
})
```

- [ ] **Step 5: Implement agent management secondary prompt page**

Import `AiPromptTemplateManage`, `listPromptTemplates`, and `bindSkillPromptTemplate`.

Add a sidebar button for `prompts`, render `<AiPromptTemplateManage embedded />`, and load:

```js
const agentPromptTemplates = ref([])
const skillPromptTemplates = ref([])
```

Use `listPromptTemplates({ templateType: 'agent' })` for agent selector and `listPromptTemplates({ templateType: 'skill' })` for skill binding selector.

- [ ] **Step 6: Run frontend test to verify GREEN**

Run: `node frontend/modules/ai/tests/prompt-template-static.test.mjs`

Expected: PASS.

- [ ] **Step 7: Commit**

Run:

```bash
git add frontend/modules/ai/menus.js frontend/modules/ai/src/api/agent.js \
  frontend/modules/ai/src/views/AiAgentManage.vue \
  frontend/modules/ai/src/views/AiPromptTemplateManage.vue \
  frontend/modules/ai/src/components/AiPromptTemplateDrawer.vue \
  frontend/modules/ai/tests/prompt-template-static.test.mjs
git commit --only frontend/modules/ai/menus.js frontend/modules/ai/src/api/agent.js \
  frontend/modules/ai/src/views/AiAgentManage.vue \
  frontend/modules/ai/src/views/AiPromptTemplateManage.vue \
  frontend/modules/ai/src/components/AiPromptTemplateDrawer.vue \
  frontend/modules/ai/tests/prompt-template-static.test.mjs \
  -m "feat: manage prompt templates under agent admin"
```

---

### Task 4: 集成验证与复核

**Files:**
- Verify: `modules/ai-agent-spring-boot-starter`
- Verify: `frontend/modules/ai`
- Verify: `frontend/web-shell`

**Interfaces:**
- Consumes: outputs from Tasks 1-3.
- Produces: final verified implementation and review fixes.

- [ ] **Step 1: Run AI starter tests**

Run: `mvn -pl modules/ai-agent-spring-boot-starter -am test`

Expected: PASS.

- [ ] **Step 2: Run AI frontend static test**

Run: `node frontend/modules/ai/tests/prompt-template-static.test.mjs`

Expected: PASS.

- [ ] **Step 3: Run web shell build**

Run: `cd frontend/web-shell && npm run build`

Expected: PASS, existing chunk size or VueUse warnings are acceptable if no build failure occurs.

- [ ] **Step 4: Request code review**

Use the code review workflow with:

```bash
BASE_SHA=$(git rev-parse b441804)
HEAD_SHA=$(git rev-parse HEAD)
```

Review description: “提示词模板分类为智能体/技能模板，智能体和注册技能引用对应模板，提示词管理入口下沉到智能体管理页。”

- [ ] **Step 5: Fix important review findings**

For each Critical or Important finding, write a failing test first, verify RED, implement the minimal fix, verify GREEN, then commit with a focused message.

- [ ] **Step 6: Final status**

Report changed areas, verification commands, known unrelated workspace dirtiness, and any existing unrelated build failures.
