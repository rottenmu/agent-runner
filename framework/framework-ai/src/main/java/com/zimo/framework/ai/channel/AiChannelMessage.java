package com.zimo.framework.ai.channel;

import java.util.Map;

public record AiChannelMessage(
        String channel,
        String tenantId,
        String userId,
        String conversationId,
        String messageId,
        String text,
        Map<String, Object> attributes) {

    public AiChannelMessage {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static AiChannelMessage of(
            String channel,
            String tenantId,
            String userId,
            String conversationId,
            String messageId,
            String text) {
        return of(channel, tenantId, userId, conversationId, messageId, text, Map.of());
    }

    public static AiChannelMessage of(
            String channel,
            String tenantId,
            String userId,
            String conversationId,
            String messageId,
            String text,
            Map<String, Object> attributes) {
        return new AiChannelMessage(channel, tenantId, userId, conversationId, messageId, text, attributes);
    }

    public String sessionId() {
        return join(channel, tenantId, conversationId, userId);
    }

    private static String join(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            String value = part == null ? "" : part.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(":");
            }
            builder.append(value);
        }
        return builder.toString();
    }
}
