package com.zimo.module.feishu.agent.dto;

import java.time.LocalDateTime;

public class FeishuTenantCredentialResponse {
    private Long id;
    private String tenantKey;
    private String tenantName;
    private String appId;
    private String appSecret;
    private String credentialStatus;
    private Integer enabled;
    private LocalDateTime lastValidateTime;
    private LocalDateTime lastRefreshTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getCredentialStatus() {
        return credentialStatus;
    }

    public void setCredentialStatus(String credentialStatus) {
        this.credentialStatus = credentialStatus;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
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
}
