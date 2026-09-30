package com.zimo.framework.ai.observ;

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
        stepFor(CURRENT_TRACE.get(), stepType, name, inputJson, outputJson, latencyMs, status);
    }

    /**
     * 记录链路步骤（带结构化属性）。
     *
     * @param attributes 结构化属性；为 {@code null} 时等价于不带属性的重载
     */
    public static void step(String stepType, String name, String inputJson, String outputJson,
                            long latencyMs, String status, Map<String, Object> attributes) {
        stepFor(CURRENT_TRACE.get(), stepType, name, inputJson, outputJson, latencyMs, status,
                attributes);
    }

    /**
     * 按显式 traceId 记录链路步骤（跨线程场景）。
     *
     * <p><b>为什么需要这个重载</b>：{@link #step} 从 {@link ThreadLocal} 取 traceId，
     * 但 AgentScope 的模型调用/工具执行运行在 Reactor 调度线程上，
     * {@code ThreadLocal} 在那个线程里是空的，于是 {@code step} 会直接 return——
     * 这正是「链路里只有外层 intent/generation，中间全空白」的根因。
     * 中间件从 {@code RuntimeContext} 拿到 traceId 后，用本方法跨线程写入。</p>
     *
     * @param traceId 链路 ID；为 {@code null} 时不记录（不伪造 ID）
     */
    public static void stepFor(String traceId, String stepType, String name, String inputJson,
                               String outputJson, long latencyMs, String status) {
        stepFor(traceId, stepType, name, inputJson, outputJson, latencyMs, status, null);
    }

    /**
     * 按显式 traceId 记录链路步骤（带结构化属性）。
     *
     * <p>属性通道存在的理由：{@code inputJson} 是自由文本，调用方想表达「可聚合的数值/布尔」
     * （召回条数、是否截断、是否异步）时只能塞 JSON，而导出侧不解析 JSON ——
     * 结果就是属性在 span 上根本不存在。详见
     * {@link TraceObserver#onStep(String, int, String, String, String, String, long, String, Map)}。</p>
     *
     * @param attributes 结构化属性；为 {@code null} 表示不带属性
     */
    public static void stepFor(String traceId, String stepType, String name, String inputJson,
                               String outputJson, long latencyMs, String status,
                               Map<String, Object> attributes) {
        if (traceId == null) {
            return;
        }
        collectPlanStep(traceId, stepType, name);
        int seq = STEP_SEQ.computeIfAbsent(traceId, t -> new AtomicInteger(0)).incrementAndGet();
        for (TraceObserver observer : OBSERVERS) {
            try {
                observer.onStep(traceId, seq, stepType, name, inputJson, outputJson, latencyMs,
                        status, attributes);
            } catch (Exception ignored) {
            }
        }
    }

    /* ---------------- 计划步骤旁路缓冲（M4-2b） ---------------- */

    /**
     * 本轮链路里出现过的计划步骤（按 traceId 收集）。
     *
     * <p><b>为什么在采集器里旁路攒一份</b>：计划模式的经验回写需要「这次计划分几步」，
     * 而步骤原本只落进观测通道（DB）。回写发生在请求线程、链路收尾之前，
     * 此刻从 DB 反查既多一次 IO，又要让 {@code framework-ai} 反向依赖观测模块 ——
     * 而框架层不能依赖业务模块。于是在唯一的 span 入口顺手攒一份，
     * 生命周期与链路对齐（{@link #endFor} 时清理，不跨请求泄漏）。</p>
     */
    private static final Map<String, List<String>> PLAN_STEPS = new ConcurrentHashMap<>();

    /** 是否为计划类步骤：与 {@code ObservEventBridge.isPlanTool} 的归类口径保持一致。 */
    private static boolean isPlanStep(String stepType, String name) {
        if ("plan".equals(stepType)) {
            return true;
        }
        return name != null && (name.startsWith("plan_")
                || name.startsWith("todo_write") || name.contains("plan_"));
    }

    private static void collectPlanStep(String traceId, String stepType, String name) {
        try {
            if (!isPlanStep(stepType, name)) {
                return;
            }
            String step = name == null ? stepType : name.trim();
            if (step.isEmpty()) {
                return;
            }
            List<String> steps = PLAN_STEPS.computeIfAbsent(
                    traceId, t -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()));
            // 去重：同一个工具在 begin/ok 两个相位各报一次，留痕只需要一次
            if (!steps.contains(step)) {
                steps.add(step);
            }
        } catch (RuntimeException ignored) {
            // 旁路缓冲失败不得影响主流程
        }
    }

    /**
     * 取本轮链路已收集的计划步骤。
     *
     * @param traceId 链路 ID
     * @return 步骤名列表；无计划步骤或 traceId 为空时返回空列表（不返回 {@code null}）
     */
    public static List<String> planStepsOf(String traceId) {
        if (traceId == null) {
            return List.of();
        }
        List<String> steps = PLAN_STEPS.get(traceId);
        if (steps == null) {
            return List.of();
        }
        synchronized (steps) {
            return List.copyOf(steps);
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
        endFor(traceId, status, prompt, response, tokens, latencyMs);
    }

    /**
     * 按显式 traceId 结束链路（支持嵌套链路）。
     *
     * <p><b>为什么需要这个重载</b>：{@link #end} 从 {@link ThreadLocal} 取 traceId，
     * 但一条请求里可能<b>嵌套存在多条链路</b>——{@code AiAgentService} 的
     * {@code chatWithHistory} 与 {@code chat} 两个入口各自会
     * {@code begin()} 一次，{@code AiSkillCallController} 又可能另起一条。
     * 此时 ThreadLocal 只记得<b>最后 begin 的那个</b> traceId，
     * 于是先 begin 的链路永远等不到 {@code end}，其观测实现里的聚合容器
     * （如 {@code GenAiTraceObserver} 的 {@code rootSpans}/{@code traceSpans} Map）
     * <b>会随请求量持续泄漏，永不释放</b>。</p>
     *
     * <p>用本重载可以让每个调用方用自己的 traceId 精确收尾。为兼容既有的
     * ThreadLocal 语义，仅当传入的 traceId 与当前线程持有的相同时才清理
     * {@code CURRENT_TRACE}，避免外层链路被内层误清。</p>
     *
     * @param traceId 链路 ID；为 {@code null} 时不通知 observer（不伪造 ID）
     * @param status 状态（ok/failed）
     * @param prompt 用户输入
     * @param response 最终回复
     * @param tokens Token 消耗
     * @param latencyMs 总耗时
     */
    public static void endFor(String traceId, String status, String prompt, String response,
                              int tokens, long latencyMs) {
        if (traceId == null) {
            return;
        }
        if (traceId.equals(CURRENT_TRACE.get())) {
            CURRENT_TRACE.remove();
        }
        STEP_SEQ.remove(traceId);
        // 计划步骤旁路缓冲与链路同生命周期：不清理会随请求量持续泄漏
        PLAN_STEPS.remove(traceId);
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
