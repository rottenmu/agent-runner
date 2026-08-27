# 智能体绑定飞书渠道 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `module-ai` 提供飞书配置到智能体的一对一绑定页面，并让飞书消息优先路由到该配置绑定的启用智能体。

**Architecture:** `ps_feishu_config.agent_id` 保存不透明的智能体 ID，飞书模块负责绑定接口和把 ID 写入 `AiChannelMessage.attributes`，但不依赖 `module-ai`。starter 将解析契约扩展为面向完整消息且保持原函数式接口兼容，`module-ai` 负责校验绑定智能体和执行渠道默认智能体降级。

**Tech Stack:** Java 17、Spring Boot 3.4.5、MyBatis-Plus、JUnit 5、Mockito、Vue 3、Element Plus、Node.js test runner、MySQL。

## Global Constraints

- 页面放在 `frontend/modules/ai`，路由固定为 `/ai/feishu-bindings`。
- 每条飞书配置最多绑定一个智能体，一个智能体可绑定多条飞书配置。
- `module-feishu` 与 `module-ai` 不增加直接 Maven 依赖。
- `module-feishu` 不校验智能体是否存在或启用，`module-ai` 运行时解析器负责最终校验。
- 未绑定或绑定智能体不可用时，回退到 `defaultChannels` 包含 `feishu` 的启用智能体。
- 不建立数据库外键。
- 不增加或修改数据库初始化器来创建 `agent_id`；部署前手工执行 MySQL `ALTER TABLE`。
- 后端 Java 注释遵守 `docs/rules/BACKEND_JAVA_COMMENT_RULES.md`。
- 后端与通用代码行数遵守 `docs/rules/CODE_SIZE_RULES.md`。
- Controller 和 Service Bean 使用唯一的普通 `public` 构造器注入。
- 不编辑 `target/`、`dist/` 等生成产物。

---

## 文件结构

### 新增文件

- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuAgentBindingRequest.java`：绑定请求 DTO。
- `frontend/modules/ai/src/api/feishu-binding.js`：飞书配置列表和绑定接口。
- `frontend/modules/ai/src/views/AiFeishuBindingManage.vue`：飞书渠道绑定页面。
- `frontend/modules/ai/tests/feishu-binding-static.test.mjs`：页面、菜单、路由和 API 静态契约测试。

### 修改文件

- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigEntity.java`：持久化 `agentId`。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigResponse.java`：返回 `agentId`。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigService.java`：声明绑定方法。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigServiceImpl.java`：更新绑定并映射响应。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigController.java`：暴露绑定接口。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigProvider.java`：提供当前启用配置的绑定智能体 ID。
- `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/RuntimeFeishuConfigProvider.java`：从数据库活动配置读取绑定。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAiChannelMessageHandler.java`：写入 `agentId` 消息属性。
- `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java`：向消息处理器注入配置提供者。
- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/agent/AiAgentProfileResolver.java`：增加面向消息的默认解析方法。
- `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/channel/AiChannelHandler.java`：使用完整消息解析智能体。
- `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/management/AiAgentManagementService.java`：按 ID 查找启用智能体。
- `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/management/AiManagedAgentProfileResolver.java`：实现绑定优先、渠道默认降级。
- `frontend/modules/ai/routes.js`：注册页面路由。
- `frontend/modules/ai/menus.js`：注册页面菜单。

### 测试文件

- `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigServiceImplTest.java`
- `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigControllerTest.java`
- `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/agent/FeishuAiChannelMessageHandlerTest.java`
- `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAiChannelAutoConfigurationTest.java`
- `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/channel/AiChannelHandlerTest.java`
- `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/management/AiAgentManagementServiceTest.java`
- `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/management/AiManagedAgentProfileResolverTest.java`

---

### Task 1: 飞书配置绑定持久化与管理接口

**Files:**

- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuAgentBindingRequest.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigEntity.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigResponse.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigService.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigServiceImpl.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigController.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigServiceImplTest.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigControllerTest.java`

**Interfaces:**

