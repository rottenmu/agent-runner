package com.zimo.module.ai.graph;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 人工审批决策（human / manual 节点的恢复入参）。
 *
 * <p>所属模块：agent-harness 运行时内核。承载一次人工介入的裁决结果，
 * 由 {@link GraphEngine#approve} 消费：批准则把决策写入该节点输出并沿出口继续执行，
 * 驳回则走驳回出口或直接判失败。</p>
 *
 * <p>为什么要把决策做成值对象而不是若干裸参数：决策体里既有裁决（approved）、
 * 也有审计信息（operatorType/operatorId/comment）与业务回填（form），
 * 后续若增加「加签」「转办」等动作，只需扩展本对象，不必改引擎方法签名。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class GraphApproval {

    /** 是否批准；false 表示驳回。 */
    private final boolean approved;

    /** 审批意见，可为空。 */
    private final String comment;

    /** 操作者类型：human / agent / system，缺省 human。 */
    private final String operatorType;

    /** 操作者标识，缺省空串。 */
    private final String operatorId;

    /** 一次性恢复令牌，必须与挂起时下发的一致。 */
    private final String resumeToken;

    /** 审批表单回填值（如审批单号、金额调整），可为空。 */
    private final Map<String, Object> form;

    /**
     * 构造审批决策。
     *
     * @param approved 是否批准
     * @param comment 审批意见，可为空
     * @param operatorType 操作者类型，可为空（按 human 处理）
     * @param operatorId 操作者标识，可为空
     * @param resumeToken 恢复令牌，恢复时必须与挂起时一致
     * @param form 表单回填值，可为空
     */
    public GraphApproval(boolean approved, String comment, String operatorType,
            String operatorId, String resumeToken, Map<String, Object> form) {
        this.approved = approved;
        this.comment = comment;
        this.operatorType = operatorType;
        this.operatorId = operatorId;
        this.resumeToken = resumeToken;
        this.form = form;
    }

    /**
     * 构造不带表单的审批决策（UI 上最常见的一键批准 / 驳回）。
     *
     * @param approved 是否批准
     * @param comment 审批意见，可为空
     * @param operatorType 操作者类型，可为空
     * @param operatorId 操作者标识，可为空
     * @param resumeToken 恢复令牌
     * @return 审批决策
     */
    public static GraphApproval of(boolean approved, String comment, String operatorType,
            String operatorId, String resumeToken) {
        return new GraphApproval(approved, comment, operatorType, operatorId, resumeToken, null);
    }

    /** 是否批准。 */
    public boolean approved() {
        return approved;
    }

    /** 审批意见。 */
    public String comment() {
        return comment;
    }

    /** 操作者类型。 */
    public String operatorType() {
        return operatorType;
    }

    /** 操作者标识。 */
    public String operatorId() {
        return operatorId;
    }

    /** 恢复令牌。 */
    public String resumeToken() {
        return resumeToken;
    }

    /** 表单回填值。 */
    public Map<String, Object> form() {
        return form;
    }

    /**
     * 转换为写入节点输出的决策载荷。
     *
     * <p>下游节点可用 {@code {{ctx.nodeOutputs.<humanNodeId>.comment}}} 之类模板读取，
     * 也便于前端回放审批记录。</p>
     *
     * @return 决策载荷（approved/comment/operatorType/operatorId/form/decidedAt）
     */
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approved", approved);
        payload.put("comment", comment == null ? "" : comment);
        payload.put("operatorType", operatorType == null || operatorType.isBlank() ? "human" : operatorType);
        payload.put("operatorId", operatorId == null ? "" : operatorId);
        payload.put("form", form == null ? Map.of() : form);
        payload.put("decidedAt", LocalDateTime.now().toString());
        return payload;
    }
}
