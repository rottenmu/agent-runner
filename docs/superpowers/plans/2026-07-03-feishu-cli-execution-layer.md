# 飞书 CLI 执行层 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 `module-feishu` 内新增飞书 CLI 执行层，统一封装标准 `npx @larksuite/cli` 和自研 Wrapper CLI 调用，并提供多维表格、文档、日历、任务业务 Service。

**Architecture:** `module-feishu-core` 提供结构化 DTO、执行器接口、日志服务、前置业务域白名单和业务 Service；`module-feishu-autoconfig` 提供 `ProcessBuilder` 真实执行器和 Spring Boot 自动装配。执行器支持 `NPX` 与 `WRAPPER` 两种模式；飞书 CLI 内部的 Credential、Transport、Restrict、Observer、Wrap、On 扩展由自研 Wrapper CLI 自身承接，Java 层只负责可配置调用和审计。

**Tech Stack:** Java 17、Spring Boot 3.4.5、Maven、MyBatis Plus、Jackson、JUnit 5、AssertJ、Mockito。

## Global Constraints

- 所有新增或修改的 `.md` 文档使用中文。
- 不编辑 `target/`、`dist/` 等生成产物。
- 不复制或输出任何真实飞书凭据。
- CLI 调用使用 `ProcessBuilder` 参数列表，不通过 shell 拼接命令。
- core 单元测试不真实执行 `npx`。
- 默认失败自动重试 1 次，即最多尝试 2 次。
- 默认 CLI 模式为 `NPX`；生产可配置为 `WRAPPER` 指向自研增强版 CLI 可执行文件。
- Java 层必须先按 `allowedBusinessTypes` 做业务域白名单校验，Wrapper CLI 的 `Restrict/Wrap` 是最终安全边界。

---

### Task 1: CLI 通用请求、结果和日志模型

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/FeishuCliCommandRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/FeishuCliCommandResult.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/FeishuCliExecutor.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/FeishuCliException.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/FeishuCliCallLogEntity.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/FeishuCliCallLogMapper.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/FeishuCliCallLogService.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuAgentSchemaInitializer.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/cli/FeishuCliCommandModelTest.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/cli/FeishuCliCallLogServiceTest.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuAgentSchemaInitializerTest.java`

**Interfaces:**
- Produces: `FeishuCliCommandRequest.api(String businessType, String method, String apiPath)`
- Produces: `FeishuCliCommandRequest.withParam(String key, Object value)`
- Produces: `FeishuCliCommandRequest.withData(String key, Object value)`
- Produces: `FeishuCliCommandResult.success(...)` and `FeishuCliCommandResult.failure(...)`
- Produces: `FeishuCliExecutor.execute(FeishuCliCommandRequest request)`
- Produces: `FeishuCliCallLogService.record(FeishuCliCommandRequest request, FeishuCliCommandResult result)`

- [ ] **Step 1: Write the failing model test**

```java
@Test
void createsStructuredApiRequestWithoutHardcodedJson() {
    FeishuCliCommandRequest request = FeishuCliCommandRequest
            .api("bitable", "POST", "/open-apis/bitable/v1/apps/app_token/tables/table_id/records")
            .withData("fields", Map.of("name", "样件A"))
            .withParam("page_size", 20);

    assertThat(request.getBusinessType()).isEqualTo("bitable");
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getApiPath()).contains("/open-apis/bitable");
    assertThat(request.getParams()).containsEntry("page_size", 20);
    assertThat(request.getData()).containsKey("fields");
}
```

- [ ] **Step 2: Run the model test to verify it fails**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuCliCommandModelTest '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: compile failure because `FeishuCliCommandRequest` does not exist.

- [ ] **Step 3: Implement the minimal model classes**

Create `FeishuCliCommandRequest` with final fields `businessType`, `method`, `apiPath`, `params`, `data`, `timeout`, getters, `api(...)`, `withParam(...)`, and `withData(...)`.

Create `FeishuCliCommandResult` with fields `success`, `exitCode`, `stdout`, `stderr`, `errorMessage`, `json`, `costMillis`, `attempts`, plus static factories.

Create `FeishuCliExecutor`:

```java
public interface FeishuCliExecutor {
    FeishuCliCommandResult execute(FeishuCliCommandRequest request);
}
```

Create `FeishuCliException extends RuntimeException`.

- [ ] **Step 4: Run the model test to verify it passes**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuCliCommandModelTest '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: PASS.

- [ ] **Step 5: Write the failing log service test**

```java
@Test
void recordsFinalCliCallResultAndTruncatesLargeOutput() {
    CapturingMapper mapper = new CapturingMapper();
    FeishuCliCallLogService service = new FeishuCliCallLogService(mapper, true);
    FeishuCliCommandRequest request = FeishuCliCommandRequest.api("document", "POST", "/open-apis/docx/v1/documents")
            .withData("title", "测试文档");
    FeishuCliCommandResult result = FeishuCliCommandResult.failure(1, "x".repeat(5000), "stderr", "failed", 33L, 2);

    service.record(request, result);

    assertThat(mapper.saved.getBusinessType()).isEqualTo("document");
    assertThat(mapper.saved.getApiPath()).isEqualTo("/open-apis/docx/v1/documents");
    assertThat(mapper.saved.getSuccess()).isZero();
    assertThat(mapper.saved.getAttempts()).isEqualTo(2);
    assertThat(mapper.saved.getStdout()).hasSizeLessThanOrEqualTo(4096);
}
```

- [ ] **Step 6: Run the log service test to verify it fails**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuCliCallLogServiceTest '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: compile failure because log classes do not exist.

- [ ] **Step 7: Implement log entity, mapper, service, and schema SQL**

Create `FeishuCliCallLogEntity` mapped to `ps_feishu_cli_call_log`.

Create `FeishuCliCallLogMapper extends BaseMapper<FeishuCliCallLogEntity>`.

Create `FeishuCliCallLogService` with safe `record(...)`; catch mapper exceptions and log warning.

Extend `FeishuAgentSchemaInitializer` to create/check `ps_feishu_cli_call_log` with the columns from the design.

- [ ] **Step 8: Run Task 1 tests**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuCliCommandModelTest,FeishuCliCallLogServiceTest,FeishuAgentSchemaInitializerTest '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: PASS.

---

### Task 2: CLI 执行重试模板

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/FeishuCliTemplate.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/FeishuCliPolicy.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/cli/FeishuCliTemplateTest.java`

