# 飞书项目结果动态数据表 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 项目管理智能体返回项目数据时，在已有飞书多维表格应用中创建一张新数据表，并把本次结果写入新表。

**Architecture:** 在 `FeishuBitableCliService` 增加创建数据表能力；`FeishuProjectResultBitableWriter` 先创建表，再用返回的 `table_id` 写记录。失败时仍给飞书机器人回复项目结果和明确失败原因。

**Tech Stack:** Java 17、Spring Boot 3.4.5、Maven、JUnit 5、Mockito、飞书 Bitable OpenAPI。

## Global Constraints

- 不新增本地数据库，继续使用 MySQL。
- Controller/Service Bean 如需注入只使用单个 public 构造器。
- 不修改 `target/`、`dist/` 等生成产物。
- 配置中的密钥按敏感信息处理，不在文档或回复中复制真实凭据。

---

### Task 1: Bitable 创建数据表请求

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/bitable/BitableTableCreateRequest.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/cli/bitable/FeishuBitableCliService.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/cli/FeishuCliBusinessServiceTest.java`

**Interfaces:**
- Produces: `FeishuBitableCliService#createTable(BitableTableCreateRequest request)`

- [ ] Write failing test for `POST /open-apis/bitable/v1/apps/{appToken}/tables`.
- [ ] Implement `BitableTableCreateRequest`.
- [ ] Implement `createTable`.
- [ ] Run `mvn -pl modules/module-feishu/module-feishu-core -am "-Dtest=FeishuCliBusinessServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`.

### Task 2: 项目结果创建表后写入

**Files:**
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuProjectResultBitableWriter.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/agent/FeishuProjectResultBitableWriterTest.java`

**Interfaces:**
- Consumes: `FeishuBitableCliService#createTable(...)`
- Produces: `appendArchiveStatus(...)` 创建数据表并写入记录。

- [ ] Write failing tests for create-table-success, create-table-failure, and write-after-create-failure.
- [ ] Parse `table_id` from Feishu CLI JSON response.
- [ ] Create table name from project result type and current timestamp.
- [ ] Write record to the created table id.
- [ ] Run `mvn -pl modules/module-feishu/module-feishu-core -am "-Dtest=FeishuProjectResultBitableWriterTest,FeishuAiChannelMessageHandlerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`.

### Task 3: 回归验证

**Files:**
- Verify only.

- [ ] Run `mvn -pl modules/module-feishu/module-feishu-autoconfig -am "-Dtest=FeishuAiChannelAutoConfigurationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`.
- [ ] Run `mvn -pl admin-shell -am -DskipTests package`.
