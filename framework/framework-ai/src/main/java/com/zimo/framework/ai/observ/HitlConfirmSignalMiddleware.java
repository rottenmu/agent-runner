package com.zimo.framework.ai.observ;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.UserConfirmResultEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * HITL（人在环路）确认信号的采集与传递中间件。
 *
 * <p><b>为什么需要它</b>：计划模式执行完成后要把「这次计划怎么做」抽象成程序性记忆回写，
 * 但<b>计划被用户驳回时不能回写</b> —— 否则会把用户否定过的方案沉淀成长期经验。
 * 而驳回与否这个信号只存在于 AgentScope 的 HITL 事件流里，应用侧此前完全没有消费
 * （全仓 {@code REQUIRE_USER_CONFIRM} 零命中），因此必须在中间件层把它捞出来、
 * 放到一个外层能读到的地方。</p>
 *
 * <p><b>信号载体为何选 RuntimeContext</b>：与 {@link HarnessTraceMiddleware} 同因 ——
 * AgentScope 的执行在 Reactor 调度线程上，{@code ThreadLocal} 过不了异步边界；
 * 而 {@link RuntimeContext} 在整次 reply 内被各层 hook 共享且线程安全。
 * 外层 {@code AiAgentService} 在 {@code agent.call(...).block()} 之后从同一个 ctx 读回结论。</p>
 *
 * <p><b>判据来源</b>：驳回态取自框架事件 {@link ConfirmResult#isConfirmed()}，
 * 而非我方对模型言辞的解析 —— 因此非恒真、可写负向对照（PRD 场景 2b）。</p>
 *
 * <p><b>兜底语义</b>：整轮未出现任何确认事件时（模型没走需要确认的工具），
 * {@link #isPlanRejected(RuntimeContext)} 返回 {@code false}，与 PRD「未驳回即回写」一致。</p>
 *
 * @author WorkBuddy
 * @since 2026-09-17
 */
public class HitlConfirmSignalMiddleware implements MiddlewareBase {

    private static final Logger LOG = LoggerFactory.getLogger(HitlConfirmSignalMiddleware.class);

    /** RuntimeContext 中标记「本轮出现过待用户确认」的 key。 */
    public static final String CTX_CONFIRM_PENDING = "zimo.hitl.confirmPending";
    /** RuntimeContext 中标记「用户驳回了确认」的 key。 */
    public static final String CTX_PLAN_REJECTED = "zimo.hitl.planRejected";
    /** RuntimeContext 中承载驳回明细（工具名等）的 key，便于链路留痕与排障。 */
    public static final String CTX_REJECT_DETAIL = "zimo.hitl.rejectDetail";

    /**
     * 订阅 agent 事件流，采集确认信号并写回 RuntimeContext。
     *
     * <p>按<b>事件类型</b>匹配而非按工具名匹配：{@code plan_exit} 之外的确认
     * （如 shell 执行授权）同样应被视为「用户对方案的否定」，硬匹配工具名会漏判。</p>
     */
    @Override
    public Flux<AgentEvent> onAgent(Agent agent, RuntimeContext ctx, AgentInput input,
                                    Function<AgentInput, Flux<AgentEvent>> next) {
        if (ctx == null) {
            return next.apply(input);
        }
        return next.apply(input).doOnNext(event -> capture(ctx, event));
    }

    /** 从单个事件里提取确认信号；不识别的类型直接忽略（不抛错、不影响主流）。 */
    private static void capture(RuntimeContext ctx, AgentEvent event) {
        try {
            if (event instanceof RequireUserConfirmEvent) {
                // 只是「到了待确认这一步」，此时还谈不上驳回；先记下来，
                // 便于区分「没走过确认流程」与「走过但被驳回了」两种情况。
                ctx.put(CTX_CONFIRM_PENDING, Boolean.TRUE);
                return;
            }
            if (event instanceof UserConfirmResultEvent result) {
                applyResults(ctx, result.getConfirmResults());
            }
        } catch (RuntimeException exception) {
            // 信号采集属旁路观测能力，任何异常都不得影响主流程
            LOG.warn("[hitl] 确认信号采集失败，已忽略: {}", exception.getMessage());
        }
    }

    /** 逐条判定确认结果：任一条被驳回即视为计划被驳回。 */
    private static void applyResults(RuntimeContext ctx, List<ConfirmResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        for (ConfirmResult result : results) {
            if (result != null && !result.isConfirmed()) {
                ctx.put(CTX_PLAN_REJECTED, Boolean.TRUE);
                ctx.put(CTX_REJECT_DETAIL, describe(result.getToolCall()));
            }
        }
    }

    /** 取被驳回的工具名，取不到就退化为类名（结构跨版本可能变）。 */
    private static String describe(Object toolCall) {
        if (toolCall == null) {
            return "(unknown)";
        }
        for (String method : List.of("getName", "getToolName", "name")) {
            try {
                Object value = toolCall.getClass().getMethod(method).invoke(toolCall);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // 试下一个方法名
            }
        }
        return toolCall.getClass().getSimpleName();
    }

    /** 本轮是否出现过待用户确认（用于区分「没这一环」与「被驳回」）。 */
    public static boolean isConfirmPending(RuntimeContext ctx) {
        return Boolean.TRUE.equals(read(ctx, CTX_CONFIRM_PENDING));
    }

    /**
     * 计划是否被用户驳回。
     *
     * <p>未出现任何确认事件时返回 {@code false}（兜底，见类注释）。</p>
     */
    public static boolean isPlanRejected(RuntimeContext ctx) {
        return Boolean.TRUE.equals(read(ctx, CTX_PLAN_REJECTED));
    }

    /** 驳回明细（被驳回的工具名），无驳回时为 {@code null}。 */
    public static String rejectDetail(RuntimeContext ctx) {
        Object value = read(ctx, CTX_REJECT_DETAIL);
        return value == null ? null : String.valueOf(value);
    }

    private static Object read(RuntimeContext ctx, String key) {
        if (ctx == null) {
            return null;
        }
        try {
            return ctx.get(key);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    public int order() {
        // 比 HarnessTraceMiddleware（order=0）更靠内层：确保先完成链路埋点，
        // 本类只负责信号采集，不参与 span 产出。
        return -1;
    }
}
