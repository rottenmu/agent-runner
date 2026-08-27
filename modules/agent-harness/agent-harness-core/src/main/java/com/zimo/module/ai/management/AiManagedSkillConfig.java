package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * AI 智能体管理模块的自定义技能配置实体，对应数据库表 ai_agent_skill_config。
 *
 * <p>该实体承载管理端创建的 API 类型技能配置。deleted 字段使用软删除规则，false 表示正常，true 表示已删除，
 * 业务侧禁止物理删除；agentId 为空时表示通用技能，可被任意智能体引用。</p>
 *
 * @author xingju
 * @since 2026-07-10
 */
@TableName("ai_agent_skill_config")
public class AiManagedSkillConfig {
    /**
     * 主键 ID，由 MySQL 自增生成，在 ai_agent_skill_config 表内唯一。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 适用智能体 ID，为空表示平台通用技能，不做租户或智能体级隔离。
     */
    private String agentId;

    /**
     * 技能名称，作为智能体运行时调用技能的稳定标识。
     */
    private String skillName;

    /**
     * 技能描述，用于管理端展示和模型选择技能时理解能力边界。
     */
    private String skillDescription;

    /**
     * 技能类型，本阶段固定承载 api 类型的自定义 HTTP 技能。
     */
    private String skillType = "api";

    /**
     * 是否只读，true 表示技能配置在绑定侧不允许被业务流程改写。
     */
    private boolean readOnly = true;

    /**
     * 是否启用，false 表示配置保留但不参与运行时技能调用。
     */
    private boolean enabled = true;

    /**
     * API 基础地址，用于拼接远程 HTTP 技能的请求地址。
     */
    private String baseUrl;

    /**
     * API 路径，用于拼接远程 HTTP 技能的请求地址。
     */
    private String apiPath;

    /**
     * HTTP 请求方法，只允许由业务层校验后的标准方法值入库。
     */
    private String httpMethod = "POST";

    /**
     * 请求头 JSON 字符串，为空表示不追加自定义请求头。
     */
    private String requestHeaders;

    /**
     * 请求超时时间，单位毫秒，由业务层保证大于 0。
     */
    private int timeoutMillis = 3000;

    /**
     * 关联的 API 注册表 ID，为空表示暂未绑定注册表来源。
     */
    private Long apiRegistryId;

    /**
     * 关联的技能提示词模板 ID，为空表示暂未绑定提示词模板。
     */
    private Long promptTemplateId;

    /**
     * 软删除状态，false 表示正常，true 表示已删除，禁止物理删除。
     */
    @TableLogic
    private boolean deleted;

    /**
     * 创建人 ID，用于审计技能配置来源。
     */
    private String createdBy;

    /**
     * 更新人 ID，用于审计最后一次修改来源。
     */
    private String updatedBy;

    /**
     * 创建时间，由系统维护，调用方无需手动赋值。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间，由系统维护，调用方无需手动赋值。
     */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getSkillDescription() {
        return skillDescription;
    }

    public void setSkillDescription(String skillDescription) {
        this.skillDescription = skillDescription;
    }

    public String getSkillType() {
        return skillType;
    }

    public void setSkillType(String skillType) {
        this.skillType = skillType;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiPath() {
        return apiPath;
    }

    public void setApiPath(String apiPath) {
        this.apiPath = apiPath;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(String requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public Long getApiRegistryId() {
        return apiRegistryId;
    }

    public void setApiRegistryId(Long apiRegistryId) {
        this.apiRegistryId = apiRegistryId;
    }

    public Long getPromptTemplateId() {
        return promptTemplateId;
    }

    public void setPromptTemplateId(Long promptTemplateId) {
        this.promptTemplateId = promptTemplateId;
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

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
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
