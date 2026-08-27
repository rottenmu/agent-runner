package com.zimo.module.feishu.config;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ps_feishu_config")
public class FeishuConfigEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configName;
    private String appId;
    private String appSecret;
    private String verificationToken;
    private String encryptKey;
    private Integer enabled;
    /**
     * 绑定的智能体 ID；为空表示该飞书配置未指定智能体。
     */
    private String agentId;
    private String tenantKey;
    private String tenantName;
    private String credentialStatus;
    private LocalDateTime lastValidateTime;
    private LocalDateTime lastRefreshTime;
    private String scanState;
    private String scanTicket;
    private String permissionScopes;
    private String eventSubscriptions;
    private String remark;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
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
     * 获取飞书配置绑定的智能体 ID。
     *
     * @return 智能体 ID，未绑定时为 {@code null}
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * 设置飞书配置绑定的智能体 ID。
     *
     * @param agentId 智能体 ID，允许为 {@code null} 以解除绑定
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
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
