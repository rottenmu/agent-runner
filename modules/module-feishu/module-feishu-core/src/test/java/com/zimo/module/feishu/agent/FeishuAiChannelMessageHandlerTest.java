package com.zimo.module.feishu.agent;

import com.zimo.module.feishu.channel.FeishuAgentCommandMessage;
import com.zimo.module.feishu.config.FeishuConfigProvider;
import com.zimo.module.feishu.config.FeishuRuntimeConfig;
import com.zimo.module.feishu.file.FeishuFileClient;
import com.zimo.module.feishu.file.FeishuFileDownloadResult;
import com.zimo.module.feishu.message.FeishuMessageResponse;
import com.zimo.module.feishu.reply.FeishuAgentReplyClient;
import com.zimo.module.feishu.reply.FeishuAgentReplyService;
import com.zimo.starter.ai.AiAgentProperties;
import com.zimo.starter.ai.AiAgentService;
import com.zimo.starter.ai.agent.AiAgentProfile;
import com.zimo.starter.ai.agent.AiHarnessAgentFactory;
import com.zimo.starter.ai.agent.AiHarnessAgentRegistry;
import com.zimo.starter.ai.agent.AiHarnessAgentRouter;
import com.zimo.starter.ai.agent.AiHarnessSessionKeyFactory;
import com.zimo.starter.ai.channel.AiChannelAgentBinding;
import com.zimo.starter.ai.channel.AiChannelHandler;
import com.zimo.starter.ai.channel.AiChannelMessage;
import com.zimo.starter.ai.channel.AiChannelReply;
import com.zimo.starter.ai.chat.AiChatResponse;
import com.zimo.starter.ai.runtime.AiAgentRuntime;
import com.zimo.starter.ai.runtime.AiAgentRuntimeStatus;
import com.zimo.starter.ai.skill.AiSkill;
import com.zimo.starter.ai.skill.AiSkillRegistry;
import com.zimo.starter.ai.skill.AiSkillResult;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeishuAiChannelMessageHandlerTest {
    @Test
    void delegatesFeishuCommandToAiChannelAndRepliesText() {
        AtomicReference<AiChannelMessage> capturedMessage = new AtomicReference<>();
        AiChannelHandler aiChannelHandler = mock(AiChannelHandler.class);
        when(aiChannelHandler.handle(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            AiChannelMessage message = invocation.getArgument(0);
            capturedMessage.set(message);
            return AiChannelReply.text("AI reply: " + message.text());
        });
        RecordingReplyClient replyClient = new RecordingReplyClient();
        FeishuAiChannelMessageHandler handler = new FeishuAiChannelMessageHandler(aiChannelHandler);

        handler.handle(commandMessage("query project risk"), new FeishuAgentReplyService(replyClient));

        assertThat(capturedMessage.get().channel()).isEqualTo("feishu");
        assertThat(capturedMessage.get().tenantId()).isEqualTo("tenant_1");
        assertThat(capturedMessage.get().userId()).isEqualTo("user_1");
        assertThat(capturedMessage.get().conversationId()).isEqualTo("chat_1");
        assertThat(capturedMessage.get().messageId()).isEqualTo("msg_1");
        assertThat(capturedMessage.get().text()).isEqualTo("query project risk");
        assertThat(replyClient.text).isEqualTo("AI reply: query project risk");
        assertThat(replyClient.cardJson).isNull();
    }

    @Test
    void usesOpenIdForAiSessionWhenFeishuUserIdIsBlank() {
        AtomicReference<AiChannelMessage> capturedMessage = new AtomicReference<>();
        AiChannelHandler aiChannelHandler = mock(AiChannelHandler.class);
        when(aiChannelHandler.handle(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            AiChannelMessage message = invocation.getArgument(0);
            capturedMessage.set(message);
            return AiChannelReply.text("ok");
        });
        FeishuAiChannelMessageHandler handler = new FeishuAiChannelMessageHandler(aiChannelHandler);

        handler.handle(commandMessage("query", " ", "open_1", "union_1"),
                new FeishuAgentReplyService(new RecordingReplyClient()));

        assertThat(capturedMessage.get().userId()).isEqualTo("open:open_1");
        assertThat(capturedMessage.get().sessionId()).isEqualTo("feishu:tenant_1:chat_1:open:open_1");
    }

    @Test
    void supportsSkillCommandThroughAiChannelHandler() throws Exception {
        AiChannelHandler aiChannelHandler = new AiChannelHandler(
                agentService(),
                new AiSkillRegistry(List.of(new EchoSkill())),
                channel -> java.util.Optional.of(new AiAgentProfile(
                        "skill-agent",
                        "tenant_1",
                        "skill-agent",
                        "qwen-plus",
                        "persona",
                        List.of("echo"),
                        true)));
        RecordingReplyClient replyClient = new RecordingReplyClient();
        FeishuAiChannelMessageHandler handler = new FeishuAiChannelMessageHandler(aiChannelHandler);

        handler.handle(commandMessage(skillCommand("echo text='feishu message'")), new FeishuAgentReplyService(replyClient));

        assertThat(replyClient.text).isEqualTo("skill result: feishu message");
    }

    @Test
    void repliesProjectCardWhenAiReplyIsProjectCardPayload() {
        FeishuAgentCommandMessage message = commandMessage("list projects");
        AiChannelHandler aiChannelHandler = mock(AiChannelHandler.class);
        when(aiChannelHandler.handle(org.mockito.ArgumentMatchers.any()))
                .thenReturn(AiChannelReply.text("""
                        {
                          "type": "project_view",
                          "title": "Project List",
                          "headers": [{"key": "business_project_code", "label": "Project Code"}],
                          "rows": [{"business_project_code": "XJ00120260704"}],
                          "buttons": [{"text": "Refresh", "action": "refresh_project_view", "value": {"viewType": "main"}}]
                        }
                        """));
        RecordingReplyClient replyClient = new RecordingReplyClient();
        FeishuAiChannelMessageHandler handler = new FeishuAiChannelMessageHandler(
                aiChannelHandler,
                new FeishuProjectCardRenderer());

        handler.handle(message, new FeishuAgentReplyService(replyClient));

        assertThat(replyClient.text).isNull();
        assertThat(replyClient.cardJson)
                .contains("\"schema\":\"2.0\"")
                .contains("\"tag\":\"table\"")
                .contains("XJ00120260704");
    }

    @Test
    void downloadsFileMessageAndPassesBase64ToAiChannel() {
        AtomicReference<AiChannelMessage> capturedMessage = new AtomicReference<>();
        AiChannelHandler aiChannelHandler = mock(AiChannelHandler.class);
        when(aiChannelHandler.handle(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            AiChannelMessage message = invocation.getArgument(0);
            capturedMessage.set(message);
            return AiChannelReply.text("ok");
        });
        FeishuFileClient fileClient = (messageId, fileKey) -> FeishuFileDownloadResult.success(
                "business-order.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "excel-bytes".getBytes(StandardCharsets.UTF_8));
        FeishuAiChannelMessageHandler handler = new FeishuAiChannelMessageHandler(
                aiChannelHandler,
                new FeishuProjectCardRenderer(),
                fileClient);

        handler.handle(fileMessage(), new FeishuAgentReplyService(new RecordingReplyClient()));

        assertThat(capturedMessage.get().attributes())
                .containsEntry("sourceName", "business-order.xlsx")
                .containsEntry("fileBase64", Base64.getEncoder().encodeToString("excel-bytes".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void addsTrimmedActiveConfigAgentIdToAiMessageAttributes() {
        AtomicReference<AiChannelMessage> capturedMessage = new AtomicReference<>();
        AiChannelHandler aiChannelHandler = capturingHandler(capturedMessage);
        FeishuConfigProvider configProvider = configProvider("  a-bound  ");
        FeishuAiChannelMessageHandler handler = new FeishuAiChannelMessageHandler(
                aiChannelHandler,
                new FeishuProjectCardRenderer(),
                null,
                configProvider);

        handler.handle(commandMessage("hello"), new FeishuAgentReplyService(new RecordingReplyClient()));

        assertThat(capturedMessage.get().attributes())
                .containsEntry("agentId", "a-bound")
                .hasEntrySatisfying(
                        AiChannelAgentBinding.ATTRIBUTE_NAME,
                        value -> assertThat(value).isEqualTo(
                                new AiChannelAgentBinding("tenant_1", "a-bound")));
    }

    @Test
    void omitsBindingWhenActiveConfigTenantDoesNotMatchEventTenant() {
        AtomicReference<AiChannelMessage> capturedMessage = new AtomicReference<>();
        AiChannelHandler aiChannelHandler = capturingHandler(capturedMessage);
        FeishuAiChannelMessageHandler handler = new FeishuAiChannelMessageHandler(
                aiChannelHandler,
                new FeishuProjectCardRenderer(),
                null,
                configProvider("other-tenant", "a-bound"));

        handler.handle(commandMessage("hello"), new FeishuAgentReplyService(new RecordingReplyClient()));

        assertThat(capturedMessage.get().attributes())
                .doesNotContainKeys("agentId", AiChannelAgentBinding.ATTRIBUTE_NAME);
    }

    @Test
    void omitsAgentIdAttributeWhenActiveConfigIsNotBound() {
        AtomicReference<AiChannelMessage> capturedMessage = new AtomicReference<>();
        AiChannelHandler aiChannelHandler = capturingHandler(capturedMessage);
        FeishuAiChannelMessageHandler handler = new FeishuAiChannelMessageHandler(
                aiChannelHandler,
                new FeishuProjectCardRenderer(),
                null,
                configProvider(" "));

        handler.handle(commandMessage("hello"), new FeishuAgentReplyService(new RecordingReplyClient()));

        assertThat(capturedMessage.get().attributes()).doesNotContainKey("agentId");
    }

    private static AiChannelHandler capturingHandler(AtomicReference<AiChannelMessage> capturedMessage) {
        AiChannelHandler aiChannelHandler = mock(AiChannelHandler.class);
        when(aiChannelHandler.handle(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            capturedMessage.set(invocation.getArgument(0));
            return AiChannelReply.text("ok");
        });
        return aiChannelHandler;
    }

    private static FeishuConfigProvider configProvider(String agentId) {
        return configProvider("tenant_1", agentId);
    }

    private static FeishuConfigProvider configProvider(String configTenantKey, String agentId) {
        return new FeishuConfigProvider() {
            @Override
            public FeishuRuntimeConfig getActiveConfig() {
                return null;
            }

            @Override
            public String getActiveAgentId() {
                return agentId;
            }

            @Override
            public String getActiveAgentId(String eventTenantKey) {
                return configTenantKey.equals(eventTenantKey) ? agentId : null;
            }
        };
    }
    private static FeishuAgentCommandMessage commandMessage(String commandText) {
        return commandMessage(commandText, "user_1", "open_1", "union_1");
    }

    private static FeishuAgentCommandMessage commandMessage(
            String commandText,
            String senderUserId,
            String senderOpenId,
            String senderUnionId) {
        return new FeishuAgentCommandMessage(
                "msg_1",
                "chat_1",
                "group",
                "tenant_1",
                senderUserId,
                senderOpenId,
                senderUnionId,
                "@" + commandText,
                commandText,
                "text",
                true);
    }

    private static FeishuAgentCommandMessage fileMessage() {
        return new FeishuAgentCommandMessage(
                "msg_file",
                "chat_1",
                "p2p",
                "tenant_1",
                "user_1",
                "open_1",
                "union_1",
                "",
                "\u89e3\u6790Excel\u751f\u6210\u9879\u76ee",
                "file",
                false,
                Map.of("fileKey", "file_v2_xxx", "sourceName", "business-order.xlsx"));
    }

    private static String skillCommand(String command) throws Exception {
        var field = AiChannelHandler.class.getDeclaredField("SKILL_PREFIX");
        field.setAccessible(true);
        return field.get(null) + command;
    }

    private static AiAgentService agentService() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setName("openclaw-lite");
        AiAgentRuntime runtime = new AiAgentRuntime(
                "openclaw-lite",
                "qwen-plus",
                "dashscope_chat",
                List.of(),
                AiAgentRuntimeStatus.READY,
                "ready");
        AiHarnessAgentRegistry registry = new AiHarnessAgentRegistry(
                new AiHarnessAgentFactory(properties, new AiSkillRegistry(List.of())),
                properties);
        return new AiAgentService(
                properties,
                new AiSkillRegistry(List.of()),
                runtime,
                request -> AiChatResponse.ok("chat reply"),
                new AiHarnessAgentRouter(null, (AiAgentProfile) null),
                registry,
                new AiHarnessSessionKeyFactory(),
                null,
                null,
                null,
                null,
                java.util.List.of() /* middlewares */);
    }

    private static class EchoSkill implements AiSkill {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "echo";
        }

        @Override
        public boolean readOnly() {
            return true;
        }

        @Override
        public AiSkillResult call(Map<String, Object> arguments) {
            return AiSkillResult.ok("skill result: " + arguments.get("text"));
        }
    }

    private static class RecordingReplyClient implements FeishuAgentReplyClient {
        private String text;
        private String cardJson;

        @Override
        public FeishuMessageResponse replyText(String messageId, String text) {
            this.text = text;
            return FeishuMessageResponse.success("reply_" + messageId);
        }

        @Override
        public FeishuMessageResponse updateText(String messageId, String text) {
            this.text = text;
            return FeishuMessageResponse.success(messageId);
        }

        @Override
        public FeishuMessageResponse replyCard(String messageId, String cardJson) {
            this.cardJson = cardJson;
            return FeishuMessageResponse.success("card_" + messageId);
        }
    }
}
