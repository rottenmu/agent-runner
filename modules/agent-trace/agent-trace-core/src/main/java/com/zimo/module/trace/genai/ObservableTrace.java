package com.zimo.module.trace.genai;

import com.zimo.framework.ai.observ.HarnessTraceMiddleware;
import com.zimo.framework.ai.observ.TraceCollector;
import io.agentscope.core.agent.RuntimeContext;
import java.util.Map;

/**
 * 可观测性统一门面（agent-trace 对外唯一入口）。
 *
 * <p><b>定位</b>：把"怎么埋点"与"埋的点怎么导出"解耦。业务侧只依赖本门面，
 * 不直接引用 {@link TraceCollector}；导出侧（OTLP / 日志 / 落库）通过实现
 * {@link GenAiSpanExporter} SPI 接入。</p>
 *
 * <h2>用法</h2>
 * <pre>{@code
 * long start = System.currentTimeMillis();
 * ObservableTrace trace = ObservableTrace.begin("agent-1", "测试助手", "conversation", "console");
 * try {
 *     trace.step(GenAiStepTypes.MODEL_CALL, "模型调用 DashScopeChatModel", "{}", "{}", 120L, "ok");
 *     trace.end("success", userText, replyText, tokens, System.currentTimeMillis() - start);
 * } catch (RuntimeException e) {
 *     trace.fail(userText, e.getMessage(), System.currentTimeMillis() - start);
 *     throw e;
 * }
 * }</pre>
 *
 * <h2>⚠️ 跨线程埋点</h2>
 * <p>{@link TraceCollector#step} 从 {@code ThreadLocal} 取 traceId，而智能体的模型调用与工具执行
 * 运行在 Reactor 调度线程上，{@code ThreadLocal} 在那个线程里是空的。
 * 因此本门面暴露 {@link Handle#step(...)} 时<b>一律走
 * {@link TraceCollector#stepFor(String, String, String, String, String, long, String)}</b>
 * 显式传 traceId，不依赖 ThreadLocal。</p>
 *
 * <h2>⚠️ 失败不反噬主流程</h2>
 * <p>观测是旁路能力：<b>任何埋点异常都不允许影响业务</b>。{@link TraceCollector} 本身已对
 * observer 回调做了 try/catch，本门面在此之上再做一层，保证即使门面自身出错
 * （如 traceId 为空导致 {@code null} 传递）也静默降级。</p>
 */
public final class ObservableTrace {

    private ObservableTrace() {
    }

    /**
     * 开始一条链路。
     *
     * @param agentId     智能体 ID（可为空）
     * @param agentName   智能体名（用于根 span 命名）
     * @param intent      意图/触发来源分类
     * @param triggerType 触发类型（如 console / feishu）
     * @return 句柄；永不为 null（观测不可用时返回空实现）
     */
    public static Handle begin(String agentId, String agentName, String intent, String triggerType) {
        return begin(null, agentId, agentName, intent, triggerType);
    }

    /**
     * 开始一条链路（显式带 sessionId）。
     *
     * @param sessionId   会话标识
     * @param agentId     智能体 ID
     * @param agentName   智能体名
     * @param intent      意图
     * @param triggerType 触发类型
     * @return 句柄；永不为 null
     */
    public static Handle begin(String sessionId, String agentId, String agentName,
                               String intent, String triggerType) {
        try {
            String traceId = TraceCollector.begin(sessionId, agentId, agentName, intent, triggerType);
            return new Handle(traceId);
        } catch (Exception e) {
            // 观测失败不阻断业务：返回无效句柄，后续 step/end 全部 no-op
            return new Handle(null);
        }
    }

