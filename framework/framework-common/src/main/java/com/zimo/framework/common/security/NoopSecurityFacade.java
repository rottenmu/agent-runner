package com.zimo.framework.common.security;

import java.util.Map;

/**
 * 安全门面空实现：未集成 {@code module-security} 时所有检查放行、审计忽略。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public final class NoopSecurityFacade implements SecurityFacade {

    @Override
    public boolean canAccess(String resourceType, String resourceId, String action) {
        return true;
    }

    @Override
    public void audit(String actorType, String actorId, String action, String objectType,
                      String objectId, String detail, boolean success) {
        // 未集成安全模块：忽略
    }

    @Override
    public String interceptInput(String text) {
        return null;
    }

    @Override
    public String sanitizeOutput(String text) {
        return text;
    }

    @Override
    public String checkDangerousUrl(String url) {
        return null;
    }

    @Override
    public ApprovalRequest requireApproval(String actionType, String objectType, String objectId,
                                           Map<String, Object> payload, String requesterId) {
        return new ApprovalRequest(null, "无需审批");
    }

    @Override
    public void notifyApprovalResult(String approvalId, boolean approved) {
        // 忽略
    }
}
