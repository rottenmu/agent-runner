package com.zimo.framework.common.ai.event;

/**
 * 会话事件（会话域）：一轮对话（user + assistant）已追加。
 *
 * @param sessionId   会话 ID
 * @param userMessage 用户消息
 * @param assistantMessage 助手消息
 * @param ts          时间戳（ms）
 */
public record ConversationTurnEvent(String sessionId, String userMessage, String assistantMessage, long ts) {
}
