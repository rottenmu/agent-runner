# 飞书 Agent 统一消息调度网关 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `module-feishu` 中实现统一消息调度网关，把飞书 Channel 消息、指令路由、业务处理、多维表格归档和卡片回复串成一条可扩展链路。

**Architecture:** 网关作为默认 `FeishuAgentMessageHandler` 接入现有 Channel 监听器。核心层新增 `gateway` 包，负责路由、限流、业务结果模型、卡片组装和调度编排；自动装配层新增 `FeishuAgentGatewayProperties` 并在 `FeishuAutoConfiguration` 中按配置注册 Bean。

**Tech Stack:** Java 17、Spring Boot 3.4.5、JUnit 5、AssertJ、Mockito、Maven 多模块、飞书 Channel SDK、飞书 CLI 封装层。

## Global Constraints

- 所有新增 `.md` 文档使用中文。
- 不编辑 `target/`、`dist/` 等生成产物。
- 不把凭据复制到文档、日志、提交信息或聊天回复中。
- 保持 `module-feishu-core + module-feishu-autoconfig` 的插件式结构。
- 网关不硬编码项目管理、WMS 等具体业务逻辑，业务查询通过 `FeishuAgentBusinessHandler` 扩展。
- 不引入 MQ、Redis 或分布式限流组件。
- 不改变已完成的凭据层、Channel SDK 层、CLI 执行层对外契约。

---

## File Structure

- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentBusinessRequest.java`
  - 保存从飞书消息转换出的业务处理请求。
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentBusinessResult.java`
  - 保存业务结果、卡片字段、归档字段和跳转链接。
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentCommandRoute.java`
  - 描述一条指令路由的名称、前缀、关键词和优先级。
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentBusinessHandler.java`
  - 业务处理器扩展接口。
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentCommandRouter.java`
  - 根据文本匹配最合适的业务处理器。
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentRateLimiter.java`
  - 本地滑动窗口限流。
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentResultCardFactory.java`
  - 统一生成业务结果卡片 JSON。
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentDispatchService.java`
  - 编排校验、凭据检查、限流、路由、业务处理、归档、回复。
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentGatewayMessageHandler.java`
  - 实现 `FeishuAgentMessageHandler`，把 Channel 消息交给调度服务。
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentGatewayException.java`
  - 网关内部受控异常。
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAgentGatewayProperties.java`
  - `feishu.agent.gateway` 配置属性。
- Modify: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java`
  - 启用属性类并注册网关 Bean。
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/gateway/*Test.java`
  - 覆盖路由、限流、卡片、调度和消息处理器。
- Test: `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAgentGatewayAutoConfigurationTest.java`
  - 覆盖自动装配条件。

---

### Task 1: 网关模型与指令路由

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentBusinessRequest.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentBusinessResult.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentCommandRoute.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentBusinessHandler.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentCommandRouter.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/gateway/FeishuAgentCommandRouterTest.java`

**Interfaces:**
- Produces: `FeishuAgentCommandRouter#route(String commandText): Optional<FeishuAgentBusinessHandler>`
- Produces: `FeishuAgentBusinessHandler#routes(): List<FeishuAgentCommandRoute>`
- Produces: `FeishuAgentBusinessHandler#handle(FeishuAgentBusinessRequest request): FeishuAgentBusinessResult`
- Produces: `FeishuAgentBusinessResult.success(String title, String summary): FeishuAgentBusinessResult`
- Produces: `FeishuAgentBusinessResult.failure(String title, String message): FeishuAgentBusinessResult`

- [ ] **Step 1: Write the failing router test**

```java
package com.zimo.module.zimo.gateway;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAgentCommandRouterTest {

  @Test
  void routesByPrefixThenPriority() {
    FeishuAgentBusinessHandler highPriority = new FakeHandler("项目查询", 10, List.of("项目"), List.of("项目进度"));
    FeishuAgentBusinessHandler lowPriority = new FakeHandler("项目兜底", 50, List.of("项目"), List.of());

    FeishuAgentCommandRouter router = new FeishuAgentCommandRouter(List.of(lowPriority, highPriority));

    assertThat(router.route("项目 XJ100 进度"))
            .containsSame(highPriority);
  }

  @Test
  void routesByKeywordWhenPrefixDoesNotMatch() {
    FeishuAgentBusinessHandler handler = new FakeHandler("交期风险", 20, List.of("风险"), List.of("交期风险"));

    FeishuAgentCommandRouter router = new FeishuAgentCommandRouter(List.of(handler));

    assertThat(router.route("帮我看看交期风险"))
            .containsSame(handler);
  }

  @Test
  void returnsEmptyWhenNoRouteMatched() {
    FeishuAgentCommandRouter router = new FeishuAgentCommandRouter(List.of());

    assertThat(router.route("未知指令")).isEmpty();
  }

  private static class FakeHandler implements FeishuAgentBusinessHandler {
    private final FeishuAgentCommandRoute route;

    private FakeHandler(String name, int priority, List<String> prefixes, List<String> keywords) {
      this.route = new FeishuAgentCommandRoute(name, prefixes, keywords, priority);
    }

    @Override
    public List<FeishuAgentCommandRoute> routes() {
      return List.of(route);
    }

    @Override
    public FeishuAgentBusinessResult handle(FeishuAgentBusinessRequest request) {
      return FeishuAgentBusinessResult.success(route.getName(), "ok");
    }
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentCommandRouterTest test`

Expected: FAIL because `FeishuAgentCommandRouter` and related gateway model classes do not exist.

- [ ] **Step 3: Implement gateway models and router**

Create the model classes with these exact signatures:

```java
public class FeishuAgentBusinessRequest {
    public FeishuAgentBusinessRequest(FeishuAgentCommandMessage message);
    public FeishuAgentCommandMessage getMessage();
    public String getCommandText();
    public String getTenantKey();
    public String getSenderUserId();
}
```

```java
public class FeishuAgentBusinessResult {
    public static FeishuAgentBusinessResult success(String title, String summary);
    public static FeishuAgentBusinessResult failure(String title, String message);
    public FeishuAgentBusinessResult field(String name, Object value);
    public FeishuAgentBusinessResult archiveField(String name, Object value);
    public FeishuAgentBusinessResult link(String text, String url);
    public boolean isSuccess();
    public String getTitle();
    public String getSummary();
    public String getMessage();
    public Map<String, Object> getFields();
    public Map<String, Object> getArchiveFields();
    public Map<String, String> getLinks();
}
```

```java
public class FeishuAgentCommandRoute {
    public FeishuAgentCommandRoute(String name, List<String> prefixes, List<String> keywords, int priority);
    public boolean matches(String commandText);
    public String getName();
    public List<String> getPrefixes();
    public List<String> getKeywords();
    public int getPriority();
}
```

```java
public interface FeishuAgentBusinessHandler {
    List<FeishuAgentCommandRoute> routes();
    FeishuAgentBusinessResult handle(FeishuAgentBusinessRequest request);
}
```

```java
public class FeishuAgentCommandRouter {
    public FeishuAgentCommandRouter(List<FeishuAgentBusinessHandler> handlers);
    public Optional<FeishuAgentBusinessHandler> route(String commandText);
}
```

Implementation rules:

- `FeishuAgentCommandRoute#matches` trims the command text.
- Prefix matching uses `startsWith`.
- Keyword matching uses `contains`.
- Empty prefixes and keywords never match.
- `FeishuAgentCommandRouter#route` flattens all handler routes, filters matches, sorts by route priority ascending, and returns the matching handler.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentCommandRouterTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/gateway/FeishuAgentCommandRouterTest.java
git commit -m "feat: add feishu agent command router"
```

---

### Task 2: 限流与卡片组装

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentRateLimiter.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentResultCardFactory.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/gateway/FeishuAgentRateLimiterTest.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/gateway/FeishuAgentResultCardFactoryTest.java`

**Interfaces:**
- Consumes: `FeishuAgentBusinessResult`
- Produces: `FeishuAgentRateLimiter#tryAcquire(String tenantKey, String senderUserId): boolean`
- Produces: `FeishuAgentResultCardFactory#buildCard(FeishuAgentBusinessResult result, boolean archived, String archiveMessage): String`

- [ ] **Step 1: Write failing rate limiter test**

```java
package com.zimo.module.zimo.gateway;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAgentRateLimiterTest {

  @Test
  void rejectsRequestsOverWindowLimit() {
    FeishuAgentRateLimiter limiter = new FeishuAgentRateLimiter(Clock.fixed(
            Instant.parse("2026-07-03T00:00:00Z"), ZoneId.of("UTC")), true, 60, 2);

    assertThat(limiter.tryAcquire("tenant-a", "user-a")).isTrue();
    assertThat(limiter.tryAcquire("tenant-a", "user-a")).isTrue();
    assertThat(limiter.tryAcquire("tenant-a", "user-a")).isFalse();
    assertThat(limiter.tryAcquire("tenant-a", "user-b")).isTrue();
  }

  @Test
  void allowsAllWhenDisabled() {
    FeishuAgentRateLimiter limiter = new FeishuAgentRateLimiter(Clock.systemUTC(), false, 60, 1);

    assertThat(limiter.tryAcquire("tenant-a", "user-a")).isTrue();
    assertThat(limiter.tryAcquire("tenant-a", "user-a")).isTrue();
  }
}
```

- [ ] **Step 2: Write failing card factory test**

```java
package com.zimo.module.zimo.gateway;

import reply.com.zimo.module.feishu.FeishuCardTemplateFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAgentResultCardFactoryTest {

  @Test
  void buildsCardWithFieldsLinksAndArchiveStatus() {
    FeishuAgentBusinessResult result = FeishuAgentBusinessResult.success("项目进度", "主项目数据已查询")
            .field("项目编号", "XJ100")
            .field("当前阶段", "制造")
            .link("打开表格", "https://feishu.cn/base/app123");

    String card = new FeishuAgentResultCardFactory(new FeishuCardTemplateFactory())
            .buildCard(result, true, "已写入多维表格");

    assertThat(card).contains("项目进度");
    assertThat(card).contains("项目编号");
    assertThat(card).contains("XJ100");
    assertThat(card).contains("已写入多维表格");
    assertThat(card).contains("打开表格");
    assertThat(card).contains("https://feishu.cn/base/app123");
  }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentRateLimiterTest,FeishuAgentResultCardFactoryTest test`

Expected: FAIL because limiter and card factory do not exist.

- [ ] **Step 4: Implement limiter and card factory**

Implementation signatures:

```java
public class FeishuAgentRateLimiter {
    public FeishuAgentRateLimiter(Clock clock, boolean enabled, long windowSeconds, int maxRequests);
    public boolean tryAcquire(String tenantKey, String senderUserId);
}
```

```java
public class FeishuAgentResultCardFactory {
    public FeishuAgentResultCardFactory(FeishuCardTemplateFactory cardTemplateFactory);
    public String buildCard(FeishuAgentBusinessResult result, boolean archived, String archiveMessage);
}
```

Implementation rules:

- Limiter key is `tenantKey + ":" + senderUserId`.
- Remove timestamps older than `windowSeconds`.
- When `maxRequests <= 0`, use `1`.
- Card markdown starts with result summary or message.
- Render fields as `**字段名**：字段值`.
- Render archive status as `**归档状态**：已写入多维表格` or the provided archive message.
- Convert result links into `FeishuCardButton` list with type `primary` for the first link and `default` for the rest.
- Limit displayed string value length to 500 characters.

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentRateLimiterTest,FeishuAgentResultCardFactoryTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/gateway
git commit -m "feat: add feishu agent result card and limiter"
```

---

### Task 3: 调度服务与消息处理器

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentDispatchService.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentGatewayMessageHandler.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway/FeishuAgentGatewayException.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/gateway/FeishuAgentDispatchServiceTest.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/gateway/FeishuAgentGatewayMessageHandlerTest.java`

**Interfaces:**
- Consumes: `FeishuConfigProvider#getActiveConfig()`
- Consumes: `FeishuBitableCliService#createRecord(BitableRecordCreateRequest request)`
- Consumes: `FeishuAgentReplyService#streamText(String messageId, String text, FeishuStreamReplyOptions options)`
- Consumes: `FeishuAgentReplyService#replyCard(String messageId, String cardJson)`
- Produces: `FeishuAgentDispatchService#dispatch(FeishuAgentCommandMessage message, FeishuAgentReplyService replyService): void`
- Produces: `FeishuAgentGatewayMessageHandler#handle(FeishuAgentCommandMessage message, FeishuAgentReplyService replyService): void`

- [ ] **Step 1: Write failing dispatch service tests**

```java
package com.zimo.module.zimo.gateway;

import channel.com.zimo.module.feishu.FeishuAgentCommandMessage;
import cli.com.zimo.module.feishu.FeishuCliCommandResult;
import bitable.cli.com.zimo.module.feishu.FeishuBitableCliService;
import config.com.zimo.module.feishu.FeishuConfigProvider;
import config.com.zimo.module.feishu.FeishuRuntimeConfig;
import message.com.zimo.module.feishu.FeishuMessageResponse;
import reply.com.zimo.module.feishu.FeishuAgentReplyClient;
import reply.com.zimo.module.feishu.FeishuAgentReplyService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeishuAgentDispatchServiceTest {

  @Test
  void dispatchesBusinessResultArchivesAndRepliesCard() {
    FeishuConfigProvider configProvider = () -> new FeishuRuntimeConfig("app_id", "secret", "token", "key");
    FeishuAgentBusinessHandler handler = request -> FeishuAgentBusinessResult.success("项目进度", "查询成功")
            .field("项目编号", "XJ100")
            .archiveField("项目编号", "XJ100")
            .link("打开表格", "https://feishu.cn/base/app123");
    FeishuAgentCommandRouter router = new FeishuAgentCommandRouter(List.of(new RoutedHandler(handler)));
    FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
    when(bitableCliService.createRecord(any())).thenReturn(FeishuCliCommandResult.success(0, "{\"ok\":true}", "", 12));
    RecordingReplyClient replyClient = new RecordingReplyClient();

    FeishuAgentDispatchService service = new FeishuAgentDispatchService(
            configProvider,
            router,
            bitableCliService,
            new FeishuAgentRateLimiter(java.time.Clock.systemUTC(), true, 60, 10),
            new FeishuAgentResultCardFactory(new reply.com.zimo.module.feishu.FeishuCardTemplateFactory()),
            true,
            true,
            "app_token",
            "table_id");

    service.dispatch(message("项目 XJ100 进度"), new FeishuAgentReplyService(replyClient));

    verify(bitableCliService).createRecord(any());
    assertThat(replyClient.textPayloads).anyMatch(text -> text.contains("正在处理"));
    assertThat(replyClient.cardPayload).contains("项目进度");
    assertThat(replyClient.cardPayload).contains("打开表格");
  }

  @Test
  void repliesCredentialErrorWhenActiveConfigMissing() {
    FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
    RecordingReplyClient replyClient = new RecordingReplyClient();

    FeishuAgentDispatchService service = new FeishuAgentDispatchService(
            () -> null,
            new FeishuAgentCommandRouter(List.of()),
            bitableCliService,
            new FeishuAgentRateLimiter(java.time.Clock.systemUTC(), true, 60, 10),
            new FeishuAgentResultCardFactory(new reply.com.zimo.module.feishu.FeishuCardTemplateFactory()),
            true,
            true,
            "app_token",
            "table_id");

    service.dispatch(message("项目 XJ100"), new FeishuAgentReplyService(replyClient));

    verify(bitableCliService, never()).createRecord(any());
    assertThat(replyClient.textPayloads).anyMatch(text -> text.contains("飞书应用未完成配置"));
  }

  private static FeishuAgentCommandMessage message(String commandText) {
    return new FeishuAgentCommandMessage("msg_1", "chat_1", "group", "tenant_1",
            "user_1", "open_1", "union_1", commandText, commandText, "text", true);
  }

  private static class RoutedHandler implements FeishuAgentBusinessHandler {
    private final FeishuAgentBusinessHandler delegate;

    private RoutedHandler(FeishuAgentBusinessHandler delegate) {
      this.delegate = delegate;
    }

    @Override
    public List<FeishuAgentCommandRoute> routes() {
      return List.of(new FeishuAgentCommandRoute("项目", List.of("项目"), List.of(), 10));
    }

    @Override
    public FeishuAgentBusinessResult handle(FeishuAgentBusinessRequest request) {
      return delegate.handle(request);
    }
  }

  private static class RecordingReplyClient implements FeishuReplyClientStub {
  }
}
```

Add a reusable test stub in the same test file or package:

```java
interface FeishuReplyClientStub extends FeishuAgentReplyClient {
    java.util.List<String> textPayloads = new java.util.ArrayList<>();
    String[] cardHolder = new String[1];

    @Override
    default FeishuMessageResponse replyText(String messageId, String text) {
        textPayloads.add(text);
        return FeishuMessageResponse.success("reply_" + messageId);
    }

    @Override
    default FeishuMessageResponse updateText(String messageId, String text) {
        textPayloads.add(text);
        return FeishuMessageResponse.success(messageId);
    }

    @Override
    default FeishuMessageResponse replyCard(String messageId, String cardJson) {
        cardHolder[0] = cardJson;
        return FeishuMessageResponse.success("card_" + messageId);
    }
}
```

When writing the real test, prefer a concrete `RecordingReplyClient` class with instance fields so assertions do not share static state.

- [ ] **Step 2: Write failing message handler test**

```java
package com.zimo.module.zimo.gateway;

import channel.com.zimo.module.feishu.FeishuAgentCommandMessage;
import reply.com.zimo.module.feishu.FeishuAgentReplyService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FeishuAgentGatewayMessageHandlerTest {

  @Test
  void delegatesToDispatchService() {
    FeishuAgentDispatchService dispatchService = mock(FeishuAgentDispatchService.class);
    FeishuAgentGatewayMessageHandler handler = new FeishuAgentGatewayMessageHandler(dispatchService);
    FeishuAgentCommandMessage message = new FeishuAgentCommandMessage("msg_1", "chat_1", "group", "tenant_1",
            "user_1", "open_1", "union_1", "项目 XJ100", "项目 XJ100", "text", true);
    FeishuAgentReplyService replyService = mock(FeishuAgentReplyService.class);

    handler.handle(message, replyService);

    verify(dispatchService).dispatch(message, replyService);
  }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentDispatchServiceTest,FeishuAgentGatewayMessageHandlerTest test`

Expected: FAIL because dispatch service and gateway message handler do not exist.

- [ ] **Step 4: Implement dispatch service and message handler**

Constructor signatures:

```java
public FeishuAgentDispatchService(
        FeishuConfigProvider configProvider,
        FeishuAgentCommandRouter router,
        FeishuBitableCliService bitableCliService,
        FeishuAgentRateLimiter rateLimiter,
        FeishuAgentResultCardFactory cardFactory,
        boolean progressReplyEnabled,
        boolean archiveEnabled,
        String archiveAppToken,
        String archiveTableId)
```

```java
public class FeishuAgentGatewayMessageHandler implements FeishuAgentMessageHandler {
    public FeishuAgentGatewayMessageHandler(FeishuAgentDispatchService dispatchService);
    @Override
    public void handle(FeishuAgentCommandMessage message, FeishuAgentReplyService replyService);
}
```

Implementation rules:

- Validate `message` and `replyService` with `Objects.requireNonNull`.
- Treat blank command text as help and reply text `请输入有效指令，例如：项目 XJ100 进度。`.
- Active config is valid only when AppID and AppSecret are both non-blank.
- Rate limit failure replies text `请求过于频繁，请稍后再试。`.
- Unknown route replies text `暂未匹配到可执行指令，请输入：帮助。`.
- Business exceptions reply text `指令执行失败，请稍后再试。`.
- Archive only when `archiveEnabled=true` and app token/table ID are non-blank.
- Archive fields include `指令`, `租户`, `发送人`, `结果标题`, `结果摘要` and result archive fields.
- Progress reply text is `正在处理：<commandText>` when enabled.
- Final card is sent after business handling, regardless of archive success.

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentDispatchServiceTest,FeishuAgentGatewayMessageHandlerTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/gateway modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/gateway
git commit -m "feat: add feishu agent dispatch gateway service"
```

---

### Task 4: 自动装配接入

**Files:**
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAgentGatewayProperties.java`
- Modify: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java`
- Test: `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAgentGatewayAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `FeishuAgentDispatchService`
- Consumes: `FeishuAgentGatewayMessageHandler`
- Produces: property prefix `feishu.agent.gateway`
- Produces: auto-configured Bean set for gateway.

- [ ] **Step 1: Write failing autoconfiguration test**

```java
package com.zimo.module.zimo.autoconfig;

import channel.com.zimo.module.feishu.FeishuAgentMessageHandler;
import config.com.zimo.module.feishu.FeishuConfigProvider;
import config.com.zimo.module.feishu.FeishuRuntimeConfig;
import gateway.com.zimo.module.feishu.FeishuAgentCommandRouter;
import gateway.com.zimo.module.feishu.FeishuAgentDispatchService;
import gateway.com.zimo.module.feishu.FeishuAgentGatewayMessageHandler;
import gateway.com.zimo.module.feishu.FeishuAgentRateLimiter;
import gateway.com.zimo.module.feishu.FeishuAgentResultCardFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAgentGatewayAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(FeishuAutoConfiguration.class))
          .withPropertyValues(
                  "feishu.app-id=test_app",
                  "feishu.app-secret=test_secret",
                  "feishu.verification-token=test_token",
                  "feishu.encrypt-key=test_encrypt",
                  "feishu.agent.channel.auto-start=false",
                  "feishu.cli.enabled=true")
          .withBean(FeishuConfigProvider.class,
                  () -> () -> new FeishuRuntimeConfig("test_app", "test_secret", "token", "key"));

  @Test
  void registersGatewayBeansWhenEnabled() {
    runner.withPropertyValues("feishu.agent.gateway.enabled=true")
            .run(context -> {
              assertThat(context).hasSingleBean(FeishuAgentGatewayProperties.class);
              assertThat(context).hasSingleBean(FeishuAgentCommandRouter.class);
              assertThat(context).hasSingleBean(FeishuAgentRateLimiter.class);
              assertThat(context).hasSingleBean(FeishuAgentResultCardFactory.class);
              assertThat(context).hasSingleBean(FeishuAgentDispatchService.class);
              assertThat(context).hasSingleBean(FeishuAgentMessageHandler.class);
              assertThat(context).getBean(FeishuAgentMessageHandler.class)
                      .isInstanceOf(FeishuAgentGatewayMessageHandler.class);
            });
  }

  @Test
  void skipsGatewayHandlerWhenDisabled() {
    runner.withPropertyValues("feishu.agent.gateway.enabled=false")
            .run(context -> assertThat(context).getBean(FeishuAgentMessageHandler.class)
                    .isNotInstanceOf(FeishuAgentGatewayMessageHandler.class));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl modules/module-feishu/module-feishu-autoconfig -Dtest=FeishuAgentGatewayAutoConfigurationTest test`

Expected: FAIL because `FeishuAgentGatewayProperties` and gateway beans are not registered.

- [ ] **Step 3: Implement properties and auto-configuration**

Create properties class:

```java
@ConfigurationProperties(prefix = "feishu.agent.gateway")
public class FeishuAgentGatewayProperties {
    private boolean enabled = true;
    private boolean rateLimitEnabled = true;
    private long rateLimitWindowSeconds = 60;
    private int rateLimitMaxRequests = 10;
    private boolean archiveEnabled = true;
    private String archiveAppToken;
    private String archiveTableId;
    private boolean progressReplyEnabled = true;
    // getters and setters
}
```

Modify `FeishuAutoConfiguration`:

- Add `FeishuAgentGatewayProperties.class` to `@EnableConfigurationProperties`.
- Import gateway classes.
- Register `FeishuAgentCommandRouter` from `ObjectProvider<FeishuAgentBusinessHandler>`.
- Register `FeishuAgentRateLimiter` with `Clock.systemUTC()` and gateway properties.
- Register `FeishuAgentResultCardFactory` with `new FeishuCardTemplateFactory()`.
- Register `FeishuAgentDispatchService` with config provider, router, bitable CLI service, limiter, card factory and properties.
- Register `FeishuAgentGatewayMessageHandler` as `FeishuAgentMessageHandler` under `@ConditionalOnMissingBean(FeishuAgentMessageHandler.class)` and `@ConditionalOnProperty(prefix = "feishu.agent.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)`.
- Keep existing `NoopFeishuAgentMessageHandler` as fallback for channel enabled but gateway disabled.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl modules/module-feishu/module-feishu-autoconfig -Dtest=FeishuAgentGatewayAutoConfigurationTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAgentGatewayProperties.java modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAgentGatewayAutoConfigurationTest.java
git commit -m "feat: autoconfigure feishu agent dispatch gateway"
```

---

### Task 5: 全模块验证与收尾

**Files:**
- Modify only if verification exposes a compile or behavior issue in files touched by Tasks 1-4.

**Interfaces:**
- Consumes: all gateway and autoconfig classes.
- Produces: passing module verification.

- [ ] **Step 1: Run core tests**

Run: `mvn -pl modules/module-feishu/module-feishu-core -am test`

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run autoconfig tests**

Run: `mvn -pl modules/module-feishu/module-feishu-autoconfig -am test`

Expected: BUILD SUCCESS.

- [ ] **Step 3: Review changed files**

Run: `git diff -- modules/module-feishu`

Expected: diff only contains gateway implementation, gateway tests, autoconfiguration changes and related tests.

- [ ] **Step 4: Commit final verification fixes when needed**

If Step 1 or Step 2 required changes, commit only those files:

```bash
git add modules/module-feishu
git commit -m "test: verify feishu agent dispatch gateway"
```

If no changes were needed after Task 4, do not create an empty commit.

---

## Self-Review

- Spec coverage: 指令路由、三层依赖串联、当前有效凭据读取、参数校验、限流、异常兜底、标准业务结果卡片、多维表格归档、自动装配和测试均有对应任务。
- Placeholder scan: 本计划每个任务都有明确文件、接口、测试命令和预期结果。
- Type consistency: `FeishuAgentBusinessHandler`、`FeishuAgentBusinessResult`、`FeishuAgentCommandRouter`、`FeishuAgentDispatchService`、`FeishuAgentGatewayMessageHandler` 的签名在任务之间保持一致。
