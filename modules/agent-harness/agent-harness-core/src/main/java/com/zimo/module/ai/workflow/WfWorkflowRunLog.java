package com.zimo.module.ai.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 工作流运行日志（节点级执行轨迹，支持断点展示）。
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@TableName("wf_workflow_run_log")
public class WfWorkflowRunLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private String nodeId;

    private String nodeName;

    private String nodeType;

    /** 事件：enter/exit/error/retry/breakpoint/manual_wait/manual_approved/manual_rejected */
    private String action;

    /** 结果：pending/success/failed/retrying/waiting */
    private String result;

    private String inputJson;

    private String outputJson;

    private String message;

    /** 是否断点暂停点 */
    private Boolean breakpoint;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getInputJson() {
        return inputJson;
    }

    public void setInputJson(String inputJson) {
        this.inputJson = inputJson;
    }

    public String getOutputJson() {
        return outputJson;
    }

    public void setOutputJson(String outputJson) {
        this.outputJson = outputJson;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getBreakpoint() {
        return breakpoint;
    }

    public void setBreakpoint(Boolean breakpoint) {
        this.breakpoint = breakpoint;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
