package com.zimo.module.feishu.channel;

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
                                "content", "{\"text\":\"@_user_2 query project risk\"}",
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
        assertThat(message.getRawText()).isEqualTo("@_user_2 query project risk");
        assertThat(message.getCommandText()).isEqualTo("query project risk");
        assertThat(message.getMessageType()).isEqualTo("text");
        assertThat(message.isMentionedBot()).isTrue();
    }

    @Test
    void ignoresNonMentionMessages() {
        Map<String, Object> payload = Map.of(
                "event", Map.of("message", Map.of(
                        "message_id", "msg_2",
                        "chat_type", "group",
                        "message_type", "text",
                        "content", "{\"text\":\"plain group message\"}"
                ))
        );

        assertThat(parser.parse(payload)).isEmpty();
    }

    @Test
    void parsesDirectTextMessageWithoutMentionIntoCommandMessage() {
        Map<String, Object> payload = Map.of(
                "header", Map.of("event_type", "im.message.receive_v1", "tenant_key", "tenant_1"),
                "event", Map.of(
                        "sender", Map.of("sender_id", Map.of(
                                "user_id", "user_1",
                                "open_id", "open_1",
                                "union_id", "union_1"
                        )),
                        "message", Map.of(
                                "message_id", "msg_4",
                                "chat_id", "chat_4",
                                "chat_type", "p2p",
                                "message_type", "text",
                                "content", "{\"text\":\"hello agent\"}"
                        )
                )
        );

        FeishuAgentCommandMessage message = parser.parse(payload).orElseThrow();

        assertThat(message.getMessageId()).isEqualTo("msg_4");
        assertThat(message.getChatId()).isEqualTo("chat_4");
        assertThat(message.getChatType()).isEqualTo("p2p");
        assertThat(message.getTenantKey()).isEqualTo("tenant_1");
        assertThat(message.getSenderUserId()).isEqualTo("user_1");
        assertThat(message.getCommandText()).isEqualTo("hello agent");
        assertThat(message.isMentionedBot()).isFalse();
    }

    @Test
    void parsesSdkConvertedCamelCaseTextMessageIntoCommandMessage() {
        Map<String, Object> payload = Map.of(
                "header", Map.of("eventType", "im.message.receive_v1", "tenantKey", "tenant_1"),
                "event", Map.of(
                        "sender", Map.of("senderId", Map.of(
                                "userId", "user_1",
                                "openId", "open_1",
                                "unionId", "union_1"
                        )),
                        "message", Map.of(
                                "messageId", "msg_5",
                                "chatId", "chat_5",
                                "chatType", "p2p",
                                "messageType", "text",
                                "content", "{\"text\":\"sdk hello\"}"
                        )
                )
        );

        FeishuAgentCommandMessage message = parser.parse(payload).orElseThrow();

        assertThat(message.getMessageId()).isEqualTo("msg_5");
        assertThat(message.getChatId()).isEqualTo("chat_5");
        assertThat(message.getChatType()).isEqualTo("p2p");
        assertThat(message.getTenantKey()).isEqualTo("tenant_1");
        assertThat(message.getSenderUserId()).isEqualTo("user_1");
        assertThat(message.getSenderOpenId()).isEqualTo("open_1");
        assertThat(message.getSenderUnionId()).isEqualTo("union_1");
        assertThat(message.getCommandText()).isEqualTo("sdk hello");
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

    @Test
    void parsesExcelFileMessageIntoCommandMessageWithFileAttributes() {
        Map<String, Object> payload = Map.of(
                "header", Map.of("event_type", "im.message.receive_v1", "tenant_key", "tenant_1"),
                "event", Map.of(
                        "sender", Map.of("sender_id", Map.of("user_id", "user_1")),
                        "message", Map.of(
                                "message_id", "msg_file",
                                "chat_id", "chat_1",
                                "chat_type", "p2p",
                                "message_type", "file",
                                "content", "{\"file_key\":\"file_v2_xxx\",\"file_name\":\"business-order.xlsx\"}"
                        )
                )
        );

        FeishuAgentCommandMessage message = parser.parse(payload).orElseThrow();

        assertThat(message.getCommandText()).isEqualTo("\u89e3\u6790Excel\u751f\u6210\u9879\u76ee");
        assertThat(message.getAttributes())
                .containsEntry("fileKey", "file_v2_xxx")
                .containsEntry("sourceName", "business-order.xlsx")
                .containsEntry("messageType", "file");
    }

    @Test
    void parsesGroupExcelFileMessageWithoutMention() {
        Map<String, Object> payload = Map.of(
                "event", Map.of(
                        "sender", Map.of("sender_id", Map.of("user_id", "user_1")),
                        "message", Map.of(
                                "message_id", "msg_file",
                                "chat_id", "chat_1",
                                "chat_type", "group",
                                "message_type", "file",
                                "content", "{\"file_key\":\"file_v2_xxx\",\"file_name\":\"business-order.xls\"}"
                        )
                )
        );

        FeishuAgentCommandMessage message = parser.parse(payload).orElseThrow();

        assertThat(message.getCommandText()).isEqualTo("\u89e3\u6790Excel\u751f\u6210\u9879\u76ee");
        assertThat(message.isMentionedBot()).isFalse();
    }
}
