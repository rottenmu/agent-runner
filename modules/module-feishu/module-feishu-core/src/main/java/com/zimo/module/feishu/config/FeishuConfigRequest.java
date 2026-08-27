package com.zimo.module.feishu.config;

public class FeishuConfigRequest {
    private String configName;
    private String appId;
    private String appSecret;
    private String verificationToken;
    private String encryptKey;
    private Integer enabled;
    private String tenantKey;
    private String tenantName;
    private String credentialStatus;
    private String scanState;
    private String scanTicket;
    private String permissionScopes;
    private String eventSubscriptions;
    private String remark;

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

    public String getScanState() {
        return scanState;
    }

    public void setScanState(String scanState) {
        this.scanState = scanState;
    }

    public String getScanTicket() {
        return scanTicket;
    }

    public void setScanTicket(String scanTicket) {
        this.scanTicket = scanTicket;
    }

    public String getPermissionScopes() {
        return permissionScopes;
    }

    public void setPermissionScopes(String permissionScopes) {
        this.permissionScopes = permissionScopes;
    }

    public String getEventSubscriptions() {
        return eventSubscriptions;
    }

    public void setEventSubscriptions(String eventSubscriptions) {
        this.eventSubscriptions = eventSubscriptions;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