**Interfaces:**
- Consumes: `FeishuCliExecutor.execute(FeishuCliCommandRequest request)`
- Consumes: `FeishuCliCallLogService.record(FeishuCliCommandRequest request, FeishuCliCommandResult result)`
- Produces: `FeishuCliPolicy.allows(String businessType)`
- Produces: `FeishuCliTemplate.execute(FeishuCliCommandRequest request)`

- [ ] **Step 1: Write the failing retry test**

```java
@Test
void retriesOnceAndRecordsFinalResult() {
    FlakyExecutor executor = new FlakyExecutor();
    CapturingLogService logService = new CapturingLogService();
    FeishuCliTemplate template = new FeishuCliTemplate(executor, logService, FeishuCliPolicy.allowAll(), 1);

    FeishuCliCommandResult result = template.execute(FeishuCliCommandRequest.api("task", "POST", "/open-apis/task/v2/tasks"));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getAttempts()).isEqualTo(2);
    assertThat(executor.calls).isEqualTo(2);
    assertThat(logService.records).hasSize(1);
    assertThat(logService.records.get(0).getAttempts()).isEqualTo(2);
}

@Test
void rejectsBusinessTypeOutsideJavaAllowListBeforeExecutorRuns() {
    FlakyExecutor executor = new FlakyExecutor();
    CapturingLogService logService = new CapturingLogService();
    FeishuCliTemplate template = new FeishuCliTemplate(
            executor,
            logService,
            FeishuCliPolicy.allowOnly(Set.of("document")),
            1
    );

    FeishuCliCommandResult result = template.execute(
            FeishuCliCommandRequest.api("bitable", "POST", "/open-apis/bitable/v1/apps/app/tables/table/records")
    );

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getErrorMessage()).contains("business type is not allowed");
    assertThat(executor.calls).isZero();
    assertThat(logService.records).hasSize(1);
}
```

- [ ] **Step 2: Run the retry test to verify it fails**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuCliTemplateTest '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: compile failure because `FeishuCliTemplate` does not exist.

- [ ] **Step 3: Implement `FeishuCliTemplate`**

Implementation rules:

