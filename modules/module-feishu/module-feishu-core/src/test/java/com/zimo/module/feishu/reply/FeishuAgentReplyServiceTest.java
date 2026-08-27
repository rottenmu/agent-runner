package com.zimo.module.feishu.reply;

import com.zimo.module.feishu.log.FeishuMessageLogEntity;
import com.zimo.module.feishu.log.FeishuMessageLogService;
import com.zimo.module.feishu.log.FeishuMessageLogStage;
import com.zimo.module.feishu.message.FeishuMessageResponse;
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

        FeishuMessageResponse response = service.replyText("msg_1", "received");

        assertThat(response.isSuccess()).isTrue();
        assertThat(client.calls).containsExactly("replyText:msg_1:received");
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

    @Test
    void recordsFailedTextReply() {
        CapturingLogService logService = new CapturingLogService();
        FeishuAgentReplyService service = new FeishuAgentReplyService(new FailingReplyClient(), logService);

        FeishuMessageResponse response = service.replyText("msg_1", "received");

        assertThat(response.isSuccess()).isFalse();
        assertThat(logService.records).hasSize(1);
        FeishuMessageLogEntity record = logService.records.get(0);
        assertThat(record.getMessageId()).isEqualTo("msg_1");
        assertThat(record.getReplyType()).isEqualTo("text");
        assertThat(record.getStage()).isEqualTo(FeishuMessageLogStage.REPLIED.name());
        assertThat(record.getSuccess()).isZero();
        assertThat(record.getErrorCode()).isEqualTo(400);
        assertThat(record.getErrorMessage()).isEqualTo("send failed");
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

    private static class FailingReplyClient extends CapturingReplyClient {
        @Override
        public FeishuMessageResponse replyText(String messageId, String text) {
            return FeishuMessageResponse.failure(400, "send failed");
        }
    }

    private static class CapturingLogService extends FeishuMessageLogService {
        private final List<FeishuMessageLogEntity> records = new ArrayList<>();

        private CapturingLogService() {
            super(null, true);
        }

        @Override
        public void record(FeishuMessageLogEntity entity) {
            records.add(entity);
        }
    }
}
