package com.zimo.starter.ai.chat;

@FunctionalInterface
public interface AiChatClient {
    AiChatResponse chat(AiChatRequest request);
}
