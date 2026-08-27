package com.zimo.module.feishu.channel;

import com.zimo.module.feishu.log.FeishuMessageLogEntity;
import com.zimo.module.feishu.log.FeishuMessageLogService;
import com.zimo.module.feishu.mapping.FeishuInternalUserSnapshot;
import com.zimo.module.feishu.mapping.FeishuUserMappingService;
import com.zimo.module.feishu.mapping.FeishuUserPermissionBinder;
import com.zimo.module.feishu.message.FeishuMessageResponse;
import com.zimo.module.feishu.reply.FeishuAgentReplyClient;
import com.zimo.module.feishu.reply.FeishuAgentReplyService;
import com.zimo.module.sys.enums.DataScopeEnum;
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

        assertThat(handler.commands).containsExactly("query project");
        assertThat(logService.stages).contains("RECEIVED", "PARSED", "MAPPED", "HANDLED");
    }

    @Test
    void delegatesUnboundMessageWhenHandlerAllowsGuestUser() {
        CapturingHandler handler = new GuestHandler();
        RecordingReplyClient replyClient = new RecordingReplyClient();
        FeishuChannelMessageListener listener = new FeishuChannelMessageListener(
                new StubParser(),
                new EmptyMappingService(),
                new FeishuUserPermissionBinder(),
                handler,
                new FeishuAgentReplyService(replyClient),
                new CapturingLogService()
        );

        listener.onMessage(Map.of("event", Map.of()));

        assertThat(handler.commands).containsExactly("query project");
        assertThat(replyClient.text).isNull();
    }

    private static class StubParser extends FeishuChannelMessageParser {
        @Override
        public Optional<FeishuAgentCommandMessage> parse(Map<String, Object> payload) {
            return Optional.of(new FeishuAgentCommandMessage("msg_1", "chat_1", "group", "tenant_1",
                    "user_1", "open_1", "union_1", "@bot query project", "query project", "text", true));
        }
    }

    private static class StubMappingService extends FeishuUserMappingService {
        StubMappingService() {
            super(null);
        }

        @Override
        public Optional<FeishuInternalUserSnapshot> findInternalUser(FeishuAgentCommandMessage message) {
            return Optional.of(new FeishuInternalUserSnapshot(7L, "planner", "factory_1", "factory one",
                    Set.of("pm:project:list"), DataScopeEnum.FACTORY));
        }
    }

    private static class EmptyMappingService extends FeishuUserMappingService {
        EmptyMappingService() {
            super(null);
        }

        @Override
        public Optional<FeishuInternalUserSnapshot> findInternalUser(FeishuAgentCommandMessage message) {
            return Optional.empty();
        }
    }

    private static class CapturingHandler implements FeishuAgentMessageHandler {
        private final List<String> commands = new ArrayList<>();

        @Override
        public void handle(FeishuAgentCommandMessage message, FeishuAgentReplyService replyService) {
            commands.add(message.getCommandText());
        }
    }

    private static class GuestHandler extends CapturingHandler {
        @Override
        public boolean requiresInternalAccount() {
            return false;
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

    private static class RecordingReplyClient extends NoopReplyClient {
        private String text;

        @Override
        public FeishuMessageResponse replyText(String messageId, String text) {
            this.text = text;
            return FeishuMessageResponse.success("reply_1");
        }
    }
}
