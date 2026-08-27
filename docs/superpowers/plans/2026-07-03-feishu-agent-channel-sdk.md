# 飞书 Agent Channel SDK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `module-feishu` 中新增基于飞书官方 Java Channel SDK 的 Agent 交互层，按当前启用飞书应用建立单活 WebSocket 长连接，接收 @机器人消息并提供文本、流式和卡片回复能力。

**Architecture:** 核心包继续放在 `module-feishu-core`，官方 SDK 适配和自动装配放在 `module-feishu-autoconfig`。核心层只定义稳定业务接口、实体、解析器、服务和默认处理器；自动装配层负责创建 `com.lark.oapi.ws.Client`、官方 IM 回复客户端和启动生命周期。

**Tech Stack:** Java 17, Spring Boot 3.4.5, Maven, MyBatis-Plus, 飞书 `com.larksuite.oapi:oapi-sdk:2.7.3`, JUnit 5, AssertJ, Mockito.

## Global Constraints

- 当前只支持“当前启用的飞书应用”单活连接，读取 `ps_feishu_config.enabled = 1`。
- Channel SDK 使用 `com.lark.oapi.ws.Client` 和 `EventDispatcher.onP2MessageReceiveV1`。
- 配置前缀使用 `feishu.agent.channel.*`，不与 `feishu.agent.credential.*` 混用。
- 不删除现有 HTTP 事件回调入口 `/api/feishu/events`。
- 不开发前端页面，不实现真实大模型推理。
- 新增或修改 `.md` 文档默认使用中文。
- 不编辑 `target/`、`dist/` 等生成产物。
- 生产代码必须先有失败测试再实现。

---

## 文件结构

### Core 新增文件

- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/channel/FeishuAgentCommandMessage.java`  
  内部命令消息模型，承载飞书消息解析后的稳定字段。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/channel/FeishuChannelMessageParser.java`  
  从飞书事件结构或 Map 结构中解析 @机器人消息。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/channel/FeishuAgentMessageHandler.java`  
  业务 Agent 处理接口。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/channel/NoopFeishuAgentMessageHandler.java`  
  默认处理器，回复收到的指令，便于连通性验证。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/channel/FeishuChannelMessageListener.java`  
  Channel 事件入口，串联解析、日志、权限绑定、业务处理和异常捕获。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/channel/FeishuChannelClientManager.java`  
  Channel 生命周期接口。

- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/reply/FeishuAgentReplyClient.java`  
  回复客户端接口，屏蔽官方 SDK。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/reply/FeishuAgentReplyService.java`  
  文本、流式、卡片回复服务。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/reply/FeishuCardButton.java`  
  卡片按钮值对象。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/reply/FeishuStreamReplyOptions.java`  
  流式回复配置值对象。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/reply/FeishuCardTemplateFactory.java`  
  交互式卡片 JSON 模板工厂。

- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/mapping/FeishuUserMappingEntity.java`  
  `ps_feishu_user_mapping` 实体。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/mapping/FeishuUserMappingMapper.java`  
  用户映射 Mapper。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/mapping/FeishuInternalUserSnapshot.java`  
  内部用户权限快照。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/mapping/FeishuUserMappingService.java`  
  用户映射查询服务。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/mapping/FeishuUserPermissionBinder.java`  
  消息线程权限上下文绑定器。

- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/log/FeishuMessageLogEntity.java`  
  `ps_feishu_message_log` 实体。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/log/FeishuMessageLogMapper.java`  
  消息日志 Mapper。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/log/FeishuMessageLogService.java`  
  消息日志服务。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/log/FeishuMessageLogStage.java`  
  日志阶段枚举。
- `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuAgentSchemaInitializer.java`  
  新增映射表和日志表的初始化器。

### Autoconfig 新增或修改文件

- `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAgentChannelProperties.java`  
  `feishu.agent.channel.*` 配置属性。
- `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/OfficialFeishuAgentReplyClient.java`  
  官方 SDK IM 回复适配器。
- `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/OfficialFeishuChannelClientManager.java`  
  官方 SDK WebSocket Client 单活连接管理器。
- `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java`  
  注册新增属性、服务、Mapper、初始化器和 Channel 生命周期 Bean。

### 测试文件

- `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/channel/FeishuChannelMessageParserTest.java`
- `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/reply/FeishuAgentReplyServiceTest.java`
- `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/reply/FeishuCardTemplateFactoryTest.java`
- `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/mapping/FeishuUserMappingServiceTest.java`
- `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/mapping/FeishuUserPermissionBinderTest.java`
- `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/log/FeishuMessageLogServiceTest.java`
- `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/channel/FeishuChannelMessageListenerTest.java`
- `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuAgentSchemaInitializerTest.java`
- `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAgentChannelPropertiesTest.java`
- `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAgentChannelAutoConfigurationTest.java`
- `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/OfficialFeishuChannelClientManagerTest.java`

---

