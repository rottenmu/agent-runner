package com.zimo.framework.ai.observ;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.model.Model;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * AgentScope 中间件 → 自研 {@link TraceCollector} 的桥接实现。
 *
 * <p><b>为什么需要它</b>：{@code TraceCollector} 以 {@link ThreadLocal} 关联当前链路，
 * 而 AgentScope 的执行是 Reactor 响应式的——{@code AiAgentService} 用 {@code agent.call(...).block()}
 * 把流式结果拉回阻塞线程，模型调用与工具执行都发生在 Reactor 的调度线程上。
 * 于是 {@code ThreadLocal} 里的 traceId 传不进 agent 内部，导致现有链路**只有外层
 * intent / generation 两个点，agent 内部的每一轮推理、每次模型调用、每次工具执行全是空白**。</p>
 *
 * <p>本类在三个 hook 上补全这些缺失的 span，并落到 {@code TraceCollector} 的既有观测通道，
 * 前端「可观测」页无需改动即可看到。</p>
 *
 * <p><b>traceId 跨线程传递</b>：用 {@link RuntimeContext} 的 key-value 区承载（它在整次 reply 内
 * 被各层 hook 共享、线程安全），而非 ThreadLocal。外层 begin 时写入，
 * 内层 hook 从 ctx 读取；读取不到时退化为「无链路」不记录（不伪造 ID）。</p>
 *
 * <p><b>与 {@code OtelTracingMiddleware} 的关系</b>：两者互补而非替代。官方中间件产出标准
 * OTLP span 树（导出到 Jaeger/Langfuse 等），本类产出项目自有的 trace 步骤（落库、供前端展示）。
 * 二者可同时挂载。</p>
 *
 * @author WorkBuddy
 * @since 2026-09-13
 */
public class HarnessTraceMiddleware implements MiddlewareBase {

    private static final Logger LOG = LoggerFactory.getLogger(HarnessTraceMiddleware.class);

    /** 未绑定 traceId 时只告警一次，避免高频请求刷日志。 */
    private static final AtomicBoolean UNBOUND_WARNED = new AtomicBoolean(false);

    /** RuntimeContext 中承载自研 traceId 的 key。 */
    public static final String CTX_TRACE_ID = "zimo.traceId";

    /**
     * 在 RuntimeContext 中写入 traceId，供内层 hook 关联。
     *
     * <p>由 {@code AiAgentService} 在 {@code TraceCollector.begin(...)} 之后、调用 agent 之前调用。
     * 若不调用，本中间件不产生任何记录（静默降级，不影响主流程）。</p>
     *
     * @param context AgentScope 运行时上下文
     * @param traceId 自研链路 ID（{@code TraceCollector.begin} 的返回值）
     */
    public static void bindTraceId(RuntimeContext context, String traceId) {
        if (context != null && traceId != null && !traceId.isBlank()) {
            context.put(CTX_TRACE_ID, traceId);
        }
    }

