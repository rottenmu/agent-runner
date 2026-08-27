package com.zimo.starter.ai.chat;

public record AiChatResponse(boolean success, String content, String errorMessage) {
    public static AiChatResponse ok(String content) {
        return new AiChatResponse(true, content, null);
    }

    public static AiChatResponse fail(String errorMessage) {
        return new AiChatResponse(false, null, errorMessage);
    }
}
