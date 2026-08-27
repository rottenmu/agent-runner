package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 智能体能力配置（目标拆解 / 意图识别 / 多轮澄清 / 参数抽取 / 问答记忆）。
 *
 * <p>以智能体 ID 为主键，各能力项以 JSON 字符串存储，无记录时使用默认配置。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@TableName("ai_agent_capability")
public class AiAgentCapability {

    /** 智能体业务主键，关联 ai_managed_agent.id */
    @TableId(type = IdType.INPUT)
    private String agentId;

    /** 目标拆解配置 JSON：{enabled, maxSubGoals} */
    private String goalDecomposition;

    /** 意图识别配置 JSON：{enabled, threshold} */
    private String intentRecognition;

    /** 多轮对话澄清配置 JSON：{enabled, maxRounds, minConfidence} */
    private String clarification;

    /** 参数抽取配置 JSON：{enabled, schema} */
    private String parameterExtraction;

    /** 问答记忆配置 JSON：{enabled, scope, maxEntries} */
    private String qaMemory;

    private LocalDateTime updatedAt;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getGoalDecomposition() {
        return goalDecomposition;
    }

    public void setGoalDecomposition(String goalDecomposition) {
        this.goalDecomposition = goalDecomposition;
    }

    public String getIntentRecognition() {
        return intentRecognition;
    }

    public void setIntentRecognition(String intentRecognition) {
        this.intentRecognition = intentRecognition;
    }

    public String getClarification() {
        return clarification;
    }

    public void setClarification(String clarification) {
        this.clarification = clarification;
    }

    public String getParameterExtraction() {
        return parameterExtraction;
    }

    public void setParameterExtraction(String parameterExtraction) {
        this.parameterExtraction = parameterExtraction;
    }

    public String getQaMemory() {
        return qaMemory;
    }

    public void setQaMemory(String qaMemory) {
        this.qaMemory = qaMemory;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