    /** 从 RuntimeContext 取回 traceId（无则 null）。 */
    private static String traceIdOf(RuntimeContext context) {
        if (context == null) {
            return null;
        }
        try {
            Object value = context.get(CTX_TRACE_ID);
            if (value == null && UNBOUND_WARNED.compareAndSet(false, true)) {
                // 只告警一次：说明调用方忘了在 agent 调用前 bindTraceId，
                // 此时本中间件会静默不记录（不影响主流程），但链路会出现空白。
                LOG.warn("[observ] RuntimeContext 未绑定 {}，链路中间步骤将不记录。"
                        + "请确认 AiAgentService 在 agent 调用前调用了 bindTraceId。", CTX_TRACE_ID);
            }
            return value == null ? null : String.valueOf(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 包裹整次 reply：记录 agent 级起止与总耗时。
     *
     * <p>注意不在此处调 {@code TraceCollector.begin}——链路的生命周期由
     * {@code AiAgentService} 掌握（它还要负责 end 与状态判定），此处只产出中间步骤。</p>
     */
    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext ctx, AgentInput input,
                                    Function<AgentInput, Flux<AgentEvent>> next) {
        String traceId = traceIdOf(ctx);
        if (traceId == null) {
            // 未绑定链路：不记录，保持零开销
            return next.apply(input);
        }
        long start = System.currentTimeMillis();
        int messageCount = input == null || input.msgs() == null ? 0 : input.msgs().size();
        // 只记一条完成事件（与其它 hook 一致）：避免同一动作产生 start/ok 两行噪音。
        // 失败路径单独记 failed，便于前端区分。
        return next.apply(input)
                .doOnComplete(() -> TraceCollector.stepFor(traceId, "agent",
                        "智能体执行 " + safeName(agent),
                        "{\"messages\":" + messageCount + "}",
                        null, System.currentTimeMillis() - start, "ok"))
                .doOnError(error -> TraceCollector.stepFor(traceId, "agent",
                        "智能体执行 " + safeName(agent),
                        "{\"messages\":" + messageCount + "}",
                        "{\"error\":\"" + safeJson(safeMessage(error)) + "\"}",
                        System.currentTimeMillis() - start, "failed"));
    }

    /**
     * 包裹每次模型 API 调用：这是现有链路最大的一块空白。
     *
     * <p>记录模型类名、消息条数、工具 schema 数、耗时。多个 ReAct 轮次会依次产生多条记录，
     * 通过 stepType 前缀区分轮次。</p>
     */
    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext ctx, ModelCallInput input,
                                        Function<ModelCallInput, Flux<AgentEvent>> next) {
        String traceId = traceIdOf(ctx);
        if (traceId == null) {
            return next.apply(input);
        }
        Model model = input == null ? null : input.model();
        int messages = input == null || input.messages() == null ? 0 : input.messages().size();
        int tools = input == null || input.tools() == null ? 0 : input.tools().size();
        String modelName = model == null ? "unknown" : model.getClass().getSimpleName();
        String inputJson = "{\"model\":\"" + safeJson(modelName) + "\",\"messages\":" + messages
                + ",\"tools\":" + tools + "}";

        long start = System.currentTimeMillis();
        return next.apply(input)
                .doOnComplete(() -> TraceCollector.stepFor(traceId, "model_call", "模型调用 " + modelName,
                        inputJson, null, System.currentTimeMillis() - start, "ok"))
                .doOnError(error -> TraceCollector.stepFor(traceId, "model_call", "模型调用 " + modelName,
                        inputJson, "{\"error\":\"" + safeJson(safeMessage(error)) + "\"}",
                        System.currentTimeMillis() - start, "failed"));
    }

    /**
     * 包裹每一轮推理：ReAct 循环的轮次切分点。
     *
     * <p>记录本轮的输入消息数与可用工具数。一轮 reasoning 通常紧跟一次 modelCall
     * （若模型决定调用工具，再跟一次 acting），三者构成完整的 ReAct 轮次。</p>
     */
    @Override
    public Flux<AgentEvent> onReasoning(Agent agent, RuntimeContext ctx, ReasoningInput input,
                                        Function<ReasoningInput, Flux<AgentEvent>> next) {
        String traceId = traceIdOf(ctx);
        if (traceId == null) {
            return next.apply(input);
        }
        int messages = input == null || input.messages() == null ? 0 : input.messages().size();
        int tools = input == null || input.tools() == null ? 0 : input.tools().size();
        long start = System.currentTimeMillis();
        return next.apply(input)
                .doOnComplete(() -> TraceCollector.stepFor(traceId, "reasoning", "推理轮次",
                        "{\"messages\":" + messages + ",\"tools\":" + tools + "}",
                        null, System.currentTimeMillis() - start, "ok"))
                .doOnError(error -> TraceCollector.stepFor(traceId, "reasoning", "推理轮次",
                        "{\"messages\":" + messages + ",\"tools\":" + tools + "}",
                        "{\"error\":\"" + safeJson(safeMessage(error)) + "\"}",
                        System.currentTimeMillis() - start, "failed"));
    }

    /**
     * 包裹每次工具执行：补齐工具级耗时的空白。
     *
     * <p>⚠️ 按 AgentScope 文档，{@code onActing} 只覆盖 agent 运行时内部的工具执行；
     * 通过 external execution 在 agent 外部执行的工具不会被此 hook 捕获。</p>
     */
    @Override
    public Flux<AgentEvent> onActing(Agent agent, RuntimeContext ctx, ActingInput input,
                                     Function<ActingInput, Flux<AgentEvent>> next) {
        String traceId = traceIdOf(ctx);
        if (traceId == null) {
            return next.apply(input);
        }
        List<?> toolCalls = input == null ? null : input.toolCalls();
        String toolNames = describeToolCalls(toolCalls);
        long start = System.currentTimeMillis();
        return next.apply(input)
                .doOnComplete(() -> TraceCollector.stepFor(traceId, "tool_call", "工具执行 " + toolNames,
                        "{\"toolCalls\":" + (toolCalls == null ? 0 : toolCalls.size()) + "}",
                        null, System.currentTimeMillis() - start, "ok"))
                .doOnError(error -> TraceCollector.stepFor(traceId, "tool_call", "工具执行 " + toolNames,
                        null, "{\"error\":\"" + safeJson(safeMessage(error)) + "\"}",
                        System.currentTimeMillis() - start, "failed"));
    }

    /* ---------------- 内部工具方法 ---------------- */

    private static String safeName(Agent agent) {
        try {
            return agent == null ? "unknown" : String.valueOf(agent.getName());
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }

    /** 从 ToolUseBlock 列表提取工具名（尽力而为，未知结构只报数量）。 */
    private static String describeToolCalls(List<?> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return "(none)";
        }
        StringBuilder names = new StringBuilder();
        for (Object call : toolCalls) {
            if (call == null) {
                continue;
            }
            String name = extractToolName(call);
            if (names.length() > 0) {
                names.append(",");
            }
            names.append(name);
            if (names.length() > 80) {
                names.append("…");
                break;
            }
        }
        return names.length() == 0 ? "(" + toolCalls.size() + ")" : names.toString();
    }

    /** 反射式取工具名：ToolUseBlock 的方法名跨版本可能变化，取不到就退化为类名。 */
    private static String extractToolName(Object call) {
        for (String method : List.of("getName", "getToolName", "name")) {
            try {
                Object value = call.getClass().getMethod(method).invoke(call);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // 试下一个方法名
            }
        }
        return call.getClass().getSimpleName();
    }

    private static String safeMessage(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    @Override
    public int order() {
        // 略靠内层：让 OtelTracingMiddleware（默认 order=1）先建立 span 上下文，
        // 本类记录的时间区间落在其 span 内，便于两边对照。
        return 0;
    }
}
