package com.zimo.framework.ai.agent;

import java.util.List;

/**
 * around-middleware（对齐 DeepSeek Harness agent/pre-step 瀑布）。
 *
 * <p>与 {@link AiRequestInterceptor}（顺序链 + 短路）相比，本接口提供完整环绕语义：
 * middleware 可通过 {@code next.proceed} 委托给下一环，并在前后做包裹处理；也可不委托
 * 直接返回（短路接管，返回拒绝或完整回复）。</p>
 */
public interface AiAgentMiddleware {

    /**
     * 环绕执行。
     *
     * @param context 请求上下文（会话/智能体）
     * @param message 当前用户消息（可能已被上游改写）
     * @param next    委托链：调用即进入下一环（最终为完整主流程）
     * @return 统一结果（CONTINUE 继续 / DENY 拒绝 / REPLY 短路回复）
     */
    AiMiddlewareResult invoke(AiRequestContext context, String message, MiddlewareChain next);

    /** 顺序（越小越先执行，默认 0）。 */
    default int order() {
        return 0;
    }

    /** 将中间件列表组装为链（按 order 升序；链尾为 terminal）。 */
    static MiddlewareChain buildChain(List<AiAgentMiddleware> middlewares, MiddlewareChain terminal) {
        MiddlewareChain chain = terminal;
        if (middlewares == null || middlewares.isEmpty()) {
            return chain;
        }
        List<AiAgentMiddleware> sorted = middlewares.stream()
                .sorted(java.util.Comparator.comparingInt(AiAgentMiddleware::order))
                .toList();
        for (int i = sorted.size() - 1; i >= 0; i--) {
            AiAgentMiddleware middleware = sorted.get(i);
            MiddlewareChain next = chain;
            chain = (context, message) -> middleware.invoke(context, message, next);
        }
        return chain;
    }
}
