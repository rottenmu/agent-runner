package com.zimo.module.security.service;

import com.zimo.module.security.entity.SecApproval;
import com.zimo.module.security.mapper.SecApprovalMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zimo.framework.common.validation.ValidationUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 操作审批服务：高危动作（修改数据库、发起对外审批、删除数据、发送外部消息等）
 * 需人工审核通过后才执行。
 *
 * <p>流程：发起 → 创建审批单（pending）→ 审批人 approve/reject →
 * approved 后调用回调执行 → executed。回调通过 {@link SecurityFacadeImpl}
 * 挂接各业务模块的 {@code approvalCallback} 执行器。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 需审批的高危动作类型。 */
    public static final List<String> HIGH_RISK_ACTIONS = List.of(
            "update_db", "delete_data", "external_approval", "send_email", "execute_ddl");

    private final SecApprovalMapper approvalMapper;
    private final AuditLogService auditLogService;

    /** 审批通过后的执行回调（由安全模块装配层注入）。 */
    private java.util.function.BiConsumer<SecApproval, Boolean> callback;

    public ApprovalService(SecApprovalMapper approvalMapper, AuditLogService auditLogService) {
        this.approvalMapper = approvalMapper;
        this.auditLogService = auditLogService;
    }

    /** 设置审批执行回调。 */
    public void setCallback(java.util.function.BiConsumer<SecApproval, Boolean> callback) {
        this.callback = callback;
    }

    /**
     * 发起审批单。
     *
     * @return 审批单（status=pending）
     */
    public SecApproval create(String actionType, String objectType, String objectId,
                              Map<String, Object> payload, String requesterId) {
        if (!HIGH_RISK_ACTIONS.contains(actionType)) {
            return null;
        }
        SecApproval approval = new SecApproval();
        approval.setApprovalNo("AP" + System.currentTimeMillis() % 1000000000L);
        approval.setActionType(actionType);
        approval.setObjectType(objectType == null ? "" : objectType);
        approval.setObjectId(objectId == null ? "" : objectId);
        approval.setPayload(toJson(payload));
        approval.setRequesterId(requesterId == null ? "" : requesterId);
        approval.setApproverId("");
        approval.setStatus("pending");
        approval.setCallbackJson("{}");
        approval.setCreatedAt(LocalDateTime.now());
        approval.setUpdatedAt(LocalDateTime.now());
        approvalMapper.insert(approval);
        auditLogService.record("user", requesterId, "approval.create", "approval",
                String.valueOf(approval.getId()), "发起审批: " + actionType, "success", "", "");
        return approval;
    }

    /** 审批单列表。 */
    public List<SecApproval> list(String status, String actionType, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return approvalMapper.selectList(Wrappers.<SecApproval>lambdaQuery()
                .eq(StrUtil.isNotBlank(status), SecApproval::getStatus, status)
                .eq(StrUtil.isNotBlank(actionType), SecApproval::getActionType, actionType)
                .orderByDesc(SecApproval::getId)
                .last("LIMIT " + safeLimit));
    }

    /** 审批详情。 */
    public SecApproval detail(Long id) {
        return approvalMapper.selectById(id);
    }

    /**
     * 审批处理：approved → 调用执行回调 → executed；rejected → 终止。
     *
     * @return 更新后的审批单
     */
    public SecApproval handle(Long id, boolean approved, String comment, String approverId) {
        SecApproval approval = approvalMapper.selectById(id);
        ValidationUtil.requireNotNull(approval, "审批单不存在: ");
        if (!"pending".equals(approval.getStatus())) {
            throw new IllegalArgumentException("审批单已处理: " + approval.getStatus());
        }
        approval.setComment(comment == null ? "" : comment);
        approval.setApproverId(approverId == null ? "" : approverId);
        approval.setUpdatedAt(LocalDateTime.now());
        if (approved) {
            approval.setStatus("approved");
            approvalMapper.updateById(approval);
            // 执行回调（真实业务动作）
            try {
                if (callback != null) {
                    callback.accept(approval, true);
                }
                approval.setStatus("executed");
                approval.setResult("执行成功");
            } catch (Exception e) {
                approval.setStatus("executed");
                approval.setResult("执行失败: " + e.getMessage());
                log.warn("审批执行失败: {}", e.getMessage());
            }
            approvalMapper.updateById(approval);
            auditLogService.record("user", approverId, "approval.approve", "approval",
                    String.valueOf(id), "审批通过并执行: " + approval.getActionType(), "success", "", "");
        } else {
            approval.setStatus("rejected");
            approval.setResult("已驳回");
            approvalMapper.updateById(approval);
            auditLogService.record("user", approverId, "approval.reject", "approval",
                    String.valueOf(id), "驳回审批: " + approval.getActionType(), "success", "", "");
        }
        return approval;
    }

    private String toJson(Object value) {
        try {
            return value == null ? "{}" : MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
