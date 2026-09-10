package com.zimo.framework.ai.intent;

import java.util.List;

/**
 * 会话上下文：意图判断所需的轻量对话信息（不依赖 AgentScope 底层）。
 *
 * @param tenantId  租户
 * @param userId    用户
 * @param sessionId 会话
 * @param history   历史消息（最近在前，供指代消解 / LLM 少样本参考）
 * @author WorkBuddy
 * @since 2026-08-15
 */
public record ConversationContext(
        String tenantId,
        String userId,
        String sessionId,
        List<String> history) {

    public static ConversationContext of(String tenantId, String userId, String sessionId) {
        return new ConversationContext(tenantId, userId, sessionId, List.of());
    }

    public static ConversationContext of(String tenantId, String userId, String sessionId, List<String> history) {
        return new ConversationContext(tenantId, userId, sessionId,
                history == null ? List.of() : List.copyOf(history));
    }
}
