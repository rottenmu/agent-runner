package com.zimo.framework.ai.memory;

import com.zimo.module.agentmemory.engine.MemoryAwarePromptBuilder;
import com.zimo.module.agentmemory.engine.MemoryScope;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.middleware.MiddlewareBase;
import reactor.core.publisher.Mono;

/**
 * 记忆预召回注入中间件（M3，PRD §3.3）。
 *
 * <p>所属模块：{@code framework-ai}。职责是把「本轮该注入的长期记忆」追加到
 * <b>系统提示词</b>末尾。</p>
 *
 * <h2>为什么不是「往 messages 里塞一条 SYSTEM 消息」</h2>
 * <p>最初实现按 PRD §3.3 的字面描述，构造一条 {@code MsgRole.SYSTEM} 消息插到 messages 最前。
 * <b>真机验证时这条路径被 AgentScope 直接拒绝</b>：</p>
 * <pre>
 * Hooks must not inject SYSTEM messages into PreCallEvent.inputMessages.
 * Use event.setSystemMessage() or event.appendSystemContent() instead.
 * </pre>
 * <p>后果不是「记忆没注入」这么轻 —— 而是<b>每一条命中记忆的对话都直接失败</b>
 * （异常发生在模型调用之前，用户看到的是一句调用失败）。本版 2.0.2 的
 * {@code PreCallEvent} 只有 {@code getInputMessages/setInputMessages}，并不存在错误信息里
 * 推荐的 {@code appendSystemContent}，因此唯一受支持的注入点就是中间的
 * {@link MiddlewareBase#onSystemPrompt}（harness 自身的技能注入、计划模式、工作区上下文
 * 三个中间件都用它）。</p>
 *
 * <h2>为什么放系统提示词而不是用户消息</h2>
 * <p>记忆是<b>可能过期的背景事实</b>。若伪装成用户发言，「以后都用 Excel 导出报表」
 * 会被模型当成一条<b>新的用户指令</b>照办 —— 那正是 PRD R3 想避免的「用户改了偏好却改不掉」。
 * 放进系统提示词并带上冲突声明，模型才会把它当参考而非命令。</p>
 *
 * <h2>每轮只召回一次</h2>
 * <p>{@code onSystemPrompt} 在推理循环里可能被多次调用，而召回是有成本、要埋点、要写审计的动作。
 * 因此首次计算后把结果（含「本轮无片段」这一结论）缓存进 {@link RuntimeContext}，
 * 后续调用直接复用 —— 否则一次对话会产生多份重复审计行，指标虚高且无法解释。</p>
 *
 * @author zimo
 * @since 2026-09-16
 */
public class MemoryPromptMiddleware implements MiddlewareBase {

    /** RuntimeContext 键：隔离维度（{@link MemoryScope}）。 */
    public static final String CTX_SCOPE = "zimo.memory.scope";
    /** RuntimeContext 键：召回查询文本（本轮用户消息）。 */
    public static final String CTX_QUERY = "zimo.memory.query";
    /** RuntimeContext 键：本轮片段缓存（空串表示「本轮确认无片段」）。 */
    public static final String CTX_FRAGMENT = "zimo.memory.fragment";

    private final MemoryAwarePromptBuilder builder;

    /**
     * @param builder 记忆感知增强器；为 {@code null} 时本中间件整体不生效（不做任何召回）
     */
    public MemoryPromptMiddleware(MemoryAwarePromptBuilder builder) {
        this.builder = builder;
    }

    /**
     * 绑定本轮的隔离维度与召回查询文本，供内层 {@code onSystemPrompt} 读取。
     *
     * <p><b>必须通过 RuntimeContext 而不是 ThreadLocal</b>：AgentScope 的执行跑在 Reactor
     * 调度线程上，{@code AiAgentService} 用 {@code .block()} 把结果拉回受控线程，
     * ThreadLocal 在异步边界处失效（与 traceId 同一个坑，见 {@code HarnessTraceMiddleware}）。</p>
     *
     * @param context AgentScope 运行时上下文；为空时什么也不做
     * @param scope   隔离维度
     * @param query   召回查询文本（通常就是本轮用户消息）
     */
    public static void bindContext(RuntimeContext context, MemoryScope scope, String query) {
        if (context == null) {
            return;
        }
        if (scope != null) {
            context.put(CTX_SCOPE, scope);
        }
        if (query != null) {
            context.put(CTX_QUERY, query);
        }
    }

    /**
     * 把本轮记忆片段追加到系统提示词末尾。
     *
     * <p>任何异常都不向外抛 —— 记忆是增强能力，召回或留痕失败都不允许影响对话本身。</p>
     *
     * @param agent        当前智能体
     * @param context      运行时上下文
     * @param systemPrompt 框架已组装好的系统提示词
     * @return 追加片段后的系统提示词；无需注入或失败时返回原值
     */
    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext context, String systemPrompt) {
        if (builder == null || context == null) {
            return Mono.just(systemPrompt);
        }
        try {
            String cached = context.get(CTX_FRAGMENT, String.class);
            if (cached != null) {
                return Mono.just(augment(systemPrompt, cached));
            }
            MemoryScope scope = context.get(CTX_SCOPE, MemoryScope.class);
            String query = context.get(CTX_QUERY, String.class);
            String fragment = builder.recallFragment(scope, query, traceIdOf(context));
            // 缓存「本轮无片段」的结论：空串是有意义的负结果，不能与「还没算」混为一谈
            context.put(CTX_FRAGMENT, fragment == null ? "" : fragment);
            return Mono.just(augment(systemPrompt, fragment));
        } catch (RuntimeException e) {
            // 不抛：记忆注入失败必须降级为「本轮没有记忆」，而不是让整轮对话失败
            return Mono.just(systemPrompt);
        }
    }

    /** 链路 ID：复用自研链路中间件的 RuntimeContext 键，保证埋点与审计写进同一条链路。 */
    private static String traceIdOf(RuntimeContext context) {
        return context.get(com.zimo.framework.ai.observ.HarnessTraceMiddleware.CTX_TRACE_ID,
                String.class);
    }

    /** 拼接片段：系统提示词为空时不产生前导空行。 */
    private static String augment(String systemPrompt, String fragment) {
        if (fragment == null || fragment.isEmpty()) {
            return systemPrompt;
        }
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return fragment;
        }
        return systemPrompt + "\n\n" + fragment;
    }
}
