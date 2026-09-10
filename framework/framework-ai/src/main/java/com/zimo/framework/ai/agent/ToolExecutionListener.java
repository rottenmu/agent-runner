package com.zimo.framework.ai.agent;

/**
 * 工具执行钩子（对应 dsh {@code tools/*} 把关流水线）：
 * 在技能工具调用前后提供拦截点（审批/白名单/预算/审计/遥测）。
 *
 * <p>实现注册为 Spring Bean 后由 {@link AiHarnessAgentFactory} 注入到技能工具，
 * 默认无任何实现时工具直通执行（零行为变化）。</p>
 */
public interface ToolExecutionListener {

    /** 工具执行前回调；返回 false 表示拒绝执行。 */
    default boolean onPreExecute(String toolName, Object params) {
        return true;
    }

    /** 工具执行后回调（成功）。 */
    default void onPostExecute(String toolName, Object params, Object result) {
    }

    /** 工具执行后回调（异常）。 */
    default void onError(String toolName, Object params, Throwable error) {
    }

    /** 工具注册时回调（工具清单审计）。 */
    default void onToolRegistered(String toolName) {
    }
}
