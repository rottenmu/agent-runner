package com.zimo.module.feishu.event;

public class FeishuBotMentionEvent {
    private final String messageId;
    private final String chatId;
    private final String text;
    private final String senderOpenId;
    private final String senderUserId;

    public FeishuBotMentionEvent(String messageId, String chatId, String text, String senderOpenId, String senderUserId) {
        this.messageId = messageId;
        this.chatId = chatId;
        this.text = text;
        this.senderOpenId = senderOpenId;
        this.senderUserId = senderUserId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getChatId() {
        return chatId;
    }

    public String getText() {
        return text;
    }

    public String getSenderOpenId() {
        return senderOpenId;
    }

    public String getSenderUserId() {
        return senderUserId;
    }
}