    /**
     * 把 traceId 绑定到当前执行上下文（跨线程埋点的前提）。
     *
     * <p>由调用方在 {@code begin} 之后、进入 agent 执行之前调用，把 traceId 挂到
     * AgentScope 的 {@code RuntimeContext} 上，中间件即可在 Reactor 线程里取回。</p>
     *
     * <p>⚠️ 不调用本方法不会报错，但链路中间步骤会整段空白 ——
     * {@link HarnessTraceMiddleware} 无法在 Reactor 线程上取回 traceId。</p>
     *
     * @param context AgentScope 运行时上下文（{@code RuntimeContext}）
     * @param handle  {@link #begin} 返回的句柄
     */
    public static void bind(RuntimeContext context, Handle handle) {
        if (context == null || handle == null || handle.traceId() == null) {
            return;
        }
        try {
            HarnessTraceMiddleware.bindTraceId(context, handle.traceId());
        } catch (Exception ignored) {
            // 绑定失败仅意味着中间件取不到 traceId（细节步骤缺失），链路本身仍成立
        }
    }

    /** 当前线程的 traceId（无则 null）。 */
    public static String currentTraceId() {
        try {
            return TraceCollector.currentTraceId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 链路句柄。
     *
     * <p>持有 traceId，提供带兜底的 step/end 操作。所有方法<b>均不抛异常</b>。</p>
     */
    public static final class Handle {

        private final String traceId;

        private Handle(String traceId) {
            this.traceId = traceId;
        }

        /** 链路 ID；为 null 表示观测不可用，所有操作都是 no-op。 */
        public String traceId() {
            return traceId;
        }

        /** 观测是否可用。 */
        public boolean active() {
            return traceId != null;
        }

        /**
         * 记录一个步骤。
         *
         * @param stepType   步骤类型（{@link GenAiStepTypes} 的规范词，或其历史别名）
         * @param name       步骤名
         * @param inputJson  输入
         * @param outputJson 输出
         * @param latencyMs  耗时毫秒
         * @param status     ok / failed
         */
        public void step(String stepType, String name, String inputJson,
                         String outputJson, long latencyMs, String status) {
            step(stepType, name, inputJson, outputJson, latencyMs, status, null);
        }

        /**
         * 记录一个步骤（带结构化属性）。
         *
         * <p>需要把「可聚合的数值/布尔」写进 span（召回条数、是否截断、是否异步等）时用本重载；
         * 塞进 {@code inputJson} 的自由文本导出侧不会解析，属性会等于不存在。</p>
         *
         * @param attributes 结构化属性；为 {@code null} 表示不带属性
         */
        public void step(String stepType, String name, String inputJson, String outputJson,
                         long latencyMs, String status, Map<String, Object> attributes) {
            if (traceId == null) {
                return;
            }
            try {
                TraceCollector.stepFor(traceId, GenAiStepTypes.normalize(stepType), name,
                        inputJson, outputJson, latencyMs, status, attributes);
            } catch (Exception ignored) {
            }
        }

        /** 记录成功步骤（status=ok）。 */
        public void ok(String stepType, String name, long latencyMs) {
            step(stepType, name, null, null, latencyMs, "ok");
        }

        /** 记录失败步骤（status=failed）。 */
        public void failed(String stepType, String name, String reason, long latencyMs) {
            step(stepType, name, null, reason, latencyMs, "failed");
        }

        /**
         * 正常收尾链路。
         *
         * @param status   成功态（ok / success）
         * @param prompt   用户输入
         * @param response 最终回复
         * @param tokens   token 消耗
         * @param latencyMs 总耗时
         */
        public void end(String status, String prompt, String response, int tokens, long latencyMs) {
            finish(status, prompt, response, tokens, latencyMs);
        }

        /**
         * 以失败态收尾链路。
         *
         * @param prompt   用户输入
         * @param errorMsg 错误信息（作为 response 写入，便于排查时直接看到原因）
         * @param latencyMs 总耗时
         */
        public void fail(String prompt, String errorMsg, long latencyMs) {
            finish("failed", prompt, errorMsg, 0, latencyMs);
        }

        private void finish(String status, String prompt, String response, int tokens, long latencyMs) {
            if (traceId == null) {
                return;
            }
            try {
                TraceCollector.endFor(traceId, status, prompt, response, tokens, latencyMs);
            } catch (Exception ignored) {
            }
        }
    }
}
