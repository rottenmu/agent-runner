# 飞书平台接入模块 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `modules/module-feishu`，让主应用可以初始化飞书 SDK Client、发送文本消息，并接收飞书事件订阅中的机器人被 @ 消息事件。

**Architecture:** 按现有插件模块风格拆为 `module-feishu-core` 和 `module-feishu-autoconfig`。核心模块提供插件身份、消息服务、事件 Controller 和可测试的 SDK 适配接口；自动配置模块提供 `FeishuProperties`、`Client` Bean、服务 Bean 和 Spring Boot 自动装配入口。

**Tech Stack:** Java 17、Spring Boot 3.4.5、Maven、多模块插件架构、飞书官方 Java SDK `com.larksuite.oapi:oapi-sdk:2.7.3`、JUnit 5。

## Global Constraints

- 新模块目录必须是 `modules/module-feishu`。
- Markdown 文档默认使用中文。
- 不写入真实飞书密钥，配置使用环境变量占位。
- `application.yml` 必须包含 `feishu.app-id`、`feishu.app-secret`、`feishu.verification-token`、`feishu.encrypt-key`。
- Client 类型必须使用 `com.lark.oapi.Client`。
- 文本消息发送必须支持 `user_id`、`open_id`、`chat_id`。
- Webhook 必须支持 URL verification 和机器人被 @ 消息事件。

---

### Task 1: Maven 骨架与插件身份

**Files:**
- Modify: `pom.xml`
- Modify: `modules/pom.xml`
- Create: `modules/module-feishu/pom.xml`
- Create: `modules/module-feishu/module-feishu-core/pom.xml`
- Create: `modules/module-feishu/module-feishu-autoconfig/pom.xml`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/FeishuPluginRegisterTest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/FeishuPluginRegister.java`

**Interfaces:**
- Produces: `FeishuPluginRegister implements PluginRegister`

- [ ] **Step 1: Write the failing test**

```java
class FeishuPluginRegisterTest {
    @Test
    void exposesStablePluginMetadata() {
        FeishuPluginRegister register = new FeishuPluginRegister();

        assertThat(register.getPluginId()).isEqualTo("feishu");
        assertThat(register.getPluginName()).isEqualTo("飞书平台");
        assertThat(register.getApiPrefix()).isEqualTo("/api/feishu");
        assertThat(register.getFrontendRoute()).isEqualTo("/integration/feishu");
        assertThat(register.getFrontendModule()).isEqualTo("feishu");
        assertThat(register.getAgentName()).isEqualTo("feishu-agent");
        assertThat(register.getOrder()).isEqualTo(6);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am test`
Expected: FAIL because `FeishuPluginRegister` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create the Maven module files and `FeishuPluginRegister`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am test`
Expected: PASS.

### Task 2: 配置属性与自动配置

**Files:**
- Test: `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfigurationTest.java`
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuProperties.java`
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java`
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Interfaces:**
- Produces: `FeishuProperties`
- Produces: Spring Beans `Client`, `FeishuPluginRegister`

- [ ] **Step 1: Write the failing test**

Use `ApplicationContextRunner` to bind `feishu.app-id`、`feishu.app-secret`、`feishu.verification-token`、`feishu.encrypt-key` and assert that `FeishuProperties` and `Client` beans exist.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl modules/module-feishu/module-feishu-autoconfig -am test`
Expected: FAIL because auto configuration classes do not exist.

- [ ] **Step 3: Write minimal implementation**

Implement properties, client factory, plugin register bean, and Spring auto configuration import.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl modules/module-feishu/module-feishu-autoconfig -am test`
Expected: PASS.

### Task 3: 文本消息发送服务

**Files:**
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/message/FeishuMessageServiceTest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/message/FeishuReceiveIdType.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/message/FeishuTextMessageRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/message/FeishuMessageResponse.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/message/FeishuMessageClient.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/message/FeishuMessageService.java`
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/OfficialFeishuMessageClient.java`

**Interfaces:**
- Consumes: `Client`
- Produces: `FeishuMessageService#sendTextMessage(FeishuReceiveIdType, String, String)`

- [ ] **Step 1: Write the failing test**

Assert that sending text with `USER_ID`、`OPEN_ID`、`CHAT_ID` creates requests whose `receiveIdType` are `user_id`、`open_id`、`chat_id` and whose content is the supplied text.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am test`
Expected: FAIL because message service classes do not exist.

- [ ] **Step 3: Write minimal implementation**

Implement service validation, DTOs, enum conversion, and SDK adapter boundary.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am test`
Expected: PASS.

### Task 4: 事件订阅 Webhook

**Files:**
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/event/FeishuEventControllerTest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/event/FeishuEventController.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/event/FeishuEventProperties.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/event/FeishuBotMentionEvent.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/event/FeishuEventHandler.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/event/NoopFeishuEventHandler.java`

**Interfaces:**
- Consumes: verification token value
- Produces: `POST /api/feishu/events`

- [ ] **Step 1: Write the failing test**

Use Spring MVC test to assert URL verification returns challenge, invalid token returns 403, and `im.message.receive_v1` mention event is passed to `FeishuEventHandler`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am test`
Expected: FAIL because event classes do not exist.

- [ ] **Step 3: Write minimal implementation**

Implement JSON Map based parser for challenge and message receive events, plus handler interface.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am test`
Expected: PASS.

### Task 5: 主应用接入与最终验证

**Files:**
- Modify: `admin-shell/pom.xml`
- Modify: `admin-shell/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `module-feishu-autoconfig`
- Produces: main application loading feishu module

- [ ] **Step 1: Add admin-shell dependency and configuration**

Add `module-feishu-autoconfig` dependency. Add `feishu` configuration values using environment variable defaults.

- [ ] **Step 2: Run focused verification**

Run:

```powershell
mvn -pl modules/module-feishu/module-feishu-core -am test
mvn -pl modules/module-feishu/module-feishu-autoconfig -am test
mvn -pl admin-shell -am package
```

Expected: all pass.
