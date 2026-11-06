package com.zimo.module.ai.runtime;

import java.util.List;

/**
 * 一次智能体执行的结果（治理层统一视图）。
 *
 * <p>为什么用状态字符串而不是抛异常表达失败：图执行里一个 agent 节点失败不该让
 * 整张图崩掉 —— 调用方需要拿到「失败原因」继续走兜底分支，异常会剥夺这个选择。</p>
 *
 * @param status 终态：success / denied（闸门拒绝）/ failed
 * @param reply 回复正文（成功时非空；闸门接管时为接管文案）
 * @param agentId 实际执行的智能体标识
 * @param agentName 智能体展示名
 * @param toolIds 本次执行解析出的工具白名单
 * @param shortCircuited 是否由闸门接管（未进入模型）
 * @param error 失败原因（成功时为 {@code null}）
 * @param elapsedMs 耗时（毫秒）
 * @author WorkBuddy
 * @since 2026-10-20
 */
public record AgentRunResult(
        String status,
        String reply,
        String agentId,
        String agentName,
        List<String> toolIds,
        boolean shortCircuited,
        String error,
        long elapsedMs) {

    /** 成功终态。 */
    public static final String STATUS_SUCCESS = "success";

    /** 闸门拒绝终态（权限不足 / 敏感词 / 意图拒绝）。 */
    public static final String STATUS_DENIED = "denied";

    /** 执行失败终态。 */
    public static final String STATUS_FAILED = "failed";

    /** 规范化空值。 */
    public AgentRunResult {
        reply = reply == null ? "" : reply;
        toolIds = toolIds == null ? List.of() : List.copyOf(toolIds);
    }

    /** 构造成功结果。 */
    public static AgentRunResult ok(String reply, String agentId, String agentName,
                                    List<String> toolIds, boolean shortCircuited, long elapsedMs) {
        return new AgentRunResult(STATUS_SUCCESS, reply, agentId, agentName,
                toolIds, shortCircuited, null, elapsedMs);
    }

    /** 构造拒绝结果。 */
    public static AgentRunResult denied(String error, String agentId, long elapsedMs) {
        return new AgentRunResult(STATUS_DENIED, "", agentId, "", List.of(), false, error, elapsedMs);
    }

    /** 构造失败结果。 */
    public static AgentRunResult failed(String error, String agentId, long elapsedMs) {
        return new AgentRunResult(STATUS_FAILED, "", agentId, "", List.of(), false, error, elapsedMs);
    }

    /** 是否成功。 */
    public boolean success() {
        return STATUS_SUCCESS.equals(status);
    }
}
