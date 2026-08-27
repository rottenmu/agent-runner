package com.zimo.module.ai.management;

/**
 * AI 管理端新增 API 技能请求体。
 */
public class AiManagedSkillRequest {
    private String name;
    private String description;
    private String agentId;
    private Long promptTemplateId;
    private boolean readOnly;
    private AiSkillApiConfigRequest apiConfig;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public Long getPromptTemplateId() {
        return promptTemplateId;
    }

    public void setPromptTemplateId(Long promptTemplateId) {
        this.promptTemplateId = promptTemplateId;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public AiSkillApiConfigRequest getApiConfig() {
        return apiConfig;
    }

    public void setApiConfig(AiSkillApiConfigRequest apiConfig) {
        this.apiConfig = apiConfig;
    }
}
