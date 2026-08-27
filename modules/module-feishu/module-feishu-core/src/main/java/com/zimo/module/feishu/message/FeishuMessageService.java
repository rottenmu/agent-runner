package com.zimo.module.feishu.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;

public class FeishuMessageService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FeishuMessageClient messageClient;

    public FeishuMessageService(FeishuMessageClient messageClient) {
        this.messageClient = Objects.requireNonNull(messageClient, "messageClient must not be null");
    }

    public FeishuMessageResponse sendTextMessage(FeishuReceiveIdType receiveIdType, String receiveId, String text) {
        if (receiveIdType == null) {
            throw new IllegalArgumentException("receiveIdType must not be null");
        }
        if (!StringUtils.hasText(receiveId)) {
            throw new IllegalArgumentException("receiveId must not be blank");
        }
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("text must not be blank");
        }

        FeishuTextMessageRequest request = new FeishuTextMessageRequest(
                receiveIdType.getApiValue(),
                receiveId,
                text,
                textContentJson(text));
        return messageClient.sendText(request);
    }

    private static String textContentJson(String text) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of("text", text));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("text content cannot be serialized", e);
        }
    }
}
