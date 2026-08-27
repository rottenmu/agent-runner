package com.zimo.starter.ai.observ;

/**
 * Trace 观测实现接口：由持久化模块实现并注册到 {@link TraceCollector}。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public interface TraceObserver {

    /**
     * 链路开始。
     *
     * @param traceId 链路 ID
     * @param sessionId 会话
     * @param agentId 智能体
     * @param agentName 智能体名称
     * @param intent 意图
     * @param triggerType 触发类型
     */
    void onBegin(String traceId, String sessionId, String agentId, String agentName,
                 String intent, String triggerType);

    /**
     * 链路步骤。
     *
     * @param traceId 链路 ID
     * @param seq 步骤序号
     * @param stepType 步骤类型（intent/knowledge_retrieval/tool_call/prompt/generation）
     * @param name 步骤名
     * @param inputJson 输入（JSON 或文本）
     * @param outputJson 输出
     * @param latencyMs 耗时
     * @param status 状态
     */
    void onStep(String traceId, int seq, String stepType, String name,
                String inputJson, String outputJson, long latencyMs, String status);

    /**
     * 链路结束。
     *
     * @param traceId 链路 ID
     * @param status 状态（ok/failed）
     * @param prompt 用户输入（原始消息）
     * @param response 最终回复
     * @param tokens Token 消耗
     * @param latencyMs 总耗时
     */
    void onEnd(String traceId, String status, String prompt, String response, int tokens, long latencyMs);
}
