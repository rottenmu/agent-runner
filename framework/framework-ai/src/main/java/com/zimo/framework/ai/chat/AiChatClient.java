package com.zimo.framework.ai.chat;

@FunctionalInterface
public interface AiChatClient {
    AiChatResponse chat(AiChatRequest request);
}
