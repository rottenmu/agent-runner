package com.zimo.framework.common.ai.event;

/**
 * Agent 事件（Agent 域）：一次模型步骤开始/结束（对应 dsh step/start · step/end）。
 *
 * @param sessionId 会话 ID
 * @param agentId   智能体 ID
 * @param phase     begin / end
 * @param prompt    步骤提示（begin）
 * @param response  步骤响应（end）
 * @param status    ok / error
 */
public record AgentStepEvent(
        String sessionId,
        String agentId,
        String phase,
        String prompt,
        String response,
        String status,
        long ts) {
}