### Task 1: 消息解析模型与 Parser

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/channel/FeishuAgentCommandMessage.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/channel/FeishuChannelMessageParser.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/channel/FeishuChannelMessageParserTest.java`

**Interfaces:**
- Produces: `FeishuChannelMessageParser.parse(Map<String, Object> payload): Optional<FeishuAgentCommandMessage>`
- Produces: `FeishuAgentCommandMessage` getters: `getMessageId()`, `getChatId()`, `getTenantKey()`, `getSenderUserId()`, `getSenderOpenId()`, `getSenderUnionId()`, `getRawText()`, `getCommandText()`, `getMessageType()`, `isMentionedBot()`
- Consumes: Jackson `ObjectMapper` for message content JSON parsing.

- [ ] **Step 1: Write the failing Parser test**

```java
package com.zimo.module.zimo.channel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuChannelMessageParserTest {

    private final FeishuChannelMessageParser parser = new FeishuChannelMessageParser();

    @Test
    void parsesMentionedTextMessageIntoCommandMessage() {
        Map<String, Object> payload = Map.of(
                "header", Map.of("event_type", "im.message.receive_v1", "tenant_key", "tenant_1"),
                "event", Map.of(
                        "sender", Map.of("sender_id", Map.of(
                                "user_id", "user_1",
                                "open_id", "open_1",
                                "union_id", "union_1"
                        )),
                        "message", Map.of(
                                "message_id", "msg_1",
                                "chat_id", "chat_1",
                                "chat_type", "group",
                                "message_type", "text",
                                "content", "{\"text\":\"@_user_2 查询项目风险\"}",
                                "mentions", List.of(Map.of("id", Map.of("user_id", "bot_1")))
                        )
                )
        );

        FeishuAgentCommandMessage message = parser.parse(payload).orElseThrow();

        assertThat(message.getMessageId()).isEqualTo("msg_1");
        assertThat(message.getChatId()).isEqualTo("chat_1");
        assertThat(message.getTenantKey()).isEqualTo("tenant_1");
        assertThat(message.getSenderUserId()).isEqualTo("user_1");
        assertThat(message.getSenderOpenId()).isEqualTo("open_1");
        assertThat(message.getSenderUnionId()).isEqualTo("union_1");
        assertThat(message.getRawText()).isEqualTo("@_user_2 查询项目风险");
        assertThat(message.getCommandText()).isEqualTo("查询项目风险");
        assertThat(message.getMessageType()).isEqualTo("text");
        assertThat(message.isMentionedBot()).isTrue();
    }

    @Test
    void ignoresNonMentionMessages() {
        Map<String, Object> payload = Map.of(
                "event", Map.of("message", Map.of(
                        "message_id", "msg_2",
                        "message_type", "text",
                        "content", "{\"text\":\"普通群消息\"}"
                ))
        );

        assertThat(parser.parse(payload)).isEmpty();
    }

    @Test
    void ignoresNonTextMessages() {
        Map<String, Object> payload = Map.of(
                "event", Map.of("message", Map.of(
                        "message_id", "msg_3",
                        "message_type", "image",
                        "mentions", List.of(Map.of("id", Map.of("user_id", "bot_1")))
                ))
        );

        assertThat(parser.parse(payload)).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuChannelMessageParserTest test`

Expected: FAIL because `FeishuChannelMessageParser` and `FeishuAgentCommandMessage` do not exist.

- [ ] **Step 3: Implement command model**

Create `FeishuAgentCommandMessage.java`:

```java
package com.zimo.module.zimo.channel;

import java.util.Objects;

public class FeishuAgentCommandMessage {
    private final String messageId;
    private final String chatId;
    private final String chatType;
    private final String tenantKey;
    private final String senderUserId;
    private final String senderOpenId;
    private final String senderUnionId;
    private final String rawText;
    private final String commandText;
    private final String messageType;
    private final boolean mentionedBot;

    public FeishuAgentCommandMessage(
            String messageId,
            String chatId,
            String chatType,
            String tenantKey,
            String senderUserId,
            String senderOpenId,
            String senderUnionId,
            String rawText,
            String commandText,
            String messageType,
            boolean mentionedBot) {
        this.messageId = messageId;
        this.chatId = chatId;
        this.chatType = chatType;
        this.tenantKey = tenantKey;
        this.senderUserId = senderUserId;
        this.senderOpenId = senderOpenId;
        this.senderUnionId = senderUnionId;
        this.rawText = rawText;
        this.commandText = commandText;
        this.messageType = messageType;
        this.mentionedBot = mentionedBot;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getChatId() {
        return chatId;
    }

    public String getChatType() {
        return chatType;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public String getSenderUserId() {
        return senderUserId;
    }

    public String getSenderOpenId() {
        return senderOpenId;
    }

    public String getSenderUnionId() {
        return senderUnionId;
    }

    public String getRawText() {
        return rawText;
    }

    public String getCommandText() {
        return commandText;
    }

    public String getMessageType() {
        return messageType;
    }

    public boolean isMentionedBot() {
        return mentionedBot;
    }

    public boolean hasCommandText() {
        return commandText != null && !commandText.trim().isEmpty();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof FeishuAgentCommandMessage that)) {
            return false;
        }
        return mentionedBot == that.mentionedBot
                && Objects.equals(messageId, that.messageId)
                && Objects.equals(chatId, that.chatId)
                && Objects.equals(chatType, that.chatType)
                && Objects.equals(tenantKey, that.tenantKey)
                && Objects.equals(senderUserId, that.senderUserId)
                && Objects.equals(senderOpenId, that.senderOpenId)
                && Objects.equals(senderUnionId, that.senderUnionId)
                && Objects.equals(rawText, that.rawText)
                && Objects.equals(commandText, that.commandText)
                && Objects.equals(messageType, that.messageType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, chatId, chatType, tenantKey, senderUserId, senderOpenId, senderUnionId,
                rawText, commandText, messageType, mentionedBot);
    }
}
```

- [ ] **Step 4: Implement parser**

Create `FeishuChannelMessageParser.java`:

```java
package com.zimo.module.zimo.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FeishuChannelMessageParser {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEXT_MESSAGE_TYPE = "text";

    public Optional<FeishuAgentCommandMessage> parse(Map<String, Object> payload) {
        Map<String, Object> event = asMap(payload == null ? null : payload.get("event"));
        Map<String, Object> message = asMap(event.get("message"));
        String messageType = asString(message.get("message_type"));
        if (!TEXT_MESSAGE_TYPE.equals(messageType) || asList(message.get("mentions")).isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> senderId = asMap(asMap(event.get("sender")).get("sender_id"));
        String rawText = extractText(asString(message.get("content")));
        String commandText = cleanMentionText(rawText);
        return Optional.of(new FeishuAgentCommandMessage(
                asString(message.get("message_id")),
                asString(message.get("chat_id")),
                asString(message.get("chat_type")),
                asString(asMap(payload.get("header")).get("tenant_key")),
                asString(senderId.get("user_id")),
                asString(senderId.get("open_id")),
                asString(senderId.get("union_id")),
                rawText,
                commandText,
                messageType,
                true
        ));
    }

    private static String extractText(String contentJson) {
        if (!hasText(contentJson)) {
            return "";
        }
        try {
            Map<String, Object> content = OBJECT_MAPPER.readValue(contentJson, new TypeReference<>() {
            });
            return asString(content.get("text"));
        } catch (Exception ignored) {
            return contentJson;
        }
    }

    private static String cleanMentionText(String rawText) {
        if (!hasText(rawText)) {
            return "";
        }
        return rawText.replaceAll("@\\S+", "").trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

    private static List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return Collections.emptyList();
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuChannelMessageParserTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/channel/FeishuAgentCommandMessage.java \
  modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/channel/FeishuChannelMessageParser.java \
  modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/channel/FeishuChannelMessageParserTest.java
git commit -m "feat: add feishu channel message parser"
```

---

### Task 2: 回复服务与卡片模板

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/reply/FeishuAgentReplyClient.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/reply/FeishuAgentReplyService.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/reply/FeishuCardButton.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/reply/FeishuStreamReplyOptions.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/reply/FeishuCardTemplateFactory.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/reply/FeishuAgentReplyServiceTest.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/reply/FeishuCardTemplateFactoryTest.java`

**Interfaces:**
- Produces: `FeishuAgentReplyClient.replyText(String messageId, String text): FeishuMessageResponse`
- Produces: `FeishuAgentReplyClient.updateText(String messageId, String text): FeishuMessageResponse`
- Produces: `FeishuAgentReplyClient.replyCard(String messageId, String cardJson): FeishuMessageResponse`
- Produces: `FeishuAgentReplyService.replyText(String messageId, String text): FeishuMessageResponse`
- Produces: `FeishuAgentReplyService.streamText(String messageId, String text, FeishuStreamReplyOptions options): FeishuMessageResponse`
- Produces: `FeishuAgentReplyService.replyCard(String messageId, String cardJson): FeishuMessageResponse`
- Produces: `FeishuCardTemplateFactory.buildActionCard(String title, String markdown, List<FeishuCardButton> buttons): String`
- Consumes: Existing `com.zimo.module.feishu.message.FeishuMessageResponse`.

- [ ] **Step 1: Write failing reply service test**

```java
package com.zimo.module.zimo.reply;

import message.com.zimo.module.feishu.FeishuMessageResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeishuAgentReplyServiceTest {

    @Test
    void sendsPlainTextReply() {
        CapturingReplyClient client = new CapturingReplyClient();
        FeishuAgentReplyService service = new FeishuAgentReplyService(client);

        FeishuMessageResponse response = service.replyText("msg_1", "已收到");

        assertThat(response.isSuccess()).isTrue();
        assertThat(client.calls).containsExactly("replyText:msg_1:已收到");
    }

    @Test
    void streamsTextByUpdatingTheSameReplyMessage() {
        CapturingReplyClient client = new CapturingReplyClient();
        FeishuAgentReplyService service = new FeishuAgentReplyService(client);

        service.streamText("msg_1", "abcdef", new FeishuStreamReplyOptions(2, Duration.ZERO));

        assertThat(client.calls).containsExactly(
                "replyText:msg_1:ab",
                "updateText:reply_msg_1:abcd",
                "updateText:reply_msg_1:abcdef"
        );
    }

    @Test
    void rejectsBlankReplyText() {
        FeishuAgentReplyService service = new FeishuAgentReplyService(new CapturingReplyClient());

        assertThatThrownBy(() -> service.replyText("msg_1", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }

    private static class CapturingReplyClient implements FeishuAgentReplyClient {
        private final List<String> calls = new ArrayList<>();

        @Override
        public FeishuMessageResponse replyText(String messageId, String text) {
            calls.add("replyText:" + messageId + ":" + text);
            return FeishuMessageResponse.success("reply_" + messageId);
        }

        @Override
        public FeishuMessageResponse updateText(String messageId, String text) {
            calls.add("updateText:" + messageId + ":" + text);
            return FeishuMessageResponse.success(messageId);
        }

        @Override
        public FeishuMessageResponse replyCard(String messageId, String cardJson) {
            calls.add("replyCard:" + messageId + ":" + cardJson);
            return FeishuMessageResponse.success("card_" + messageId);
        }
    }
}
```

- [ ] **Step 2: Write failing card template test**

```java
package com.zimo.module.zimo.reply;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuCardTemplateFactoryTest {

    @Test
    void buildsActionCardWithButtonsInOriginalOrder() {
        FeishuCardTemplateFactory factory = new FeishuCardTemplateFactory();

        String card = factory.buildActionCard("项目风险", "**风险较高**",
                List.of(
                        FeishuCardButton.url("查看项目", "primary", "https://example.com/project"),
                        FeishuCardButton.value("确认收到", "default", Map.of("action", "ack"))
                ));

        assertThat(card).contains("\"title\"");
        assertThat(card).contains("项目风险");
        assertThat(card).contains("查看项目");
        assertThat(card).contains("确认收到");
        assertThat(card.indexOf("查看项目")).isLessThan(card.indexOf("确认收到"));
        assertThat(card).contains("https://example.com/project");
        assertThat(card).contains("\"action\":\"ack\"");
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentReplyServiceTest,FeishuCardTemplateFactoryTest test`

Expected: FAIL because reply package classes do not exist.

- [ ] **Step 4: Implement reply client interface and options**

Create `FeishuAgentReplyClient.java`:

```java
package com.zimo.module.zimo.reply;

import message.com.zimo.module.feishu.FeishuMessageResponse;

public interface FeishuAgentReplyClient {
    FeishuMessageResponse replyText(String messageId, String text);

    FeishuMessageResponse updateText(String messageId, String text);

    FeishuMessageResponse replyCard(String messageId, String cardJson);
}
```

Create `FeishuStreamReplyOptions.java`:

```java
package com.zimo.module.zimo.reply;

import java.time.Duration;

public class FeishuStreamReplyOptions {
    private final int chunkSize;
    private final Duration interval;

    public FeishuStreamReplyOptions(int chunkSize, Duration interval) {
        this.chunkSize = chunkSize <= 0 ? 80 : chunkSize;
        this.interval = interval == null || interval.isNegative() ? Duration.ZERO : interval;
    }

    public static FeishuStreamReplyOptions defaults() {
        return new FeishuStreamReplyOptions(80, Duration.ofMillis(300));
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public Duration getInterval() {
        return interval;
    }
}
```

- [ ] **Step 5: Implement reply service**

Create `FeishuAgentReplyService.java`:

```java
package com.zimo.module.zimo.reply;

import message.com.zimo.module.feishu.FeishuMessageResponse;

import java.util.Objects;

public class FeishuAgentReplyService {
    private final FeishuAgentReplyClient replyClient;

    public FeishuAgentReplyService(FeishuAgentReplyClient replyClient) {
        this.replyClient = Objects.requireNonNull(replyClient, "replyClient must not be null");
    }

    public FeishuMessageResponse replyText(String messageId, String text) {
        requireText(messageId, "messageId must not be blank");
        requireText(text, "text must not be blank");
        return replyClient.replyText(messageId, text);
    }

    public FeishuMessageResponse streamText(String messageId, String text, FeishuStreamReplyOptions options) {
        requireText(messageId, "messageId must not be blank");
        requireText(text, "text must not be blank");
        FeishuStreamReplyOptions actualOptions = options == null ? FeishuStreamReplyOptions.defaults() : options;
        int chunkSize = actualOptions.getChunkSize();
        String firstChunk = text.substring(0, Math.min(chunkSize, text.length()));
        FeishuMessageResponse firstResponse = replyClient.replyText(messageId, firstChunk);
        if (!firstResponse.isSuccess() || firstChunk.length() == text.length()) {
            return firstResponse;
        }

        String replyMessageId = firstResponse.getMessageId();
        FeishuMessageResponse latest = firstResponse;
        for (int end = firstChunk.length() + chunkSize; end <= text.length() + chunkSize; end += chunkSize) {
            int actualEnd = Math.min(end, text.length());
            sleep(actualOptions);
            latest = replyClient.updateText(replyMessageId, text.substring(0, actualEnd));
            if (!latest.isSuccess() || actualEnd == text.length()) {
                return latest;
            }
        }
        return latest;
    }

    public FeishuMessageResponse replyCard(String messageId, String cardJson) {
        requireText(messageId, "messageId must not be blank");
        requireText(cardJson, "cardJson must not be blank");
        return replyClient.replyCard(messageId, cardJson);
    }

    private static void sleep(FeishuStreamReplyOptions options) {
        if (options.getInterval().isZero()) {
            return;
        }
        try {
            Thread.sleep(options.getInterval().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }
}
```

- [ ] **Step 6: Implement card button and factory**

Create `FeishuCardButton.java`:

```java
package com.zimo.module.zimo.reply;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class FeishuCardButton {
    private final String text;
    private final String type;
    private final String url;
    private final Map<String, Object> value;

    private FeishuCardButton(String text, String type, String url, Map<String, Object> value) {
        this.text = text;
        this.type = hasText(type) ? type : "default";
        this.url = url;
        this.value = value == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    public static FeishuCardButton url(String text, String type, String url) {
        return new FeishuCardButton(text, type, url, Collections.emptyMap());
    }

    public static FeishuCardButton value(String text, String type, Map<String, Object> value) {
        return new FeishuCardButton(text, type, null, value);
    }

    public String getText() {
        return text;
    }

    public String getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, Object> getValue() {
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
```

Create `FeishuCardTemplateFactory.java`:

```java
package com.zimo.module.zimo.reply;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FeishuCardTemplateFactory {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String buildActionCard(String title, String markdown, List<FeishuCardButton> buttons) {
        requireText(title, "title must not be blank");
        requireText(markdown, "markdown must not be blank");

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("config", Map.of("wide_screen_mode", true));
        card.put("header", Map.of(
                "template", "blue",
                "title", Map.of("tag", "plain_text", "content", title)
        ));

        List<Object> elements = new ArrayList<>();
        elements.add(Map.of("tag", "markdown", "content", markdown));
        if (buttons != null && !buttons.isEmpty()) {
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("tag", "action");
            action.put("layout", "bisected");
            List<Object> actions = new ArrayList<>();
            for (FeishuCardButton button : buttons) {
                actions.add(toButton(button));
            }
            action.put("actions", actions);
            elements.add(action);
        }
        card.put("elements", elements);

        try {
            return OBJECT_MAPPER.writeValueAsString(card);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("card cannot be serialized", e);
        }
    }

    private static Map<String, Object> toButton(FeishuCardButton button) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("tag", "button");
        item.put("text", Map.of("tag", "plain_text", "content", button.getText()));
        item.put("type", button.getType());
        if (hasText(button.getUrl())) {
            item.put("url", button.getUrl());
        }
        if (!button.getValue().isEmpty()) {
            item.put("value", button.getValue());
        }
        return item;
    }

    private static void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuAgentReplyServiceTest,FeishuCardTemplateFactoryTest test`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/reply \
  modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/reply
git commit -m "feat: add feishu agent reply utilities"
```

---

### Task 3: 用户映射与权限上下文绑定

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/mapping/FeishuUserMappingEntity.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/mapping/FeishuUserMappingMapper.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/mapping/FeishuInternalUserSnapshot.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/mapping/FeishuUserMappingService.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/mapping/FeishuUserPermissionBinder.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/mapping/FeishuUserMappingServiceTest.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/mapping/FeishuUserPermissionBinderTest.java`

**Interfaces:**
- Consumes: `FeishuAgentCommandMessage` from Task 1.
- Produces: `FeishuUserMappingService.findInternalUser(FeishuAgentCommandMessage message): Optional<FeishuInternalUserSnapshot>`
- Produces: `FeishuUserPermissionBinder.bind(FeishuInternalUserSnapshot snapshot): AutoCloseable`
- Uses: `context.com.zimo.module.sys.PermissionCache` and `UserPermissionContext`.

- [ ] **Step 1: Write failing mapping service test**

```java
package com.zimo.module.zimo.mapping;

import channel.com.zimo.module.feishu.FeishuAgentCommandMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeishuUserMappingServiceTest {

    @Test
    void resolvesByTenantAndFeishuUserIdFirst() {
        FeishuUserMappingMapper mapper = mock(FeishuUserMappingMapper.class);
        when(mapper.selectBestMapping("tenant_1", "user_1", "open_1", "union_1"))
                .thenReturn(entity("tenant_1", "user_1", "open_1", "union_1", 7L, "planner", 1));
        FeishuUserMappingService service = new FeishuUserMappingService(mapper);

        FeishuAgentCommandMessage message = command("tenant_1", "user_1", "open_1", "union_1");

        FeishuInternalUserSnapshot snapshot = service.findInternalUser(message).orElseThrow();

        assertThat(snapshot.getUserId()).isEqualTo(7L);
        assertThat(snapshot.getAccount()).isEqualTo("planner");
    }

    @Test
    void ignoresDisabledMapping() {
        FeishuUserMappingMapper mapper = mock(FeishuUserMappingMapper.class);
        when(mapper.selectBestMapping("tenant_1", "user_1", "open_1", "union_1"))
                .thenReturn(entity("tenant_1", "user_1", "open_1", "union_1", 7L, "planner", 0));
        FeishuUserMappingService service = new FeishuUserMappingService(mapper);

        assertThat(service.findInternalUser(command("tenant_1", "user_1", "open_1", "union_1"))).isEmpty();
    }

    private static FeishuAgentCommandMessage command(String tenantKey, String userId, String openId, String unionId) {
        return new FeishuAgentCommandMessage("msg_1", "chat_1", "group", tenantKey,
                userId, openId, unionId, "查询", "查询", "text", true);
    }

    private static FeishuUserMappingEntity entity(
            String tenantKey,
            String feishuUserId,
            String openId,
            String unionId,
            Long internalUserId,
            String account,
            Integer enabled) {
        FeishuUserMappingEntity entity = new FeishuUserMappingEntity();
        entity.setTenantKey(tenantKey);
        entity.setFeishuUserId(feishuUserId);
        entity.setFeishuOpenId(openId);
        entity.setFeishuUnionId(unionId);
        entity.setInternalUserId(internalUserId);
        entity.setInternalAccount(account);
        entity.setOrganizationId("factory_1");
        entity.setOrganizationName("一号工厂");
        entity.setDataScope("FACTORY");
        entity.setPermissions("pm:project:list,wms:stock:list");
        entity.setEnabled(enabled);
        return entity;
    }
}
```

- [ ] **Step 2: Write failing permission binder test**

```java
package com.zimo.module.zimo.mapping;

import context.com.zimo.module.sys.PermissionCache;
import context.com.zimo.module.sys.UserPermissionContext;
import enums.com.zimo.module.sys.DataScopeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuUserPermissionBinderTest {

    @AfterEach
    void tearDown() {
        PermissionCache.unbindCurrent();
        PermissionCache.clear();
    }

    @Test
    void bindsAndCleansCurrentPermissionContext() throws Exception {
        FeishuUserPermissionBinder binder = new FeishuUserPermissionBinder();
        FeishuInternalUserSnapshot snapshot = new FeishuInternalUserSnapshot(
                7L,
                "planner",
                "factory_1",
                "一号工厂",
                Set.of("pm:project:list"),
                DataScopeEnum.FACTORY
        );

        AutoCloseable scope = binder.bind(snapshot);

        assertThat(UserPermissionContext.currentUserId()).contains(7L);
        assertThat(UserPermissionContext.currentAccount()).contains("planner");
        assertThat(UserPermissionContext.currentPermissions()).contains("pm:project:list");

        scope.close();

        assertThat(UserPermissionContext.current()).isEmpty();
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuUserMappingServiceTest,FeishuUserPermissionBinderTest test`

Expected: FAIL because mapping classes do not exist.

- [ ] **Step 4: Implement mapping entity and mapper**

Create `FeishuUserMappingEntity.java` with MyBatis-Plus annotations for `ps_feishu_user_mapping`. Include fields from the design: `id`, `feishuUserId`, `feishuOpenId`, `feishuUnionId`, `tenantKey`, `internalUserId`, `internalAccount`, `internalUserName`, `organizationId`, `organizationName`, `dataScope`, `permissions`, `enabled`, `remark`, `deleted`, `createTime`, `updateTime`, with getters and setters.

Create `FeishuUserMappingMapper.java`:

```java
package com.zimo.module.zimo.mapping;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface FeishuUserMappingMapper extends BaseMapper<FeishuUserMappingEntity> {

    @Select("""
            SELECT *
            FROM ps_feishu_user_mapping
            WHERE deleted = 0
              AND tenant_key = #{tenantKey}
              AND enabled = 1
              AND (
                feishu_user_id = #{feishuUserId}
                OR feishu_open_id = #{openId}
                OR feishu_union_id = #{unionId}
              )
            ORDER BY
              CASE
                WHEN feishu_user_id = #{feishuUserId} THEN 1
                WHEN feishu_open_id = #{openId} THEN 2
                WHEN feishu_union_id = #{unionId} THEN 3
                ELSE 4
              END
            LIMIT 1
            """)
    FeishuUserMappingEntity selectBestMapping(
            @Param("tenantKey") String tenantKey,
            @Param("feishuUserId") String feishuUserId,
            @Param("openId") String openId,
            @Param("unionId") String unionId);
}
```

- [ ] **Step 5: Implement snapshot, service, and binder**

Create `FeishuInternalUserSnapshot.java`:

```java
package com.zimo.module.zimo.mapping;

import enums.com.zimo.module.sys.DataScopeEnum;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class FeishuInternalUserSnapshot {
    private final Long userId;
    private final String account;
    private final String organizationId;
    private final String organizationName;
    private final Set<String> permissions;
    private final DataScopeEnum dataScope;

    public FeishuInternalUserSnapshot(Long userId, String account, String organizationId, String organizationName,
                                      Set<String> permissions, DataScopeEnum dataScope) {
        this.userId = userId;
        this.account = account;
        this.organizationId = organizationId;
        this.organizationName = organizationName;
        this.permissions = permissions == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
        this.dataScope = dataScope == null ? DataScopeEnum.SELF : dataScope;
    }

    public Long getUserId() {
        return userId;
    }

    public String getAccount() {
        return account;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public DataScopeEnum getDataScope() {
        return dataScope;
    }
}
```

Create `FeishuUserMappingService.java` and `FeishuUserPermissionBinder.java` using `PermissionCache.bindCurrent(UserPermissionContext.of(...))`; parse comma-separated permissions into a `LinkedHashSet`; parse invalid data scope as `DataScopeEnum.SELF`.

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuUserMappingServiceTest,FeishuUserPermissionBinderTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/mapping \
  modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/mapping
git commit -m "feat: add feishu user mapping and permission binding"
```

---

### Task 4: 消息链路日志与表初始化

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/log/FeishuMessageLogStage.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/log/FeishuMessageLogEntity.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/log/FeishuMessageLogMapper.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/log/FeishuMessageLogService.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuAgentSchemaInitializer.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/log/FeishuMessageLogServiceTest.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuAgentSchemaInitializerTest.java`

**Interfaces:**
- Produces: `FeishuMessageLogService.record(FeishuMessageLogEntity log): void`
- Produces: `FeishuMessageLogService.recordFailure(FeishuAgentCommandMessage message, String stage, Exception ex): void`
- Produces: `FeishuAgentSchemaInitializer.createUserMappingTableSql(): String`
- Produces: `FeishuAgentSchemaInitializer.createMessageLogTableSql(): String`

- [ ] **Step 1: Write failing log service test**

```java
package com.zimo.module.zimo.log;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FeishuMessageLogServiceTest {

    @Test
    void recordsLogThroughMapper() {
        FeishuMessageLogMapper mapper = mock(FeishuMessageLogMapper.class);
        FeishuMessageLogService service = new FeishuMessageLogService(mapper, true);
        FeishuMessageLogEntity entity = new FeishuMessageLogEntity();
        entity.setMessageId("msg_1");
        entity.setStage(FeishuMessageLogStage.RECEIVED.name());
        entity.setSuccess(1);

        service.record(entity);

        verify(mapper).insert(entity);
        assertThat(entity.getStage()).isEqualTo(FeishuMessageLogStage.RECEIVED.name());
    }

    @Test
    void skipsWhenDisabled() {
        FeishuMessageLogMapper mapper = mock(FeishuMessageLogMapper.class);
        FeishuMessageLogService service = new FeishuMessageLogService(mapper, false);

        FeishuMessageLogEntity entity = new FeishuMessageLogEntity();
        service.record(entity);

        verify(mapper, never()).insert(entity);
    }
}
```

- [ ] **Step 2: Write failing schema initializer test**

```java
package com.zimo.module.zimo.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAgentSchemaInitializerTest {

    @Test
    void userMappingSchemaContainsRequiredColumns() {
        String sql = FeishuAgentSchemaInitializer.createUserMappingTableSql().toLowerCase();

        assertThat(sql).contains("create table if not exists ps_feishu_user_mapping");
        assertThat(sql).contains("feishu_user_id");
        assertThat(sql).contains("internal_user_id");
        assertThat(sql).contains("permissions");
    }

    @Test
    void messageLogSchemaContainsRequiredColumns() {
        String sql = FeishuAgentSchemaInitializer.createMessageLogTableSql().toLowerCase();

        assertThat(sql).contains("create table if not exists ps_feishu_message_log");
        assertThat(sql).contains("message_id");
        assertThat(sql).contains("command_text");
        assertThat(sql).contains("raw_payload");
        assertThat(sql).contains("reply_payload");
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuMessageLogServiceTest,FeishuAgentSchemaInitializerTest test`

Expected: FAIL because log and schema classes do not exist.

- [ ] **Step 4: Implement log enum, entity, mapper, service**

Create `FeishuMessageLogStage.java`:

```java
package com.zimo.module.zimo.log;

public enum FeishuMessageLogStage {
    RECEIVED,
    PARSED,
    MAPPED,
    HANDLED,
    REPLIED,
    FAILED
}
```

Create `FeishuMessageLogEntity.java` with `@TableName("ps_feishu_message_log")`, `@TableId(type = IdType.AUTO)`, and fields from the design. Use `Integer success`, `Long costMillis`, and `LocalDateTime createTime`.

Create `FeishuMessageLogMapper.java`:

```java
package com.zimo.module.zimo.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface FeishuMessageLogMapper extends BaseMapper<FeishuMessageLogEntity> {
}
```

Create `FeishuMessageLogService.java` with constructor `(FeishuMessageLogMapper mapper, boolean enabled)` and methods `record(...)`, `recordFailure(...)`. `record` must return immediately when disabled or entity is null.

- [ ] **Step 5: Implement schema initializer**

Create `FeishuAgentSchemaInitializer.java` as `ApplicationRunner`. It should:
- accept `DataSource` in constructor;
- create `ps_feishu_user_mapping` if absent;
- create `ps_feishu_message_log` if absent;
- add missing columns using metadata checks;
- log completion.

Expose `public static String createUserMappingTableSql()` and `public static String createMessageLogTableSql()` for tests.

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuMessageLogServiceTest,FeishuAgentSchemaInitializerTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/log \
  modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/config/FeishuAgentSchemaInitializer.java \
  modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/log \
  modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/config/FeishuAgentSchemaInitializerTest.java
git commit -m "feat: add feishu agent message logging schema"
```

---

### Task 5: Channel 监听器与默认 Agent 处理器

**Files:**
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/channel/FeishuAgentMessageHandler.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/channel/NoopFeishuAgentMessageHandler.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/channel/FeishuChannelMessageListener.java`
- Create: `modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/channel/FeishuChannelClientManager.java`
- Test: `modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/channel/FeishuChannelMessageListenerTest.java`

**Interfaces:**
- Consumes: Tasks 1-4.
- Produces: `FeishuAgentMessageHandler.handle(FeishuAgentCommandMessage message, FeishuAgentReplyService replyService): void`
- Produces: `FeishuChannelMessageListener.onMessage(Map<String, Object> payload): void`
- Produces: `FeishuChannelClientManager.start(): void`, `stop(): void`, `isRunning(): boolean`

- [ ] **Step 1: Write failing listener test**

```java
package com.zimo.module.zimo.channel;

import log.com.zimo.module.feishu.FeishuMessageLogEntity;
import log.com.zimo.module.feishu.FeishuMessageLogService;
import mapping.com.zimo.module.feishu.FeishuInternalUserSnapshot;
import mapping.com.zimo.module.feishu.FeishuUserMappingService;
import mapping.com.zimo.module.feishu.FeishuUserPermissionBinder;
import reply.com.zimo.module.feishu.FeishuAgentReplyClient;
import reply.com.zimo.module.feishu.FeishuAgentReplyService;
import message.com.zimo.module.feishu.FeishuMessageResponse;
import enums.com.zimo.module.sys.DataScopeEnum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuChannelMessageListenerTest {

    @Test
    void bindsUserAndDelegatesMentionedCommandToHandler() {
        CapturingLogService logService = new CapturingLogService();
        CapturingHandler handler = new CapturingHandler();
        FeishuChannelMessageListener listener = new FeishuChannelMessageListener(
                new StubParser(),
                new StubMappingService(),
                new FeishuUserPermissionBinder(),
                handler,
                new FeishuAgentReplyService(new NoopReplyClient()),
                logService
        );

        listener.onMessage(Map.of("event", Map.of()));

        assertThat(handler.commands).containsExactly("查询项目");
        assertThat(logService.stages).contains("RECEIVED", "PARSED", "MAPPED", "HANDLED");
    }

    private static class StubParser extends FeishuChannelMessageParser {
        @Override
        public Optional<FeishuAgentCommandMessage> parse(Map<String, Object> payload) {
            return Optional.of(new FeishuAgentCommandMessage("msg_1", "chat_1", "group", "tenant_1",
                    "user_1", "open_1", "union_1", "@机器人 查询项目", "查询项目", "text", true));
        }
    }

    private static class StubMappingService extends FeishuUserMappingService {
        StubMappingService() {
            super(null);
        }

        @Override
        public Optional<FeishuInternalUserSnapshot> findInternalUser(FeishuAgentCommandMessage message) {
            return Optional.of(new FeishuInternalUserSnapshot(7L, "planner", "factory_1", "一号工厂",
                    Set.of("pm:project:list"), DataScopeEnum.FACTORY));
        }
    }

    private static class CapturingHandler implements FeishuAgentMessageHandler {
        private final List<String> commands = new ArrayList<>();

        @Override
        public void handle(FeishuAgentCommandMessage message, FeishuAgentReplyService replyService) {
            commands.add(message.getCommandText());
        }
    }

    private static class CapturingLogService extends FeishuMessageLogService {
        private final List<String> stages = new ArrayList<>();

        CapturingLogService() {
            super(null, true);
        }

        @Override
        public void record(FeishuMessageLogEntity entity) {
            stages.add(entity.getStage());
        }
    }

    private static class NoopReplyClient implements FeishuAgentReplyClient {
        @Override
        public FeishuMessageResponse replyText(String messageId, String text) {
            return FeishuMessageResponse.success("reply_1");
        }

        @Override
        public FeishuMessageResponse updateText(String messageId, String text) {
            return FeishuMessageResponse.success(messageId);
        }

        @Override
        public FeishuMessageResponse replyCard(String messageId, String cardJson) {
            return FeishuMessageResponse.success("reply_card_1");
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuChannelMessageListenerTest test`

Expected: FAIL because listener and handler classes do not exist.

- [ ] **Step 3: Implement handler interfaces and default handler**

Create `FeishuAgentMessageHandler.java`:

```java
package com.zimo.module.zimo.channel;

import reply.com.zimo.module.feishu.FeishuAgentReplyService;

public interface FeishuAgentMessageHandler {
    void handle(FeishuAgentCommandMessage message, FeishuAgentReplyService replyService);
}
```

Create `NoopFeishuAgentMessageHandler.java`:

```java
package com.zimo.module.zimo.channel;

import reply.com.zimo.module.feishu.FeishuAgentReplyService;

public class NoopFeishuAgentMessageHandler implements FeishuAgentMessageHandler {
    @Override
    public void handle(FeishuAgentCommandMessage message, FeishuAgentReplyService replyService) {
        if (!message.hasCommandText()) {
            replyService.replyText(message.getMessageId(), "请输入需要处理的指令。");
            return;
        }
        replyService.replyText(message.getMessageId(), "已收到指令：" + message.getCommandText());
    }
}
```

Create `FeishuChannelClientManager.java`:

```java
package com.zimo.module.zimo.channel;

public interface FeishuChannelClientManager {
    void start();

    void stop();

    boolean isRunning();
}
```

- [ ] **Step 4: Implement listener**

Create `FeishuChannelMessageListener.java` with constructor dependencies:
`FeishuChannelMessageParser parser`, `FeishuUserMappingService mappingService`, `FeishuUserPermissionBinder permissionBinder`, `FeishuAgentMessageHandler handler`, `FeishuAgentReplyService replyService`, `FeishuMessageLogService logService`.

`onMessage(Map<String, Object> payload)` must:
- record `RECEIVED`;
- parse message;
- return when parser returns empty;
- record `PARSED`;
- resolve mapping;
- reply binding prompt and record `FAILED` when mapping absent;
- bind permissions in try-with-resources;
- record `MAPPED`;
- invoke handler;
- record `HANDLED`;
- catch `Exception`, record `FAILED`, and rethrow as `IllegalStateException`.

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -pl modules/module-feishu/module-feishu-core -Dtest=FeishuChannelMessageListenerTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/channel \
  modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/channel/FeishuChannelMessageListenerTest.java
git commit -m "feat: add feishu channel message listener"
```

---

### Task 6: 官方 SDK 适配与自动装配

**Files:**
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAgentChannelProperties.java`
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/OfficialFeishuAgentReplyClient.java`
- Create: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/OfficialFeishuChannelClientManager.java`
- Modify: `modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java`
- Test: `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAgentChannelPropertiesTest.java`
- Test: `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAgentChannelAutoConfigurationTest.java`
- Test: `modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/OfficialFeishuChannelClientManagerTest.java`

**Interfaces:**
- Consumes: `FeishuChannelClientManager`, `FeishuChannelMessageListener`, `FeishuAgentReplyClient`, `FeishuAgentReplyService`, `FeishuAgentSchemaInitializer`.
- Produces: Spring Beans for Channel properties, reply client, reply service, parser, listener, default handler, mapping service, log service, schema initializer, and client manager.

- [ ] **Step 1: Write failing properties test**

```java
package com.zimo.module.zimo.autoconfig;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAgentChannelPropertiesTest {

    @Test
    void bindsChannelPropertiesWithDefaultsAndOverrides() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("feishu.agent.channel.enabled", "false")
                .withProperty("feishu.agent.channel.auto-start", "false")
                .withProperty("feishu.agent.channel.stream.chunk-size", "12")
                .withProperty("feishu.agent.channel.stream.interval-millis", "25")
                .withProperty("feishu.agent.channel.log.record-raw-payload", "false");

        FeishuAgentChannelProperties properties = Binder.get(environment)
                .bind("feishu.agent.channel", Bindable.of(FeishuAgentChannelProperties.class))
                .orElseThrow();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isAutoStart()).isFalse();
        assertThat(properties.isAutoReconnect()).isTrue();
        assertThat(properties.getStream().getChunkSize()).isEqualTo(12);
        assertThat(properties.getStream().getIntervalMillis()).isEqualTo(25);
        assertThat(properties.getLog().isRecordRawPayload()).isFalse();
        assertThat(properties.getLog().isEnabled()).isTrue();
    }
}
```

- [ ] **Step 2: Write failing auto-configuration test**

```java
package com.zimo.module.zimo.autoconfig;

import channel.com.zimo.module.feishu.FeishuAgentMessageHandler;
import channel.com.zimo.module.feishu.FeishuChannelMessageListener;
import config.com.zimo.module.feishu.FeishuConfigService;
import reply.com.zimo.module.feishu.FeishuAgentReplyService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FeishuAgentChannelAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeishuAutoConfiguration.class))
            .withPropertyValues(
                    "feishu.app-id=cli_test_app",
                    "feishu.app-secret=test_secret",
                    "feishu.verification-token=test_token",
                    "feishu.encrypt-key=test_encrypt_key",
                    "feishu.agent.channel.enabled=true",
                    "feishu.agent.channel.auto-start=false")
            .withBean(FeishuConfigService.class, () -> mock(FeishuConfigService.class));

    @Test
    void registersChannelBeansWhenEnabled() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(FeishuAgentChannelProperties.class);
            assertThat(context).hasSingleBean(FeishuAgentReplyService.class);
            assertThat(context).hasSingleBean(FeishuAgentMessageHandler.class);
            assertThat(context).hasSingleBean(FeishuChannelMessageListener.class);
        });
    }

    @Test
    void doesNotRegisterChannelListenerWhenDisabled() {
        runner.withPropertyValues("feishu.agent.channel.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FeishuChannelMessageListener.class));
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -pl modules/module-feishu/module-feishu-autoconfig -Dtest=FeishuAgentChannelPropertiesTest,FeishuAgentChannelAutoConfigurationTest test`

Expected: FAIL because properties and autoconfig beans do not exist.

- [ ] **Step 4: Implement properties**

Create `FeishuAgentChannelProperties.java`:

```java
package com.zimo.module.zimo.autoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feishu.agent.channel")
public class FeishuAgentChannelProperties {
    private boolean enabled = true;
    private boolean autoStart = true;
    private boolean autoReconnect = true;
    private long awaitReadyTimeoutSeconds = 10;
    private final Stream stream = new Stream();
    private final Log log = new Log();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public long getAwaitReadyTimeoutSeconds() {
        return awaitReadyTimeoutSeconds;
    }

    public void setAwaitReadyTimeoutSeconds(long awaitReadyTimeoutSeconds) {
        this.awaitReadyTimeoutSeconds = awaitReadyTimeoutSeconds;
    }

    public Stream getStream() {
        return stream;
    }

    public Log getLog() {
        return log;
    }

    public static class Stream {
        private int chunkSize = 80;
        private long intervalMillis = 300;

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public long getIntervalMillis() {
            return intervalMillis;
        }

        public void setIntervalMillis(long intervalMillis) {
            this.intervalMillis = intervalMillis;
        }
    }

    public static class Log {
        private boolean enabled = true;
        private boolean recordRawPayload = true;
        private boolean recordReplyPayload = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRecordRawPayload() {
            return recordRawPayload;
        }

        public void setRecordRawPayload(boolean recordRawPayload) {
            this.recordRawPayload = recordRawPayload;
        }

        public boolean isRecordReplyPayload() {
            return recordReplyPayload;
        }

        public void setRecordReplyPayload(boolean recordReplyPayload) {
            this.recordReplyPayload = recordReplyPayload;
        }
    }
}
```

- [ ] **Step 5: Implement official reply client**

Create `OfficialFeishuAgentReplyClient.java` using `FeishuConfigProvider` to build current `Client`. It must call:
- `client.im().message().reply(ReplyMessageReq...)` for text and card replies;
- `client.im().message().patch(PatchMessageReq...)` for update;
- return `FeishuMessageResponse.success(messageId)` when SDK success;
- return `FeishuMessageResponse.failure(code, msg)` on SDK failure or exception.

Use text content JSON `{"text":"..."}` and card content as the card JSON string.

- [ ] **Step 6: Implement official channel client manager**

Create `OfficialFeishuChannelClientManager.java` implementing `FeishuChannelClientManager`, `SmartLifecycle`, and `DisposableBean`.

Behavior:
- if `autoStart=false`, `isAutoStartup()` returns false;
- `start()` reads `FeishuConfigProvider.getActiveConfig()`;
- when active config missing app id or secret, log warn and do not throw;
- build `EventDispatcher.newBuilder(verificationToken, encryptKey).onP2MessageReceiveV1(...)`;
- convert `P2MessageReceiveV1` to Map using Jackson `ObjectMapper.convertValue(event, new TypeReference<Map<String,Object>>() {})`;
- call `listener.onMessage(payload)`;
- build `new com.lark.oapi.ws.Client.Builder(appId, appSecret).eventHandler(dispatcher).autoReconnect(properties.isAutoReconnect()).build()`;
- call `client.start()`;
- call `client.awaitReady(timeoutMillis)` when timeout is positive;
- `stop()` closes client and marks not running.

- [ ] **Step 7: Modify FeishuAutoConfiguration**

Update `@EnableConfigurationProperties` to include `FeishuAgentChannelProperties.class`.

Register beans under:
`@ConditionalOnProperty(prefix = "feishu.agent.channel", name = "enabled", havingValue = "true", matchIfMissing = true)`.

Add beans for:
- `FeishuChannelMessageParser`
- `FeishuAgentReplyClient`
- `FeishuAgentReplyService`
- `FeishuUserMappingService`
- `FeishuUserPermissionBinder`
- `FeishuMessageLogService`
- `FeishuAgentMessageHandler`
- `FeishuChannelMessageListener`
- `FeishuChannelClientManager`
- `FeishuAgentSchemaInitializer`

Extend `@MapperScan` package remains `com.zimo.module.feishu.mapper` only if new mappers live outside that package; otherwise add `com.zimo.module.feishu.mapping` and `com.zimo.module.feishu.log` to mapper scan. Prefer adding base packages:

```java
basePackages = {
        "com.zimo.module.feishu.mapper",
        "com.zimo.module.feishu.mapping",
        "com.zimo.module.feishu.log"
}
```

- [ ] **Step 8: Run auto-configuration tests**

Run: `mvn -pl modules/module-feishu/module-feishu-autoconfig -Dtest=FeishuAgentChannelPropertiesTest,FeishuAgentChannelAutoConfigurationTest test`

Expected: PASS.

- [ ] **Step 9: Run module verification**

Run: `mvn -pl modules/module-feishu/module-feishu-core test`

Expected: PASS.

Run: `mvn -pl modules/module-feishu/module-feishu-autoconfig -am test`

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig \
  modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig \
  modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu
git commit -m "feat: wire feishu agent channel auto configuration"
```

---

## 自查清单

- 设计目标“官方 Java Channel SDK WebSocket 长连接”：Task 6 覆盖。
- 监听 `im.message.receive_v1` 并解析 @机器人消息：Task 1 和 Task 5 覆盖。
- 普通文本、流式分段、按钮卡片回复：Task 2 和 Task 6 覆盖。
- 飞书 UserId 与内部账号映射、数据权限上下文：Task 3 覆盖。
- 消息全链路日志落库和异常记录：Task 4 和 Task 5 覆盖。
- 表不存在才创建、字段缺失才补齐：Task 4 覆盖。
- 自动装配和 `feishu.agent.channel.*` 配置：Task 6 覆盖。
- 计划未包含前端页面和真实大模型推理，符合非目标。
