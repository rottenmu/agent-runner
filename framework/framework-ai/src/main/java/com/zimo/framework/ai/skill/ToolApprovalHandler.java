package com.zimo.framework.ai.skill;

import java.util.Map;

/**
 * 工具调用 HITL 审批处理器（对齐 DeepSeek Harness one-shot approval）。
 *
 * <p>实现方决定每次工具调用的审批结果：
 * <ul>
 *   <li>{@link Status#APPROVED}：放行执行</li>
 *   <li>{@link Status#REJECTED}：拒绝执行（返回拒绝原因）</li>
 *   <li>{@link Status#PENDING}：待人工审批——同步模式下按 {@code denyOnPending} 处理（默认放行）</li>
 * </ul>
 * 未配置 handler 时工具默认放行。</p>
 */
public interface ToolApprovalHandler {

    enum Status { APPROVED, REJECTED, PENDING }

    /** 审批一次工具调用。 */
    Status approve(ToolCallContext context, String toolName, Map<String, Object> arguments);

    /** PENDING 是否视为拒绝（默认 false = 放行）。 */
    default boolean denyOnPending() {
        return false;
    }
}
