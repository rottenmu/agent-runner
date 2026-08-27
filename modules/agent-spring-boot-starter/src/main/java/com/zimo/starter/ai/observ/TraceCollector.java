package com.zimo.starter.ai.observ;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 执行链路 Trace 采集器：跨模块收集意图识别、知识召回、工具调用、生成等步骤。
 *
 * <p>通过 {@link TraceObserver} 将事件转发给持久化实现（如 module-ai 观测中心写库）。
 * 使用 {@link ThreadLocal} 关联当前线程的执行链路。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public final class TraceCollector {

    private static final List<TraceObserver> OBSERVERS = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<String> CURRENT_TRACE = new ThreadLocal<>();
    private static final Map<String, AtomicInteger> STEP_SEQ = new ConcurrentHashMap<>();

    private TraceCollector() {
    }

    /** 注册观测实现（模块启动时调用）。 */
    public static void register(TraceObserver observer) {
        if (observer != null && !OBSERVERS.contains(observer)) {
            OBSERVERS.add(observer);
        }
    }

    /**
     * 开始一条执行链路。
     *
     * @param sessionId 会话
     * @param agentId 智能体
     * @param agentName 智能体名称
     * @param intent 意图（路由到的类型）
     * @param triggerType 触发类型
     * @return traceId
     */
    public static String begin(String sessionId, String agentId, String agentName,
                               String intent, String triggerType) {
        // hutool IdUtil.fastSimpleUUID：无横线 UUID，取前 16 位作为链路 ID
        String traceId = cn.hutool.core.util.IdUtil.fastSimpleUUID().substring(0, 16);
        CURRENT_TRACE.set(traceId);
        STEP_SEQ.put(traceId, new AtomicInteger(0));
        for (TraceObserver observer : OBSERVERS) {
            try {
                observer.onBegin(traceId, sessionId, agentId, agentName, intent, triggerType);
            } catch (Exception ignored) {
            }
        }
        return traceId;
    }

    /** 记录链路步骤。 */
    public static void step(String stepType, String name, String inputJson,
                            String outputJson, long latencyMs, String status) {
        String traceId = CURRENT_TRACE.get();
        if (traceId == null) {
            return;
        }
        int seq = STEP_SEQ.computeIfAbsent(traceId, t -> new AtomicInteger(0)).incrementAndGet();
        for (TraceObserver observer : OBSERVERS) {
            try {
                observer.onStep(traceId, seq, stepType, name, inputJson, outputJson, latencyMs, status);
            } catch (Exception ignored) {
            }
        }
    }

    /** 结束执行链路。 */
    /**
     * 结束执行链路。
     *
     * @param status 状态（ok/failed）
     * @param prompt 用户输入
     * @param response 最终回复
     * @param tokens Token 消耗
     * @param latencyMs 总耗时
     */
    public static void end(String status, String prompt, String response, int tokens, long latencyMs) {
        String traceId = CURRENT_TRACE.get();
        if (traceId == null) {
            return;
        }
        CURRENT_TRACE.remove();
        STEP_SEQ.remove(traceId);
        for (TraceObserver observer : OBSERVERS) {
            try {
                observer.onEnd(traceId, status, prompt, response, tokens, latencyMs);
            } catch (Exception ignored) {
            }
        }
    }

    /** 当前线程链路 ID（无则 null）。 */
    public static String currentTraceId() {
        return CURRENT_TRACE.get();
    }
}
