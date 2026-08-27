package com.zimo.module.feishu.message;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeishuMessageServiceTest {

    @Test
    void sendsTextMessagesToSupportedReceiveIdTypes() {
        CapturingMessageClient client = new CapturingMessageClient();
        FeishuMessageService service = new FeishuMessageService(client);

        service.sendTextMessage(FeishuReceiveIdType.USER_ID, "user_1", "用户消息");
        service.sendTextMessage(FeishuReceiveIdType.OPEN_ID, "open_1", "开放消息");
        service.sendTextMessage(FeishuReceiveIdType.CHAT_ID, "chat_1", "群消息");

        assertThat(client.requests)
                .extracting(FeishuTextMessageRequest::getReceiveIdType)
                .containsExactly("user_id", "open_id", "chat_id");
        assertThat(client.requests)
                .extracting(FeishuTextMessageRequest::getReceiveId)
                .containsExactly("user_1", "open_1", "chat_1");
        assertThat(client.requests)
                .extracting(FeishuTextMessageRequest::getText)
                .containsExactly("用户消息", "开放消息", "群消息");
    }

    @Test
    void rejectsBlankText() {
        FeishuMessageService service = new FeishuMessageService(new CapturingMessageClient());

        assertThatThrownBy(() -> service.sendTextMessage(FeishuReceiveIdType.USER_ID, "user_1", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }

    private static class CapturingMessageClient implements FeishuMessageClient {
        private final List<FeishuTextMessageRequest> requests = new ArrayList<>();

        @Override
        public FeishuMessageResponse sendText(FeishuTextMessageRequest request) {
            requests.add(request);
            return FeishuMessageResponse.success("message_" + requests.size());
        }
    }
}
