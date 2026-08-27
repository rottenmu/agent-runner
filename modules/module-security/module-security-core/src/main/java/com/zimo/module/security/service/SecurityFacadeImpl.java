package com.zimo.module.security.service;

import com.zimo.module.security.entity.SecApproval;
import com.zimo.framework.common.security.SecurityFacade;
import com.zimo.module.auth.security.AuthSessionService;
import java.util.Map;

/**
 * 安全门面实现：组合审计、RBAC、内容安全、审批能力，向各业务模块提供统一入口。
 *
 * <p>业务模块（AI 对话、工具治理、知识库检索）通过 {@code Optional<SecurityFacade>}
 * 注入本实现；未集成安全模块时由 {@link com.zimo.framework.common.security.NoopSecurityFacade}
 * 平滑降级。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class SecurityFacadeImpl implements SecurityFacade {

    private final AuditLogService auditLogService;
    private final ResourcePermissionService permissionService;
    private final ContentSafetyService safetyService;
    private final ApprovalService approvalService;
    private final AuthSessionService sessionService;

    public SecurityFacadeImpl(AuditLogService auditLogService,
                              ResourcePermissionService permissionService,
                              ContentSafetyService safetyService,
                              ApprovalService approvalService,
                              AuthSessionService sessionService) {
        this.auditLogService = auditLogService;
        this.permissionService = permissionService;
        this.safetyService = safetyService;
        this.approvalService = approvalService;
        this.sessionService = sessionService;
    }

    @Override
    public boolean canAccess(String resourceType, String resourceId, String action) {
        try {
            Long userId = sessionService.getLoginIdAsLong();
            String username = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
            return permissionService.check(username, userId, resourceType, resourceId, action);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void audit(String actorType, String actorId, String action, String objectType,
                      String objectId, String detail, boolean success) {
        auditLogService.record(actorType, actorId, action, objectType, objectId,
                detail, success ? "success" : "failed", "", "");
    }

    @Override
    public String interceptInput(String text) {
        return safetyService.interceptInput(text);
    }

    @Override
    public String sanitizeOutput(String text) {
        return safetyService.sanitizeOutput(text);
    }

    @Override
    public String checkDangerousUrl(String url) {
        return safetyService.checkDangerousUrl(url);
    }

    @Override
    public ApprovalRequest requireApproval(String actionType, String objectType, String objectId,
                                           Map<String, Object> payload, String requesterId) {
        SecApproval approval = approvalService.create(actionType, objectType, objectId, payload, requesterId);
        if (approval == null) {
            return new ApprovalRequest(null, "无需审批");
        }
        return new ApprovalRequest(approval.getApprovalNo(), "高危操作需人工审批，审批单号: " + approval.getApprovalNo());
    }

    @Override
    public void notifyApprovalResult(String approvalId, boolean approved) {
        // 由前端审批接口直接驱动；此处保留兼容入口
    }
}
