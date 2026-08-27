package com.zimo.module.ai.modelconfig;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 模型配置数据实体，对应数据库表 {@code ai_model_config}。
 *
 * <p>{@code apiKey} 只允许在服务端内部流转，对外响应必须使用脱敏字段。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
@TableName(value = "ai_model_config", autoResultMap = true)
public class AiModelConfigEntity {

    /** 主键 ID，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置名称，面向前端和管理员展示。 */
    private String configName;

    /** 配置说明，允许为空。 */
    private String description;

    /** 模型供应商编码，仅允许 bailian 或 custom。 */
    private String provider;

    /** 模型服务调用地址。 */
    private String endpoint;

    /** 原始 API Key，仅服务端内部保存和使用。 */
    private String apiKey;

    /** 模型 ID，例如 qwen-plus。 */
    private String modelId;

    /** 运行环境编码，允许 dev、staging、prod。 */
    private String env;

    /** 启用状态，true 表示该配置允许被业务智能体选择。 */
    private boolean enabled;

    /** 采样温度，未配置时默认 0.70。 */
    private BigDecimal temperature;

    /** Top P 参数，未配置时默认 0.80。 */
    private BigDecimal topP;

    /** 最大输出 Token 数，未配置时默认 4096。 */
    private Integer maxTokens;

    /** 管理端标签列表，由 MyBatis-Plus JSON TypeHandler 持久化。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /** 最近一次连接测试状态，默认 untested。 */
    private String lastTestStatus;

    /** 最近一次连接测试耗时，单位毫秒。 */
    private Integer lastTestLatency;

    /** 最近一次连接测试消息。 */
    private String lastTestMessage;

    /** 最近一次连接测试时间，尚未测试时为空。 */
    private LocalDateTime lastTestedAt;

    /** 逻辑删除状态，false 表示正常，true 表示已删除。 */
    @TableLogic
    private boolean deleted;

    /** 创建时间，由数据库或仓储层维护。 */
    private LocalDateTime createdAt;

    /** 更新时间，由数据库或仓储层维护。 */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public BigDecimal getTopP() {
        return topP;
    }

    public void setTopP(BigDecimal topP) {
        this.topP = topP;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getLastTestStatus() {
        return lastTestStatus;
    }

    public void setLastTestStatus(String lastTestStatus) {
        this.lastTestStatus = lastTestStatus;
    }

    public Integer getLastTestLatency() {
        return lastTestLatency;
    }

    public void setLastTestLatency(Integer lastTestLatency) {
        this.lastTestLatency = lastTestLatency;
    }

    public String getLastTestMessage() {
        return lastTestMessage;
    }

    public void setLastTestMessage(String lastTestMessage) {
        this.lastTestMessage = lastTestMessage;
    }

    public LocalDateTime getLastTestedAt() {
        return lastTestedAt;
    }

    public void setLastTestedAt(LocalDateTime lastTestedAt) {
        this.lastTestedAt = lastTestedAt;
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