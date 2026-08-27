package com.zimo.module.ai.management;

/**
 * AI 管理模块的提示词模板保存请求，用于新增和编辑 ai_prompt_template 数据。
 *
 * <p>请求体必须提供 templateName、sourceType 和 CoSTAR 六段内容；审计字段由调用方按当前登录用户传入。</p>
 *
 * @author xingju
 * @since 2026-07-08
 */
public class AiPromptTemplateRequest {
    /**
     * 模板编码，用于业务识别模板，不承担数据库唯一约束。
     */
    private String templateCode;

    /**
     * 模板名称，用于页面展示和用户选择。
     */
    private String templateName;

    /**
     * 模板说明，用于描述模板适用场景。
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
     * 业务简述，用于记录系统生成提示词时的原始输入。
     */
    private String businessDescription;

    /**
     * 启用状态，true 表示启用，false 表示停用。
     */
    private boolean enabled = true;

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
}
