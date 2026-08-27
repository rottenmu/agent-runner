package com.zimo.module.feishu.config;

import java.time.LocalDateTime;

public class FeishuConfigResponse {
    private Long id;
    private String configName;
    private String appId;
    private String appSecret;
    private String verificationToken;
    private String encryptKey;
    private Integer enabled;
    /**
     * 当前绑定的智能体 ID；为空表示未绑定。
     */
    private String agentId;
    private String tenantKey;
    private String tenantName;
    private String credentialStatus;
    private LocalDateTime lastValidateTime;
    private LocalDateTime lastRefreshTime;
    private String scanState;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

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

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public String getEncryptKey() {
        return encryptKey;
    }

    public void setEncryptKey(String encryptKey) {
        this.encryptKey = encryptKey;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取当前绑定的智能体 ID。
     *
     * @return 智能体 ID，未绑定时为 {@code null}
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * 设置当前绑定的智能体 ID。
     *
     * @param agentId 智能体 ID，允许为 {@code null}
     */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public void setTenantKey(String tenantKey) {
        this.tenantKey = tenantKey;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getCredentialStatus() {
        return credentialStatus;
    }

    public void setCredentialStatus(String credentialStatus) {
        this.credentialStatus = credentialStatus;
    }

    public LocalDateTime getLastValidateTime() {
        return lastValidateTime;
    }

    public void setLastValidateTime(LocalDateTime lastValidateTime) {
        this.lastValidateTime = lastValidateTime;
    }

    public LocalDateTime getLastRefreshTime() {
        return lastRefreshTime;
    }

    public void setLastRefreshTime(LocalDateTime lastRefreshTime) {
        this.lastRefreshTime = lastRefreshTime;
    }

    public String getScanState() {
        return scanState;
    }

    public void setScanState(String scanState) {
        this.scanState = scanState;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
