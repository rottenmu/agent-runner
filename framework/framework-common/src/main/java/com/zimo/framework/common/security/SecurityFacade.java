package com.zimo.framework.common.security;

import java.util.Map;
import cn.hutool.core.util.StrUtil;

/**
 * 安全管控门面：向各业务模块提供细粒度 RBAC 权限、审计留痕、内容安全、高危操作审批的统一入口。
 *
 * <p>由 {@code module-security} 提供实现并注册为 Spring Bean；业务模块通过
 * {@code Optional<SecurityFacade>} 注入即可在无安全模块时平滑降级（使用
 * {@link NoopSecurityFacade} 默认行为）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public interface SecurityFacade {

    /**
     * 资源访问权限校验。
     *
     * @param resourceType 资源类型（agent / knowledge_base / tool / api / sensitive_data）
     * @param resourceId 资源 ID（为空表示对整类资源的默认权限）
     * @param action 动作（view / edit / execute）
     * @return true=允许
     */
    boolean canAccess(String resourceType, String resourceId, String action);

    /**
     * 审计留痕（对话、工具调用、数据读取、修改操作）。
     *
     * @param actorType 操作者类型（user / agent / system）
     * @param actorId 操作者 ID
     * @param action 动作描述（如 chat.send / tool.call / data.read / data.modify）
     * @param objectType 对象类型
     * @param objectId 对象 ID
     * @param detail 明细（JSON）
     * @param success 是否成功
     */
    void audit(String actorType, String actorId, String action, String objectType,
               String objectId, String detail, boolean success);

    /**
     * 输入内容安全拦截。
     *
     * @param text 用户输入
     * @return null=放行；非 null=拦截原因
     */
    String interceptInput(String text);

    /**
     * 输出内容净化（敏感词掩码 + PII 脱敏）。
     *
     * @param text 模型输出
     * @return 净化后文本
     */
    String sanitizeOutput(String text);

    /**
     * 高危接口检测（禁止 Agent 访问）。
     *
     * @param url 目标 URL
     * @return null=允许；非 null=拦截原因
     */
    String checkDangerousUrl(String url);

    /**
     * 高危操作审批。
     *
     * @param actionType 动作类型（update_db / delete_data / external_approval / send_email / execute_ddl）
     * @param objectType 对象类型
     * @param objectId 对象 ID
     * @param payload 请求载荷
     * @param requesterId 发起人
     * @return null=无需审批放行；非 null=审批单信息（等待人工审核）
     */
    ApprovalRequest requireApproval(String actionType, String objectType, String objectId,
                                    Map<String, Object> payload, String requesterId);

    /**
     * 审批结果通知（审批通过后执行回调）。
     *
     * @param approvalId 审批单号
     * @param approved 是否通过
     */
    void notifyApprovalResult(String approvalId, boolean approved);

    /**
     * 审批请求结果。
     *
     * @param approvalId 审批单号（null 表示无需审批）
     * @param message 提示信息
     */
    record ApprovalRequest(String approvalId, String message) {
        public boolean required() {
            return StrUtil.isNotBlank(approvalId);
        }
    }
}
