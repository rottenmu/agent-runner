package com.zimo.module.ai.management;

import java.util.ArrayList;
import java.util.List;

public class AiManagedAgentRequest {
    private String name;
    private String desc;
    private String persona;
    private String model = "qwen-plus";
    private Long promptTemplateId;
    /** 智能体类型：conversation=普通对话，rag=检索增强，tool=工具调用，plan=规划执行，graph=图任务流。 */
    private String agentType = "conversation";
    /** 类型专属配置 JSON（知识库路径、计划参数、图任务声明等）。 */
    private String agentConfig;
    private List<String> skillIds = new ArrayList<>();
    private boolean enabled = true;
    private String userId = "u001";
    /** 智能体所属租户标识；为空时后端使用 {@code userId} 作为租户标识。 */
    private String tenantId;
    private String userName = "张建国";
    private List<String> defaultChannels = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getPersona() {
        return persona;
    }

    public void setPersona(String persona) {
        this.persona = persona;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Long getPromptTemplateId() {
        return promptTemplateId;
    }

    public void setPromptTemplateId(Long promptTemplateId) {
        this.promptTemplateId = promptTemplateId;
    }

    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    public String getAgentConfig() {
        return agentConfig;
    }

    public void setAgentConfig(String agentConfig) {
        this.agentConfig = agentConfig;
    }

    public List<String> getSkillIds() {
        return skillIds;
    }

    public void setSkillIds(List<String> skillIds) {
        this.skillIds = skillIds == null ? new ArrayList<>() : new ArrayList<>(skillIds);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * 获取请求中显式指定的租户标识。
     *
     * @return 租户标识；为空时由后端使用 {@code userId}
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * 设置智能体所属租户标识。
     *
     * @param tenantId 租户标识；允许为空，为空时使用 {@code userId}
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public List<String> getDefaultChannels() {
        return defaultChannels;
    }

    public void setDefaultChannels(List<String> defaultChannels) {
        this.defaultChannels = defaultChannels == null ? new ArrayList<>() : new ArrayList<>(defaultChannels);
    }
}
