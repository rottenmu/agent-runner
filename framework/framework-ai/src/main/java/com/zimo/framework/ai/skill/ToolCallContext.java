package com.zimo.framework.ai.skill;

/**
 * 工具调用上下文（流水线打点/守卫/审批所需）。
 *
 * @param traceId   链路 ID（可空：非主链路调用）
 * @param sessionId 会话 ID（可空）
 * @param agentId   智能体 ID（可空）
 * @param agentName 智能体名（可空）
 */
public record ToolCallContext(String traceId, String sessionId, String agentId, String agentName) {

    public static ToolCallContext of(String traceId) {
        return new ToolCallContext(traceId, null, null, null);
    }

    public static ToolCallContext of(String traceId, String sessionId, String agentId, String agentName) {
        return new ToolCallContext(traceId, sessionId, agentId, agentName);
    }
}
