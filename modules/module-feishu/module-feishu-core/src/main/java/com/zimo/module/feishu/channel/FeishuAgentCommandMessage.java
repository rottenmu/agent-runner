package com.zimo.module.feishu.channel;

import java.util.Map;
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
    private final Map<String, Object> attributes;

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
        this(messageId, chatId, chatType, tenantKey, senderUserId, senderOpenId, senderUnionId,
                rawText, commandText, messageType, mentionedBot, Map.of());
    }

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
            boolean mentionedBot,
            Map<String, Object> attributes) {
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
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
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

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public boolean hasCommandText() {
        return commandText != null && !commandText.trim().isEmpty();
    }

    public String getStableSenderIdentity() {
        String userId = clean(senderUserId);
        if (userId != null) {
            return userId;
        }
        String openId = clean(senderOpenId);
        if (openId != null) {
            return "open:" + openId;
        }
        String unionId = clean(senderUnionId);
        if (unionId != null) {
            return "union:" + unionId;
        }
        String fallbackMessageId = clean(messageId);
        return fallbackMessageId == null ? "" : "message:" + fallbackMessageId;
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
                && Objects.equals(messageType, that.messageType)
                && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, chatId, chatType, tenantKey, senderUserId, senderOpenId,
                senderUnionId, rawText, commandText, messageType, mentionedBot, attributes);
    }

    private static String clean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