```java
public FeishuCliCommandResult execute(FeishuCliCommandRequest request) {
    if (!policy.allows(request.getBusinessType())) {
        FeishuCliCommandResult denied = FeishuCliCommandResult.failure(
                -1, "", "", "business type is not allowed: " + request.getBusinessType(), 0L, 0);
        logService.record(request, denied);
        return denied;
    }
    FeishuCliCommandResult latest = null;
    int maxAttempts = Math.max(1, retryTimes + 1);
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        latest = executor.execute(request);
        latest = latest.withAttempts(attempt);
        if (latest.isSuccess()) {
            break;
        }
    }
    logService.record(request, latest);
    return latest;
}
```

If `executor.execute` throws, convert it to failed `FeishuCliCommandResult` and keep retrying until attempts are exhausted.

- [ ] **Step 4: Run the retry test to verify it passes**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuCliTemplateTest '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: PASS.

---

### Task 3: 四类业务 Service 和 DTO

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/bitable/BitableRecordCreateRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/bitable/BitableRecordQueryRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/bitable/BitableCellUpdateRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/bitable/FeishuBitableCliService.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/document/DocumentCreateRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/document/DocumentAppendRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/document/FeishuDocumentCliService.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/calendar/CalendarEventCreateRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/calendar/CalendarAttendeeAddRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/calendar/FeishuCalendarCliService.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/task/TaskCreateRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/task/TaskAssigneeRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/task/FeishuTaskCliService.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/cli/FeishuCliBusinessServiceTest.java`

**Interfaces:**
- Consumes: `FeishuCliTemplate.execute(FeishuCliCommandRequest request)`
- Produces: `FeishuBitableCliService.createRecord(BitableRecordCreateRequest request)`
- Produces: `FeishuBitableCliService.queryRecords(BitableRecordQueryRequest request)`
- Produces: `FeishuBitableCliService.updateCell(BitableCellUpdateRequest request)`
- Produces: `FeishuDocumentCliService.createDocument(DocumentCreateRequest request)`
- Produces: `FeishuDocumentCliService.appendContent(DocumentAppendRequest request)`
- Produces: `FeishuDocumentCliService.getDocumentLink(String documentToken)`
- Produces: `FeishuCalendarCliService.createEvent(CalendarEventCreateRequest request)`
- Produces: `FeishuCalendarCliService.addAttendees(CalendarAttendeeAddRequest request)`
- Produces: `FeishuTaskCliService.createTask(TaskCreateRequest request)`
- Produces: `FeishuTaskCliService.assignOwner(TaskAssigneeRequest request)`

- [ ] **Step 1: Write the failing business service test**

```java
@Test
void bitableCreateRecordBuildsStructuredApiRequest() {
    CapturingTemplate template = new CapturingTemplate();
    FeishuBitableCliService service = new FeishuBitableCliService(template);

    service.createRecord(new BitableRecordCreateRequest("app_token", "table_id", Map.of("名称", "样件A")));

    assertThat(template.request.getBusinessType()).isEqualTo("bitable");
    assertThat(template.request.getMethod()).isEqualTo("POST");
    assertThat(template.request.getApiPath()).isEqualTo("/open-apis/bitable/v1/apps/app_token/tables/table_id/records");
    assertThat(template.request.getData()).containsKey("fields");
}

