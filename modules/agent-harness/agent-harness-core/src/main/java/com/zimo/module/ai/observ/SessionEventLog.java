package com.zimo.module.ai.observ;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 追加式会话事件日志（对标 DeepSeek Harness session log）。
 *
 * <p>append-only 事实源：一条链路（traceId）从 BEGIN → STEP… → END 的完整事件流，
 * 事件按 seq 单调递增追加，不可修改。回放（replay）/ Token 计量 / fork 均派生自该流。
 * 事件类型：{@link #TYPE_BEGIN} / {@link #TYPE_STEP} / {@link #TYPE_END}。</p>
 */
@TableName("observ_session_event")
public class SessionEventLog {

    /** 链路开始事件。 */
    public static final String TYPE_BEGIN = "BEGIN";
    /** 步骤事件（意图路由/工具调用/生成等，细分见 stepType）。 */
    public static final String TYPE_STEP = "STEP";
    /** 链路收尾事件（含状态/prompt/response/tokens）。 */
    public static final String TYPE_END = "END";

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 链路 ID（traceId）。 */
    private String traceId;
    /** 会话 ID。 */
    private String sessionId;
    /** 智能体名。 */
    private String agentName;
    /** 事件序号（同一链路内单调递增，0 起）。 */
    private Integer seq;
    /** 事件类型：BEGIN / STEP / END。 */
    private String eventType;
    /** 步骤类型（intent/tool/generation/agent/plan…）。 */
    private String stepType;
    /** 事件名称（如 invoke_agent / 意图路由 / 工具名）。 */
    private String name;
    /** 输入内容（JSON 文本，可截断）。 */
    private String inputText;
    /** 输出内容（JSON 文本，可截断）。 */
    private String outputText;
    /** 状态：ok / success / failed 等。 */
    private String status;
    /** Token 计量（输出 token，END 事件累计）。 */
    private Integer tokens;
    /** 耗时（毫秒）。 */
    private Long latencyMs;
    /** 事件时间戳（毫秒）。 */
    private Long eventTs;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getStepType() { return stepType; }
    public void setStepType(String stepType) { this.stepType = stepType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInputText() { return inputText; }
    public void setInputText(String inputText) { this.inputText = inputText; }
    public String getOutputText() { return outputText; }
    public void setOutputText(String outputText) { this.outputText = outputText; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTokens() { return tokens; }
    public void setTokens(Integer tokens) { this.tokens = tokens; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public Long getEventTs() { return eventTs; }
    public void setEventTs(Long eventTs) { this.eventTs = eventTs; }
}
