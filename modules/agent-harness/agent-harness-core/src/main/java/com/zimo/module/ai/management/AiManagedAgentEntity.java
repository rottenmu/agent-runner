package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * AI 智能体管理模块的持久化实体，对应数据库表 {@code ai_managed_agent}。
 *
 * <p>技能和默认渠道以 JSON 字符串存储；{@code deleted} 使用 MyBatis-Plus 逻辑删除，
 * 0 表示正常，1 表示已删除。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
@TableName("ai_managed_agent")
public class AiManagedAgentEntity {

    /** 业务主键，由管理服务生成并在表内唯一。 */
    @TableId
    private String id;

    /** 智能体名称。 */
    private String agentName;

    /** 智能体说明。 */
    private String agentDesc;

    /** 智能体系统提示词。 */
    private String persona;

    /** 智能体使用的模型名称。 */
    private String modelName;

    /** 关联的提示词模板主键，允许为空。 */
    private Long promptTemplateId;

    /** 智能体类型：conversation=普通对话，rag=检索增强，tool=工具调用，plan=规划执行，graph=图任务流。 */
    private String agentType;

    /** 类型专属配置 JSON（知识库路径、计划参数、图任务声明等）。 */
    private String agentConfig;

    /** 技能标识列表的 JSON 字符串。 */
    private String skillIds;

    /** 启用状态，true 表示可参与运行时解析。 */
    private boolean enabled;

    /** 创建或维护该智能体的用户标识。 */
    private String userId;

    /**
     * 智能体所属租户 ID，是数据和运行时路由的隔离维度。
     *
     * <p>历史数据由 {@code user_id} 回填；新增数据未显式指定时由管理服务使用 {@code userId} 填充。</p>
     */
    private String tenantId;

    /** 创建或维护该智能体的用户名称。 */
    private String userName;

    /** 默认渠道列表的 JSON 字符串。 */
    private String defaultChannels;

    /** 逻辑删除状态，0 表示正常，1 表示已删除，禁止物理删除。 */
    @TableLogic
    private boolean deleted;

    /** 创建时间，由数据库维护。 */
    private LocalDateTime createdAt;

    /** 更新时间，由数据库维护。 */
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getAgentDesc() {
        return agentDesc;
    }

    public void setAgentDesc(String agentDesc) {
        this.agentDesc = agentDesc;
    }

    public String getPersona() {
        return persona;
    }

    public void setPersona(String persona) {
        this.persona = persona;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
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

    public String getSkillIds() {
        return skillIds;
    }

    public void setSkillIds(String skillIds) {
        this.skillIds = skillIds;
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
     * 获取智能体所属租户标识。
     *
     * @return 数据和运行时路由使用的租户标识
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * 设置智能体所属租户标识。
     *
     * @param tenantId 数据和运行时路由使用的租户标识，不允许跨租户复用
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

    public String getDefaultChannels() {
        return defaultChannels;
    }

    public void setDefaultChannels(String defaultChannels) {
        this.defaultChannels = defaultChannels;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
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