@Test
void taskAssignOwnerBuildsMemberRequest() {
    CapturingTemplate template = new CapturingTemplate();
    FeishuTaskCliService service = new FeishuTaskCliService(template);

    service.assignOwner(new TaskAssigneeRequest("task_guid", List.of("ou_user_1")));

    assertThat(template.request.getMethod()).isEqualTo("POST");
    assertThat(template.request.getApiPath()).isEqualTo("/open-apis/task/v2/tasks/task_guid/members");
    assertThat(template.request.getData()).containsKey("members");
}
```

- [ ] **Step 2: Run the business service test to verify it fails**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuCliBusinessServiceTest '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: compile failure because DTO and services do not exist.

- [ ] **Step 3: Implement DTOs and service methods**

Use immutable constructor DTOs with getters. Validate required string fields with `StringUtils.hasText`.

Service methods return `FeishuCliCommandResult`.

For `getDocumentLink(String documentToken)`, return a success result with JSON containing:

```json
{"url":"https://feishu.cn/docx/<documentToken>"}
```

when no CLI query is needed.

- [ ] **Step 4: Run the business service test to verify it passes**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuCliBusinessServiceTest '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: PASS.

---

### Task 4: 真实 `ProcessBuilder` CLI 执行器

**Files:**
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuCliProperties.java`
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/ProcessFeishuCliExecutor.java`
- Test: `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/ProcessFeishuCliExecutorTest.java`

**Interfaces:**
- Consumes: `FeishuCliCommandRequest`
- Produces: `FeishuCliProperties.Mode.NPX`
- Produces: `FeishuCliProperties.Mode.WRAPPER`
- Produces: `ProcessFeishuCliExecutor.execute(FeishuCliCommandRequest request)`

- [ ] **Step 1: Write the failing command construction test**

```java
@Test
void buildsNpxApiCommandWithParamsAndData() {
    FeishuCliProperties properties = new FeishuCliProperties();
    CapturingProcessRunner runner = new CapturingProcessRunner(0, "{\"code\":0}", "");
    ProcessFeishuCliExecutor executor = new ProcessFeishuCliExecutor(properties, runner);

    executor.execute(FeishuCliCommandRequest.api("bitable", "GET", "/open-apis/bitable/v1/apps/app/tables/table/records")
            .withParam("page_size", 20)
            .withData("fields", Map.of("名称", "样件A")));

    assertThat(runner.command).containsExactly(
            "npx", "@larksuite/cli@latest", "api", "GET",
            "/open-apis/bitable/v1/apps/app/tables/table/records",
            "--format", "json",
            "--params", "{\"page_size\":20}",
            "--data", "{\"fields\":{\"名称\":\"样件A\"}}"
    );
}

@Test
void buildsWrapperCommandWhenWrapperModeIsConfigured() {
    FeishuCliProperties properties = new FeishuCliProperties();
    properties.setMode(FeishuCliProperties.Mode.WRAPPER);
    properties.setWrapperPath("D:/tools/my-lark-cli.exe");
    CapturingProcessRunner runner = new CapturingProcessRunner(0, "{\"code\":0}", "");
    ProcessFeishuCliExecutor executor = new ProcessFeishuCliExecutor(properties, runner);

    executor.execute(FeishuCliCommandRequest.api("document", "POST", "/open-apis/docx/v1/documents")
            .withData("title", "项目纪要"));

    assertThat(runner.command).containsExactly(
            "D:/tools/my-lark-cli.exe", "api", "POST",
            "/open-apis/docx/v1/documents",
            "--format", "json",
            "--data", "{\"title\":\"项目纪要\"}"
    );
}
```

- [ ] **Step 2: Run the executor test to verify it fails**

Run: `mvn -pl modules/module-feishu/module-feishu-autoconfig -am -Dtest=ProcessFeishuCliExecutorTest '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: compile failure because `ProcessFeishuCliExecutor` does not exist.

- [ ] **Step 3: Implement properties, runner abstraction, and executor**

`FeishuCliProperties` defaults:

```java
public enum Mode { NPX, WRAPPER }

private boolean enabled = true;
private Mode mode = Mode.NPX;
private String command = "npx";
private String packageName = "@larksuite/cli@latest";
private String wrapperPath;
private long timeoutSeconds = 30;
private int retryTimes = 1;
private boolean logEnabled = true;
private String workingDirectory;
private Set<String> allowedBusinessTypes = Set.of("bitable", "document", "calendar", "task");
```

`ProcessFeishuCliExecutor` should expose a package-private `ProcessRunner` constructor for tests and a public constructor using the real runner.

Real runner uses `ProcessBuilder(command).directory(...)`, waits for timeout, captures stdout/stderr, and destroys the process on timeout.

Command prefix rules:

- `NPX`: `command`, `packageName`, `api`, `METHOD`, `PATH`
- `WRAPPER`: `wrapperPath`, `api`, `METHOD`, `PATH`
- `WRAPPER` with blank `wrapperPath`: return failed `FeishuCliCommandResult` and do not start a process.

- [ ] **Step 4: Run the executor test to verify it passes**

Run: `mvn -pl modules/module-feishu/module-feishu-autoconfig -am -Dtest=ProcessFeishuCliExecutorTest '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: PASS.

---

### Task 5: 自动装配和 Mapper 扫描整合

**Files:**
- Modify: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java`
- Modify: `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfigurationTest.java`
- Create: `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuCliAutoConfigurationTest.java`

**Interfaces:**
- Consumes: all classes from Tasks 1-4.
- Produces: Spring beans for `FeishuCliTemplate`, `FeishuCliExecutor`, four business Services, and `FeishuCliCallLogService`.
- Produces: `FeishuCliPolicy` from `feishu.cli.allowed-business-types`.