- Consumes: `FeishuConfigMapper.selectById(Long)` 和 `FeishuConfigMapper.updateById(FeishuConfigEntity)`。
- Produces: `FeishuConfigService.bindAgent(Long, String): FeishuConfigResponse`。
- Produces: `PUT /api/biz/feishu/config/{id}/agent-binding`，请求体 `{"agentId":"a123"}`。
- Produces: 所有 `FeishuConfigResponse` 增加可空字段 `agentId`。

- [ ] **Step 1: 编写 Service 失败测试**

在 `FeishuConfigServiceImplTest` 增加保存、替换和解除绑定测试：

```java
@Test
void bindsAndUnbindsAgentWithoutChangingOtherConfigFields() {
    FeishuConfigEntity entity = entity(10L, "生产飞书", "cli_app");
    entity.setAgentId("a-old");
    when(mapper.selectById(10L)).thenReturn(entity);
    when(mapper.updateById(entity)).thenReturn(1);

    FeishuConfigResponse bound = service.bindAgent(10L, "  a-new  ");
    assertThat(bound.getAgentId()).isEqualTo("a-new");
    assertThat(entity.getConfigName()).isEqualTo("生产飞书");
    assertThat(entity.getAppId()).isEqualTo("cli_app");

    FeishuConfigResponse unbound = service.bindAgent(10L, " ");
    assertThat(unbound.getAgentId()).isNull();
    verify(mapper, times(2)).updateById(entity);
}
```

同时在现有更新测试中先设置 `entity.setAgentId("a-bound")`，调用 `service.update(...)` 后断言：

```java
assertThat(entity.getAgentId()).isEqualTo("a-bound");
```

- [ ] **Step 2: 运行 Service 测试并确认失败**

Run:

```text
mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuConfigServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，提示 `setAgentId` 或 `bindAgent` 不存在。

- [ ] **Step 3: 编写 Controller 失败测试**

在 `FeishuConfigControllerTest` 的模拟 Service 上增加：

```java
FeishuConfigResponse response = new FeishuConfigResponse();
response.setId(10L);
response.setAgentId("a-new");
when(service.bindAgent(10L, "a-new")).thenReturn(response);

