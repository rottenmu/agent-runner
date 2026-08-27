package com.zimo.starter.ai.agent;

/**
 * around-middleware 链委托：{@code proceed} 将控制权交给链中下一环
 * （最后一个 middleware 的 proceed 即主流程执行器）。
 *
 * <p>middleware 可：调用 {@code proceed} 前做前置处理；{@code proceed} 返回后做后置包裹；
 * 或完全不调用 proceed 直接返回（短路接管）。</p>
 */
@FunctionalInterface
public interface MiddlewareChain {

    /** 委托下一环执行。 */
    AiMiddlewareResult proceed(AiRequestContext context, String message);
}