- [ ] **Step 1: Write the failing auto-configuration test**

```java
@Test
void registersCliBeansWhenEnabled() {
    runner.withPropertyValues(
            "feishu.cli.enabled=true",
            "feishu.agent.channel.auto-start=false"
    ).run(context -> {
        assertThat(context).hasSingleBean(FeishuCliProperties.class);
        assertThat(context).hasSingleBean(FeishuCliExecutor.class);
        assertThat(context).hasSingleBean(FeishuCliPolicy.class);
        assertThat(context).hasSingleBean(FeishuCliTemplate.class);
        assertThat(context).hasSingleBean(FeishuBitableCliService.class);
        assertThat(context).hasSingleBean(FeishuDocumentCliService.class);
        assertThat(context).hasSingleBean(FeishuCalendarCliService.class);
        assertThat(context).hasSingleBean(FeishuTaskCliService.class);
    });
}

@Test
void skipsCliBeansWhenDisabled() {
    runner.withPropertyValues("feishu.cli.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(FeishuCliTemplate.class));
}
```

- [ ] **Step 2: Run the auto-configuration test to verify it fails**

Run: `mvn -pl modules/module-feishu/module-feishu-autoconfig -am -Dtest=FeishuCliAutoConfigurationTest '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: compile failure because CLI beans are not registered.

- [ ] **Step 3: Wire CLI beans in `FeishuAutoConfiguration`**

Add `FeishuCliProperties.class` to `@EnableConfigurationProperties`.

Add conditional beans:

```java
@Bean
@ConditionalOnMissingBean
@ConditionalOnProperty(prefix = "feishu.cli", name = "enabled", havingValue = "true", matchIfMissing = true)
public FeishuCliExecutor feishuCliExecutor(FeishuCliProperties properties) {
    return new ProcessFeishuCliExecutor(properties);
}
```

Register `FeishuCliCallLogService`, `FeishuCliTemplate`, and four business services under the same property condition.

Add `com.zimo.module.feishu.cli` to `@MapperScan`.

When constructing `FeishuCliTemplate`, pass `FeishuCliPolicy.allowOnly(properties.getAllowedBusinessTypes())`.

- [ ] **Step 4: Run the auto-configuration test to verify it passes**

Run: `mvn -pl modules/module-feishu/module-feishu-autoconfig -am -Dtest=FeishuCliAutoConfigurationTest '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: PASS.

---

### Task 6: Demo 测试和模块级验证

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/cli/FeishuCliUsageDemoTest.java`

**Interfaces:**
- Consumes: four business services.
- Produces: test-only usage examples for all required business methods.

- [ ] **Step 1: Write demo tests for all business methods**

```java
@Test
void demonstratesBitableDocumentCalendarAndTaskCalls() {
    CapturingTemplate template = new CapturingTemplate();
    new FeishuBitableCliService(template).createRecord(
            new BitableRecordCreateRequest("app", "table", Map.of("名称", "样件A")));
    new FeishuDocumentCliService(template).createDocument(
            new DocumentCreateRequest("项目纪要", "folder_token"));
    new FeishuCalendarCliService(template).createEvent(
            new CalendarEventCreateRequest("primary", "评审会", "2026-07-03T09:00:00+08:00", "2026-07-03T10:00:00+08:00"));
    new FeishuTaskCliService(template).createTask(
            new TaskCreateRequest("跟进物料齐套", "请确认齐套状态"));

    assertThat(template.requests).hasSize(4);
}
```

- [ ] **Step 2: Run the demo test**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuCliUsageDemoTest '-Dsurefire.failIfNoSpecifiedTests=false' test`

Expected: PASS.

- [ ] **Step 3: Run full module verification**

Run:

```text
mvn -pl modules/module-feishu/module-feishu-core -am test
mvn -pl modules/module-feishu/module-feishu-autoconfig -am test
```

Expected: both commands end with `BUILD SUCCESS`.

---

## Self-Review

- Spec coverage: CLI 调用、DTO 入参、四类业务 Service、日志、重试、自动装配、测试示例均有任务覆盖。
- Placeholder scan: 本计划不包含待填项或未定义步骤。
- Type consistency: 后续任务依赖的核心类型均在 Task 1 和 Task 2 明确定义。
