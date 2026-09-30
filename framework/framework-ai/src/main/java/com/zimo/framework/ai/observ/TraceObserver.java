package com.zimo.framework.ai.observ;

import java.util.Map;

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
     * 链路步骤（带结构化属性）。
     *
     * <p><b>为什么需要这个重载</b>：{@link #onStep(String, int, String, String, String, String, long, String)}
     * 只带 {@code inputJson}/{@code outputJson} 两个自由文本槽，调用方想在 span 上表达
     * <b>可聚合的数值/布尔属性</b>（如 {@code memory.recall.count} /
     * {@code memory.recall.truncated} / {@code memory.async}）时只能把 JSON 塞进
     * input 再指望导出侧解析 —— 导出侧实际上不解析，属性等于丢失。</p>
     *
     * <p><b>默认实现刻意丢弃属性</b>：本方法是<b>向后兼容</b>的扩展点，老实现不必改动；
     * 但这也意味着「只要实现了本接口就应该覆写本方法」，否则属性会静默消失。
     * 判断某实现是否真正支持属性，看它是否覆写了本方法。</p>
     *
     * @param attributes 结构化属性（键为 OTel 语义约定名或模块自有扩展名）；
     *                   实现方应保证其中的值可被序列化，且<b>不得</b>因属性为空而丢弃 span
     */
    default void onStep(String traceId, int seq, String stepType, String name,
                        String inputJson, String outputJson, long latencyMs, String status,
                        Map<String, Object> attributes) {
        onStep(traceId, seq, stepType, name, inputJson, outputJson, latencyMs, status);
    }

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
