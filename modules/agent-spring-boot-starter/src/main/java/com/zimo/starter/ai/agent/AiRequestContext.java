package com.zimo.starter.ai.agent;

/**
 * 请求拦截上下文：一次用户消息进入智能体前的快照。
 *
 * @param sessionId 会话 ID（可空）
 * @param agentId   智能体 ID（可空）
 * @param profile   路由/显式 profile（可空；旧版兼容链路可能为 null）
 */
public record AiRequestContext(String sessionId, String agentId, AiAgentProfile profile) {

    public static AiRequestContext of(String sessionId, String agentId, AiAgentProfile profile) {
        return new AiRequestContext(sessionId, agentId, profile);
    }
}
