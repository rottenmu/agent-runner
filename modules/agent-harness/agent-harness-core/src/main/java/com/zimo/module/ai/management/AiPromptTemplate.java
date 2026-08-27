package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * AI 管理模块的提示词模板实体，对应数据库表 ai_prompt_template。
 *
 * <p>该实体承载 CoSTAR 六段提示词内容、创建来源、业务简述、启停状态、软删除状态和审计字段。
 * 软删除字段中 0 表示正常，1 表示已删除，业务侧禁止物理删除。</p>
 *
 * @author xingju
 * @since 2026-07-08
 */
@TableName("ai_prompt_template")
public class AiPromptTemplate {
    /**
     * 主键 ID，由 MySQL 自增生成，在 ai_prompt_template 表内唯一。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 模板编码，用于前后端识别模板，不承担数据库唯一约束。
     */
    private String templateCode;

    /**
     * 模板名称，用于管理页面展示和用户选择。
     */
    private String templateName;

    /**
     * 模板说明，用于补充模板适用场景。
     */
    private String description;

    /**
     * 模板类型：agent 表示智能体提示词模板，skill 表示技能提示词模板。
     */
    private String templateType = "agent";

    /**
     * CoSTAR 上下文，说明业务背景、已有信息和约束。
     */
    private String contextText;

    /**
     * CoSTAR 目标，说明希望智能体完成的任务。
     */
    private String objectiveText;

    /**
     * CoSTAR 风格，说明输出表达风格。
     */
    private String styleText;

    /**
     * CoSTAR 语气，说明输出语气和态度。
     */
    private String toneText;

    /**
     * CoSTAR 受众，说明回答面向的用户角色。
     */
    private String audienceText;

    /**
     * CoSTAR 响应格式，说明输出结构、格式和限制。
     */
    private String responseText;

    /**
     * 创建方式，manual 表示手动编写，generated 表示系统生成。
     */
    private String sourceType = "manual";

    /**
     * 业务简述，用于系统生成提示词模板草稿。
     */
    private String businessDescription;

    /**
     * 启用状态，true 表示启用，false 表示停用。
     */
    private boolean enabled = true;

    /**
     * 软删除状态，false 表示正常，true 表示已删除，业务侧禁止物理删除。
     */
    @TableLogic
    private boolean deleted;

    /**
     * 创建人 ID，用于审计模板来源。
     */
    private String createdBy;

    /**
     * 创建人名称，用于管理页面展示审计信息。
     */
    private String createdName;

    /**
     * 更新人 ID，用于审计最后修改来源。
     */
    private String updatedBy;

    /**
     * 更新人名称，用于管理页面展示最后修改人。
     */
    private String updatedName;

    /**
     * 创建时间，由系统维护，无需调用方手动赋值。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，由系统维护，无需调用方手动赋值。
     */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTemplateType() {
        return templateType;
    }

    public void setTemplateType(String templateType) {
        this.templateType = templateType;
    }

    public String getContextText() {
        return contextText;
    }

    public void setContextText(String contextText) {
        this.contextText = contextText;
    }

    public String getObjectiveText() {
        return objectiveText;
    }

    public void setObjectiveText(String objectiveText) {
        this.objectiveText = objectiveText;
    }

    public String getStyleText() {
        return styleText;
    }

    public void setStyleText(String styleText) {
        this.styleText = styleText;
    }

    public String getToneText() {
        return toneText;
    }

    public void setToneText(String toneText) {
        this.toneText = toneText;
    }

    public String getAudienceText() {
        return audienceText;
    }

    public void setAudienceText(String audienceText) {
        this.audienceText = audienceText;
    }

    public String getResponseText() {
        return responseText;
    }

    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getBusinessDescription() {
        return businessDescription;
    }

    public void setBusinessDescription(String businessDescription) {
        this.businessDescription = businessDescription;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedName() {
        return createdName;
    }

    public void setCreatedName(String createdName) {
        this.createdName = createdName;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getUpdatedName() {
        return updatedName;
    }

    public void setUpdatedName(String updatedName) {
        this.updatedName = updatedName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