mockMvc.perform(put("/api/biz/feishu/config/10/agent-binding")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
                {"agentId":"a-new"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.agentId").value("a-new"));
```

- [ ] **Step 4: 运行 Controller 测试并确认失败**

Run:

```text
mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuConfigControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，接口返回 404 或 `bindAgent` 尚不存在。

- [ ] **Step 5: 实现实体、响应和请求 DTO**

在 `FeishuConfigEntity` 和 `FeishuConfigResponse` 中分别加入字段及 getter/setter：

```java
private String agentId;

public String getAgentId() {
    return agentId;
}

public void setAgentId(String agentId) {
    this.agentId = agentId;
}
```

创建 `FeishuAgentBindingRequest`：

```java
package com.zimo.module.zimo.config;

/**
 * 飞书配置绑定智能体请求。
 *
 * @author Codex
 * @since 2026-07-23
 */
public class FeishuAgentBindingRequest {
    private String agentId;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }
}
```

- [ ] **Step 6: 实现 Service 绑定方法**

在 `FeishuConfigService` 增加：

```java
FeishuConfigResponse bindAgent(Long id, String agentId);
```

在 `FeishuConfigServiceImpl` 增加：

```java
@Override
public FeishuConfigResponse bindAgent(Long id, String agentId) {
    FeishuConfigEntity existing = requireExisting(id);
    existing.setAgentId(StringUtils.hasText(agentId) ? agentId.trim() : null);
    mapper.updateById(existing);
    return toMaskedResponse(existing);
}
```

并在 `toMaskedResponse` 增加：

```java
response.setAgentId(entity.getAgentId());
```

普通 `create` 和 `update` 不读取请求中的 `agentId`，确保编辑飞书凭据不会修改绑定。

- [ ] **Step 7: 实现 Controller 绑定接口**

在 `FeishuConfigController` 增加：

```java
@PutMapping("/{id}/agent-binding")
public R<FeishuConfigResponse> bindAgent(
        @PathVariable Long id,
        @RequestBody FeishuAgentBindingRequest request) {
    return R.ok(configService.bindAgent(id, request.getAgentId()));
}
```

- [ ] **Step 8: 运行测试并确认通过**

Run:

```text
mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuConfigServiceImplTest,FeishuConfigControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 两个测试类全部 PASS。

- [ ] **Step 9: 提交**

```text
git add modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuAgentBindingRequest.java modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigEntity.java modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigResponse.java modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigService.java modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigServiceImpl.java modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigController.java modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigServiceImplTest.java modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuConfigControllerTest.java
git commit -m "feat(feishu): 增加智能体绑定接口"
```

---

### Task 2: starter 支持按完整渠道消息解析智能体

**Files:**

- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/agent/AiAgentProfileResolver.java`
- Modify: `modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/channel/AiChannelHandler.java`
- Test: `modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/channel/AiChannelHandlerTest.java`

**Interfaces:**

- Consumes: `AiChannelMessage`。
- Produces: `AiAgentProfileResolver.resolveForMessage(AiChannelMessage)` 默认方法。
- Compatibility: `resolveDefaultForChannel(String)` 仍为唯一抽象方法，原有 lambda 和实现保持可编译。

- [ ] **Step 1: 编写消息上下文解析失败测试**

在 `AiChannelHandlerTest` 增加：

```java
@Test
void resolvesAgentWithCompleteChannelMessage() {
    AtomicReference<AiChannelMessage> resolvedMessage = new AtomicReference<>();
    AiAgentProfileResolver resolver = new AiAgentProfileResolver() {
        @Override
        public Optional<AiAgentProfile> resolveDefaultForChannel(String channel) {
            return Optional.empty();
        }

        @Override
        public Optional<AiAgentProfile> resolveForMessage(AiChannelMessage message) {
            resolvedMessage.set(message);
            return Optional.of(new AiAgentProfile(
                    "a-bound", "绑定智能体", "qwen-plus", "persona", List.of()));
        }
    };
    AiChannelHandler handler = new AiChannelHandler(agentService("ok"), new AiSkillRegistry(List.of()), resolver);
    AiChannelMessage message = AiChannelMessage.of(
            "feishu", "tenant_1", "user_1", "chat_1", "msg_1", "hello",
            Map.of("agentId", "a-bound"));

    handler.handle(message);

    assertThat(resolvedMessage).hasValue(message);
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```text
mvn -pl modules/ai-agent-spring-boot-starter -am -Dtest=AiChannelHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，提示 `resolveForMessage` 不能重写或断言未捕获完整消息。

- [ ] **Step 3: 扩展解析接口且保持函数式兼容**

在 `AiAgentProfileResolver` 导入 `AiChannelMessage` 并增加默认方法：

```java
default Optional<AiAgentProfile> resolveForMessage(AiChannelMessage message) {
    if (message == null) {
        return Optional.empty();
    }
    return resolveDefaultForChannel(message.channel());
}
```

保留：

```java
@FunctionalInterface
public interface AiAgentProfileResolver {
    Optional<AiAgentProfile> resolveDefaultForChannel(String channel);
}
```

- [ ] **Step 4: 让渠道处理器传递完整消息**

将 `AiChannelHandler.defaultAgent` 中的解析调用替换为：

```java
return profileResolver.resolveForMessage(message).orElse(null);
```

- [ ] **Step 5: 运行 starter 测试**

Run:

```text
mvn -pl modules/ai-agent-spring-boot-starter -am -Dtest=AiChannelHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS，原有按渠道 lambda 测试和新增完整消息测试均通过。

- [ ] **Step 6: 提交**

```text
git add modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/agent/AiAgentProfileResolver.java modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/channel/AiChannelHandler.java modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/channel/AiChannelHandlerTest.java
git commit -m "feat(ai): 支持按渠道消息解析智能体"
```

---

### Task 3: module-ai 实现绑定优先与渠道默认降级

**Files:**

- Modify: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/management/AiAgentManagementService.java`
- Modify: `modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/management/AiManagedAgentProfileResolver.java`
- Test: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/management/AiAgentManagementServiceTest.java`
- Test: `modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/management/AiManagedAgentProfileResolverTest.java`

**Interfaces:**

- Consumes: `AiChannelMessage.attributes().get("agentId")`。
- Produces: `AiAgentManagementService.findEnabledAgentById(String): Optional<AiManagedAgent>`。
- Produces: `AiManagedAgentProfileResolver.resolveForMessage(AiChannelMessage)`。

- [ ] **Step 1: 编写按 ID 查找启用智能体失败测试**

在 `AiAgentManagementServiceTest` 增加：

```java
@Test
void findsOnlyEnabledAgentById() {
    AiAgentManagementService service = serviceWithAgents(
            agent("a-enabled", "启用智能体", true, List.of("feishu")),
            agent("a-disabled", "停用智能体", false, List.of()));

    assertThat(service.findEnabledAgentById(" a-enabled "))
            .map(AiManagedAgent::id)
            .contains("a-enabled");
    assertThat(service.findEnabledAgentById("a-disabled")).isEmpty();
    assertThat(service.findEnabledAgentById(" ")).isEmpty();
}
```

测试 helper 使用现有构造器和测试仓储构造两条智能体，不能新增生产代码测试专用构造器。

- [ ] **Step 2: 编写解析顺序失败测试**

在 `AiManagedAgentProfileResolverTest` 增加三个测试：

```java
@Test
void prefersBoundFeishuAgent() {
    AiChannelMessage message = AiChannelMessage.of(
            "feishu", "tenant", "user", "chat", "msg", "hello",
            Map.of("agentId", "a-bound"));

    assertThat(resolver.resolveForMessage(message))
            .map(AiAgentProfile::id)
            .contains("a-bound");
}

@Test
void fallsBackToChannelDefaultWhenBoundAgentIsDisabled() {
    AiChannelMessage message = AiChannelMessage.of(
            "feishu", "tenant", "user", "chat", "msg", "hello",
            Map.of("agentId", "a-disabled"));

    assertThat(resolver.resolveForMessage(message))
            .map(AiAgentProfile::id)
            .contains("a-default");
}

@Test
void ignoresAgentIdForNonFeishuChannel() {
    AiChannelMessage message = AiChannelMessage.of(
            "web", "tenant", "user", "chat", "msg", "hello",
            Map.of("agentId", "a-bound"));

    assertThat(resolver.resolveForMessage(message))
            .map(AiAgentProfile::id)
            .contains("a-web");
}
```

- [ ] **Step 3: 运行测试并确认失败**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -am -Dtest=AiAgentManagementServiceTest,AiManagedAgentProfileResolverTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，提示 `findEnabledAgentById` 或 `resolveForMessage` 不存在。

- [ ] **Step 4: 实现启用智能体查询**

在 `AiAgentManagementService` 增加：

```java
public synchronized Optional<AiManagedAgent> findEnabledAgentById(String id) {
    if (!StringUtils.hasText(id)) {
        return Optional.empty();
    }
    AiManagedAgent agent = agents.get(id.trim());
    return agent != null && agent.enabled() ? Optional.of(agent) : Optional.empty();
}
```

- [ ] **Step 5: 实现消息级解析**

在 `AiManagedAgentProfileResolver` 导入 `AiChannelMessage` 和 `StringUtils`，增加：

```java
@Override
public Optional<AiAgentProfile> resolveForMessage(AiChannelMessage message) {
    if (message == null) {
        return Optional.empty();
    }
    if ("feishu".equalsIgnoreCase(message.channel())) {
        String agentId = textAttribute(message, "agentId");
        Optional<AiManagedAgent> boundAgent = managementService.findEnabledAgentById(agentId);
        if (boundAgent.isPresent()) {
            return boundAgent.map(this::toProfile);
        }
    }
    return resolveDefaultForChannel(message.channel());
}

private String textAttribute(AiChannelMessage message, String key) {
    Object value = message.attributes().get(key);
    return value == null ? null : String.valueOf(value).trim();
}
```

不因无效绑定抛出异常，确保进入渠道默认智能体降级。

- [ ] **Step 6: 运行 module-ai 测试**

Run:

```text
mvn -pl modules/module-ai/module-ai-core -am -Dtest=AiAgentManagementServiceTest,AiManagedAgentProfileResolverTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS。

- [ ] **Step 7: 提交**

```text
git add modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/management/AiAgentManagementService.java modules/module-ai/module-ai-core/src/main/java/com/xingju/module/ai/management/AiManagedAgentProfileResolver.java modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/management/AiAgentManagementServiceTest.java modules/module-ai/module-ai-core/src/test/java/com/xingju/module/ai/management/AiManagedAgentProfileResolverTest.java
git commit -m "feat(ai): 按飞书绑定解析智能体"
```

---

### Task 4: 飞书消息携带当前配置绑定的智能体 ID

**Files:**

- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigProvider.java`
- Modify: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/RuntimeFeishuConfigProvider.java`
- Modify: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAiChannelMessageHandler.java`
- Modify: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/agent/FeishuAiChannelMessageHandlerTest.java`
- Test: `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAiChannelAutoConfigurationTest.java`

**Interfaces:**

- Consumes: `FeishuConfigService.getActiveConfigSummary()`。
- Produces: `FeishuConfigProvider.getActiveAgentId(): String`，默认返回 `null`。
- Produces: 飞书 `AiChannelMessage.attributes()` 中的可选 `agentId`。

- [ ] **Step 1: 编写消息属性失败测试**

在 `FeishuAiChannelMessageHandlerTest` 增加：

```java
@Test
void addsActiveConfigAgentIdToAiMessageAttributes() {
    AtomicReference<AiChannelMessage> capturedMessage = new AtomicReference<>();
    AiChannelHandler aiChannelHandler = mock(AiChannelHandler.class);
    when(aiChannelHandler.handle(any())).thenAnswer(invocation -> {
        capturedMessage.set(invocation.getArgument(0));
        return AiChannelReply.text("ok");
    });
    FeishuConfigProvider configProvider = new FeishuConfigProvider() {
        @Override
        public FeishuRuntimeConfig getActiveConfig() {
            return null;
        }

        @Override
        public String getActiveAgentId() {
            return "a-bound";
        }
    };
    FeishuAiChannelMessageHandler handler = new FeishuAiChannelMessageHandler(
            aiChannelHandler, new FeishuProjectCardRenderer(), null, configProvider);

    handler.handle(commandMessage("hello"), new FeishuAgentReplyService(new RecordingReplyClient()));

    assertThat(capturedMessage.get().attributes()).containsEntry("agentId", "a-bound");
}
```

再增加未绑定测试：

```java
assertThat(capturedMessage.get().attributes()).doesNotContainKey("agentId");
```

- [ ] **Step 2: 运行处理器测试并确认失败**

Run:

```text
mvn -pl modules/module-feishu/module-feishu-core -am -Dtest=FeishuAiChannelMessageHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，四参数构造器和 `getActiveAgentId` 不存在。

- [ ] **Step 3: 扩展飞书配置提供者**

在 `FeishuConfigProvider` 增加兼容默认方法：

```java
default String getActiveAgentId() {
    return null;
}
```

在 `RuntimeFeishuConfigProvider` 增加：

```java
@Override
public String getActiveAgentId() {
    FeishuConfigService configService = configServiceProvider.getIfAvailable();
    if (configService == null) {
        return null;
    }
    FeishuConfigResponse active = configService.getActiveConfigSummary();
    return active == null ? null : active.getAgentId();
}
```

- [ ] **Step 4: 将绑定写入消息属性**

在 `FeishuAiChannelMessageHandler` 保存 `FeishuConfigProvider` 字段。现有一参数和两参数构造器继续传入 `null`，完整构造器改为：

```java
public FeishuAiChannelMessageHandler(
        AiChannelHandler aiChannelHandler,
        FeishuProjectCardRenderer projectCardRenderer,
        FeishuFileClient fileClient,
        FeishuConfigProvider configProvider) {
    this.aiChannelHandler = Objects.requireNonNull(aiChannelHandler, "aiChannelHandler must not be null");
    this.projectCardRenderer = Objects.requireNonNull(projectCardRenderer, "projectCardRenderer must not be null");
    this.fileClient = fileClient;
    this.configProvider = configProvider;
}
```

在 `attributes` 方法创建副本后加入：

```java
String agentId = configProvider == null ? null : configProvider.getActiveAgentId();
if (hasText(agentId)) {
    attributes.put("agentId", agentId.trim());
}
```

文件下载逻辑继续在同一属性 Map 上追加内容。

- [ ] **Step 5: 更新自动配置并补充 Bean 测试**

将 `FeishuAutoConfiguration.feishuAiChannelMessageHandler` 参数和构造调用改为：

```java
public FeishuAgentMessageHandler feishuAiChannelMessageHandler(
        AiChannelHandler aiChannelHandler,
        FeishuProjectCardRenderer projectCardRenderer,
        FeishuFileClient feishuFileClient,
        FeishuConfigProvider feishuConfigProvider) {
    return new FeishuAiChannelMessageHandler(
            aiChannelHandler,
            projectCardRenderer,
            feishuFileClient,
            feishuConfigProvider);
}
```

在 `FeishuAiChannelAutoConfigurationTest` 保持现有 Bean 创建断言，并确保测试上下文提供 `FeishuConfigProvider`；预期仍只有一个 `FeishuAgentMessageHandler`。

- [ ] **Step 6: 运行飞书 core 与 autoconfig 测试**

Run:

```text
mvn -pl modules/module-feishu/module-feishu-autoconfig -am -Dtest=FeishuAiChannelMessageHandlerTest,FeishuAiChannelAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS。

- [ ] **Step 7: 提交**

```text
git add modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuConfigProvider.java modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/RuntimeFeishuConfigProvider.java modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAiChannelMessageHandler.java modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/agent/FeishuAiChannelMessageHandlerTest.java modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAiChannelAutoConfigurationTest.java
git commit -m "feat(feishu): 传递配置绑定的智能体"
```

---

### Task 5: 增加飞书渠道绑定页面

**Files:**

- Create: `frontend/modules/ai/src/api/feishu-binding.js`
- Create: `frontend/modules/ai/src/views/AiFeishuBindingManage.vue`
- Create: `frontend/modules/ai/tests/feishu-binding-static.test.mjs`
- Modify: `frontend/modules/ai/routes.js`
- Modify: `frontend/modules/ai/menus.js`

**Interfaces:**

- Consumes: `GET /biz/feishu/config/page`、`GET /biz/ai/agents`。
- Produces: `bindFeishuAgent(configId, agentId)` 调用 `PUT /biz/feishu/config/{id}/agent-binding`。
- Produces: `/ai/feishu-bindings` 管理页面。

- [ ] **Step 1: 编写前端静态失败测试**

创建 `frontend/modules/ai/tests/feishu-binding-static.test.mjs`：

```javascript
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const read = (...parts) => {
  const file = join(root, ...parts)
  assert.ok(existsSync(file), `文件应存在：${parts.join('/')}`)
  return readFileSync(file, 'utf8')
}

const menus = read('menus.js')
const routes = read('routes.js')
const api = read('src', 'api', 'feishu-binding.js')
const page = read('src', 'views', 'AiFeishuBindingManage.vue')

assert.match(menus, /\/ai\/feishu-bindings/)
assert.match(menus, /飞书渠道绑定/)
assert.match(routes, /AiFeishuBindingManage/)
assert.match(routes, /AiFeishuBindingManage\.vue/)
assert.match(api, /\/biz\/feishu\/config\/page/)
assert.match(api, /\/biz\/feishu\/config\/\$\{configId\}\/agent-binding/)
assert.match(page, /listAgents/)
assert.match(page, /item\.enabled/)
assert.match(page, /agentId/)
assert.match(page, /绑定已失效/)
assert.match(page, /ElMessageBox\.confirm/)
assert.doesNotMatch(page, /appSecret|verificationToken|encryptKey/)
```

- [ ] **Step 2: 运行静态测试并确认失败**

Run:

```text
node --test frontend/modules/ai/tests/feishu-binding-static.test.mjs
```

Expected: FAIL，提示 API 或页面文件不存在。

- [ ] **Step 3: 实现 API 封装**

创建 `frontend/modules/ai/src/api/feishu-binding.js`：

```javascript
import request from '../../../../web-shell/src/api/request'

export function listFeishuConfigs(params) {
  return request.get('/biz/feishu/config/page', { params })
}

export function bindFeishuAgent(configId, agentId) {
  return request.put(`/biz/feishu/config/${configId}/agent-binding`, {
    agentId: agentId || null
  })
}
```

- [ ] **Step 4: 注册菜单和路由**

在 `frontend/modules/ai/routes.js` 增加：

```javascript
{
  path: '/ai/feishu-bindings',
  name: 'AiFeishuBindingManage',
  component: () => import('./src/views/AiFeishuBindingManage.vue')
}
```

在 `frontend/modules/ai/menus.js` 增加：

```javascript
{ path: '/ai/feishu-bindings', title: '飞书渠道绑定' }
```

菜单顺序放在“智能体管理”之后、“模型配置管理”之前。

- [ ] **Step 5: 实现页面数据和操作**

创建 `AiFeishuBindingManage.vue`，页面脚本的核心状态和行为如下：

```vue
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAgents } from '../api/agent'
import { bindFeishuAgent, listFeishuConfigs } from '../api/feishu-binding'

const loading = ref(false)
const savingId = ref(null)
const configs = ref([])
const agents = ref([])
const query = reactive({ configName: '', appId: '' })

const enabledAgents = computed(() => agents.value.filter(item => item.enabled))

function unwrap(data) {
  return data && typeof data === 'object' && 'data' in data ? data.data : data
}

function records(data) {
  const value = unwrap(data)
  return Array.isArray(value?.records) ? value.records : []
}

function agentName(agentId) {
  if (!agentId) return '未绑定'
  return agents.value.find(item => item.id === agentId)?.name || '绑定已失效'
}

function isInvalidBinding(row) {
  return Boolean(row.agentId) && !agents.value.some(item => item.id === row.agentId && item.enabled)
}

async function loadData() {
  loading.value = true
  try {
    const [configResult, agentResult] = await Promise.all([
      listFeishuConfigs({
        current: 1,
        size: 100,
        configName: query.configName || undefined,
        appId: query.appId || undefined
      }),
      listAgents()
    ])
    configs.value = records(configResult)
    const agentData = unwrap(agentResult)
    agents.value = Array.isArray(agentData) ? agentData : []
  } catch (error) {
    ElMessage.error(error?.message || '加载飞书绑定数据失败')
  } finally {
    loading.value = false
  }
}

async function saveBinding(row, agentId) {
  savingId.value = row.id
  try {
    const result = unwrap(await bindFeishuAgent(row.id, agentId))
    configs.value = configs.value.map(item => item.id === row.id ? result : item)
    ElMessage.success('绑定已保存')
  } catch (error) {
    ElMessage.error(error?.message || '保存绑定失败')
  } finally {
    savingId.value = null
  }
}

async function unbind(row) {
  await ElMessageBox.confirm(`确认解除「${row.configName}」的智能体绑定？`, '解除绑定', {
    type: 'warning'
  })
  await saveBinding(row, null)
}

onMounted(loadData)
</script>
```

模板必须包含：

```vue
<el-form inline>
  <el-form-item label="配置名称">
    <el-input v-model="query.configName" clearable placeholder="输入配置名称" />
  </el-form-item>
  <el-form-item label="App ID">
    <el-input v-model="query.appId" clearable placeholder="输入 App ID" />
  </el-form-item>
  <el-form-item>
    <el-button type="primary" @click="loadData">查询</el-button>
  </el-form-item>
</el-form>
<el-table :data="configs" v-loading="loading">
  <el-table-column prop="configName" label="配置名称" min-width="160" />
  <el-table-column prop="appId" label="App ID" min-width="180" />
  <el-table-column prop="tenantName" label="租户" min-width="140" />
  <el-table-column label="配置状态" width="110">
    <template #default="{ row }">
      <el-tag :type="row.enabled === 1 ? 'success' : 'info'">
        {{ row.enabled === 1 ? '当前启用' : '未启用' }}
      </el-tag>
    </template>
  </el-table-column>
  <el-table-column label="当前智能体" min-width="180">
    <template #default="{ row }">
      <el-tag :type="isInvalidBinding(row) ? 'danger' : row.agentId ? 'success' : 'info'">
        {{ agentName(row.agentId) }}
      </el-tag>
    </template>
  </el-table-column>
  <el-table-column label="绑定操作" min-width="280">
    <template #default="{ row }">
      <el-select
        :model-value="row.agentId"
        filterable
        clearable
        placeholder="选择启用的智能体"
        :loading="savingId === row.id"
        @change="agentId => saveBinding(row, agentId)"
      >
        <el-option
          v-for="item in enabledAgents"
          :key="item.id"
          :label="item.name"
          :value="item.id"
        />
      </el-select>
      <el-button :disabled="!row.agentId" @click="unbind(row)">解除绑定</el-button>
    </template>
  </el-table-column>
</el-table>
```

页面只引用配置摘要字段，不出现 `appSecret`、`verificationToken` 或 `encryptKey`。

- [ ] **Step 6: 运行 AI 前端测试**

Run:

```text
node --test frontend/modules/ai/tests/*.test.mjs
```

Expected: 全部 PASS。

- [ ] **Step 7: 构建前端主壳**

Run（工作目录 `frontend/web-shell`）：

```text
npm run build
```

Expected: Vite 构建成功并以退出码 0 结束。

- [ ] **Step 8: 提交**

```text
git add frontend/modules/ai/src/api/feishu-binding.js frontend/modules/ai/src/views/AiFeishuBindingManage.vue frontend/modules/ai/tests/feishu-binding-static.test.mjs frontend/modules/ai/routes.js frontend/modules/ai/menus.js
git commit -m "feat(ai): 增加飞书渠道绑定页面"
```

---

### Task 6: 集成验证与数据库脚本交付

**Files:**

- Verify only: `modules/ai-agent-spring-boot-starter`
- Verify only: `modules/module-ai`
- Verify only: `modules/module-feishu`
- Verify only: `frontend/modules/ai`
- Verify only: `frontend/web-shell`

**Interfaces:**

- Consumes: Tasks 1-5 的全部接口。
- Produces: 可部署构建和需要人工执行的 MySQL 变更 SQL。

- [ ] **Step 1: 检查没有自动建列逻辑**

Run:

```text
rg -n "agent_id|agentId" modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuSchemaInitializer.java modules/module-ai/module-ai-autoconfig/src/main/java
```

Expected: `FeishuSchemaInitializer.java` 不包含 `agent_id`，AI 自动配置中不存在本功能的数据库初始化器。

- [ ] **Step 2: 运行相关后端测试**

Run:

```text
mvn -pl modules/module-feishu/module-feishu-autoconfig,modules/ai-agent-spring-boot-starter,modules/module-ai/module-ai-autoconfig -am test
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: 运行前端测试**

Run:

```text
node --test frontend/modules/ai/tests/*.test.mjs
```

Expected: 全部 PASS。

- [ ] **Step 4: 构建主应用**

Run:

```text
mvn -pl admin-shell -am -DskipTests package
```

Expected: BUILD SUCCESS。

- [ ] **Step 5: 构建前端**

Run（工作目录 `frontend/web-shell`）：

```text
npm run build
```

Expected: 构建成功并以退出码 0 结束。

- [ ] **Step 6: 向用户交付人工执行 SQL**

交付以下 SQL，不自动连接或修改数据库：

```sql
ALTER TABLE ps_feishu_config
    ADD COLUMN agent_id VARCHAR(64) NULL COMMENT '绑定的智能体ID' AFTER enabled;
```

同时说明执行顺序：先备份数据库并执行 SQL，再部署新版后端，最后进入“AI → 飞书渠道绑定”完成配置。

- [ ] **Step 7: 检查最终改动范围**

Run:

```text
git status --short
git diff --check
```

Expected: 不包含 `target/`、`dist/`、密钥或与本功能无关的新改动；既有用户改动保持不变。

---

## 自检结果

- 设计文档中的数据库字段、接口、运行时解析、降级、前端页面、模块边界和测试要求均有对应任务。
- `AiAgentProfileResolver` 的抽象方法保持不变，类型兼容；新增方法使用 `AiChannelMessage`。
- 飞书绑定字段统一命名为数据库 `agent_id`、Java/JSON `agentId`、消息属性 `agentId`。
- 飞书模块只传递不透明 ID，智能体有效性由 `module-ai` 检查。
- 计划未包含占位任务，不修改或新增数据库初始化器。
