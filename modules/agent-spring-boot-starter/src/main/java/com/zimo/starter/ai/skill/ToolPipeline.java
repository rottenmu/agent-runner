package com.zimo.starter.ai.skill;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 工具执行流水线（对齐 DeepSeek Harness tool pipeline）。
 *
 * <p>阶段：<b>pre hooks</b>（可拒绝）→ <b>guards</b>（单调/频控守卫）→
 * <b>HITL 审批</b>（一次性审批，可拒绝）→ <b>执行</b>（超时由技能自身保证，
 * 本层提供失败重试）→ <b>post hooks</b>（可重写结果）→ <b>冻结</b>
 * （trace step 打点：tool 类型，含 input/output/耗时/状态）。
 * 任一阶段拒绝即短路，不进入执行。</p>
 */
public class ToolPipeline {

    private final List<ToolHook> hooks;
    private final List<ToolGuard> guards;
    private final ToolApprovalHandler approvalHandler;
    private final int maxRetries;

    public ToolPipeline(List<ToolHook> hooks, List<ToolGuard> guards,
                        ToolApprovalHandler approvalHandler, int maxRetries) {
        this.hooks = hooks == null ? List.of() : hooks;
        this.guards = guards == null ? List.of() : guards;
        this.approvalHandler = approvalHandler;
        this.maxRetries = Math.max(0, maxRetries);
    }

    /** 无扩展点的流水线（全放行、无重试）。 */
    public static ToolPipeline empty() {
        return new ToolPipeline(List.of(), List.of(), null, 0);
    }

    /**
     * 执行工具（经完整流水线）。
     *
     * @param context  调用上下文（trace 打点用）
     * @param toolName 工具名
     * @param arguments 入参
     * @param executor 实际执行器（接受入参，返回结果；可抛异常触发重试）
     * @return 最终结果（拒绝/失败均以 AiSkillResult.fail 表达）
     */
    public AiSkillResult execute(ToolCallContext context, String toolName,
                                 Map<String, Object> arguments,
                                 Function<Map<String, Object>, AiSkillResult> executor) {
        long start = System.currentTimeMillis();
        // 1) pre hooks
        for (ToolHook hook : hooks) {
            Optional<String> denied = hook.pre(context, toolName, arguments);
            if (denied.isPresent()) {
                return finish(context, toolName, arguments,
                        AiSkillResult.fail("工具被 pre-hook 拒绝：" + denied.get()), start);
            }
        }
        // 2) guards
        for (ToolGuard guard : guards) {
            Optional<String> reason = guard.check(context, toolName, arguments);
            if (reason.isPresent()) {
                return finish(context, toolName, arguments,
                        AiSkillResult.fail("工具调用被守卫拦截：" + reason.get()), start);
            }
        }
        // 3) HITL 审批
        if (approvalHandler != null) {
            ToolApprovalHandler.Status status = approvalHandler.approve(context, toolName, arguments);
            if (status == ToolApprovalHandler.Status.REJECTED
                    || (status == ToolApprovalHandler.Status.PENDING && approvalHandler.denyOnPending())) {
                return finish(context, toolName, arguments,
                        AiSkillResult.fail("工具调用未通过审批（" + status + "）"), start);
            }
        }
        // 4) 执行（带重试）
        AiSkillResult result = null;
        Exception lastError = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                result = executor.apply(arguments);
                if (result != null) {
                    break;
                }
                lastError = new IllegalStateException("executor 返回 null");
            } catch (Exception e) {
                lastError = e;
                if (attempt >= maxRetries) {
                    result = AiSkillResult.fail("工具执行失败（已重试 " + attempt + " 次）："
                            + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                }
            }
        }
        if (result == null) {
            result = AiSkillResult.fail("工具执行失败：" + (lastError == null ? "未知错误" : lastError.getMessage()));
        }
        // 5) post hooks（可重写）
        for (ToolHook hook : hooks) {
            result = hook.post(context, toolName, arguments, result);
        }
        return finish(context, toolName, arguments, result, start);
    }

    /** 冻结：trace step 打点 + 返回。 */
    private AiSkillResult finish(ToolCallContext context, String toolName,
                                 Map<String, Object> arguments, AiSkillResult result, long start) {
        try {
            com.zimo.starter.ai.observ.TraceCollector.step("tool", toolName,
                    toJson(arguments), result == null ? null : result.content(),
                    System.currentTimeMillis() - start,
                    result != null && result.success() ? "ok" : "failed");
        } catch (Exception ignored) {
            // 打点失败不影响工具结果
        }
        return result;
    }

    private static String toJson(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(arguments);
        } catch (Exception e) {
            return String.valueOf(arguments);
        }
    }
}
