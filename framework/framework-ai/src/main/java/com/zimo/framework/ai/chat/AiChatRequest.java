package com.zimo.framework.ai.chat;

import com.zimo.module.agentmemory.chat.AiChatMessage;
import java.util.List;

public record AiChatRequest(
        String agentName,
        String message,
        String sessionId,
        String modelName,
        double temperature,
        int maxTokens,
        String systemPrompt,
        List<AiChatMessage> history) {

    public AiChatRequest(
            String agentName,
            String message,
            String sessionId,
            String modelName,
            double temperature,
            int maxTokens) {
        this(agentName, message, sessionId, modelName, temperature, maxTokens, null, List.of());
    }

    public AiChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
