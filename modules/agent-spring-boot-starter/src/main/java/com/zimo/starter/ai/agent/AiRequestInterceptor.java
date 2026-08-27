package com.zimo.starter.ai.agent;

/**
 * 请求拦截器 SPI（对应 dsh agent/pre-step 权威拦截/改写/拒绝决策点）。
 *
 * <p>在用户消息进入 harness 前执行：可做安全校验、敏感词过滤、提示词改写、
 * 权限控制等。实现注册为 Spring Bean 后由 {@link AiAgentService} 收集为链，
 * 任一拦截器 DENY 即终止请求。</p>
 */
public interface AiRequestInterceptor {

    /**
     * 拦截决策。
     *
     * @param context     请求上下文（会话/智能体/profile）
     * @param userMessage 当前消息（可能已被前置拦截器改写）
     * @return 决策；返回 {@code null} 等价于 {@link AiRequestDecision#pass()}
     */
    AiRequestDecision intercept(AiRequestContext context, String userMessage);
}
