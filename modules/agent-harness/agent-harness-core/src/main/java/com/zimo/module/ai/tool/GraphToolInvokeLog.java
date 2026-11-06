package com.zimo.module.ai.tool;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 图内工具调用审计记录（append-only）。
 *
 * <p>所属模块：agent-harness 运行时内核的工具治理层。每次工具调用无论成功、失败还是
 * 被权限拦截，都必须留一条记录：工具是「能改变外部世界」的能力，出问题后
 * 第一件要回答的事是「谁在什么场景下用它做了什么」，没有这条记录就只能靠猜。</p>
 *
 * <h2>与 {@code module-tools} 的 {@code tool_invoke_log} 是什么关系</h2>
 * <p>两者<b>不重复</b>，回答的是不同问题：</p>
 * <ul>
 *   <li>{@code module-tools.tool_invoke_log}：工具<b>运行时</b>视角 —— 某个工具被谁调用、
 *       成功还是失败、是否被限流/熔断（由 {@code ToolGovernanceService} 写入）。</li>
 *   <li>{@code ai_graph_tool_invoke_log}（本表）：<b>编排</b>视角 —— 哪一次图运行
 *       （{@code runId}）、哪个节点（{@code nodeId}）用了什么入参，
 *       这是图引擎排障与回放必需的信息，运行时审计里没有。</li>
 * </ul>
 *
 * <p>之所以<b>另起表名而不是复用</b>：一是上述维度确实不同；二是同一进程内
 * 同名 Mapper（{@code toolInvokeLogMapper}）会导致
 * {@code ConflictingBeanDefinitionException} 直接启动失败 —— 这是实测踩到过的坑。</p>
 *
 * <p>本表只提供插入与查询，<b>不提供更新与删除</b> —— 审计记录一旦可改，
 * 它就不再是审计记录。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
@TableName("ai_graph_tool_invoke_log")
public class GraphToolInvokeLog {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工具业务标识。 */
    private String toolId;

    /** 租户标识。 */
    private String tenantId;

    /** 调用者标识。 */
    private String userId;

    /** 图运行实例 ID（非图调用为空）。 */
    private Long runId;

    /** 图节点 ID（非图调用为空）。 */
    private String nodeId;

    /** 入参 JSON（超长截断，避免审计表被大参数撑爆）。 */
    private String argsJson;

    /** 结果摘要（超长截断）。 */
    private String resultJson;

    /** 调用结论：success / failed / guarded。 */
    private String status;

    /** 失败原因（成功时为空）。 */
    private String errorMsg;

    /** 耗时（毫秒）。 */
    private Long latencyMs;

    /** 调用时间戳（毫秒）。 */
    private Long invokeTs;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getArgsJson() {
        return argsJson;
    }

    public void setArgsJson(String argsJson) {
        this.argsJson = argsJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Long getInvokeTs() {
        return invokeTs;
    }

    public void setInvokeTs(Long invokeTs) {
        this.invokeTs = invokeTs;
    }
}
