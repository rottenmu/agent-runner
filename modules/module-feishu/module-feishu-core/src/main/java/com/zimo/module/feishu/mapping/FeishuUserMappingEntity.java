package com.zimo.module.feishu.mapping;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ps_feishu_user_mapping")
public class FeishuUserMappingEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String feishuUserId;
    private String feishuOpenId;
    private String feishuUnionId;
    private String tenantKey;
    private Long internalUserId;
    private String internalAccount;
    private String internalUserName;
    private String organizationId;
    private String organizationName;
    private String dataScope;
    private String permissions;
    private Integer enabled;
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

    public String getFeishuUserId() {
        return feishuUserId;
    }

    public void setFeishuUserId(String feishuUserId) {
        this.feishuUserId = feishuUserId;
    }

    public String getFeishuOpenId() {
        return feishuOpenId;
    }

    public void setFeishuOpenId(String feishuOpenId) {
        this.feishuOpenId = feishuOpenId;
    }

    public String getFeishuUnionId() {
        return feishuUnionId;
    }

    public void setFeishuUnionId(String feishuUnionId) {
        this.feishuUnionId = feishuUnionId;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public void setTenantKey(String tenantKey) {
        this.tenantKey = tenantKey;
    }

    public Long getInternalUserId() {
        return internalUserId;
    }

    public void setInternalUserId(Long internalUserId) {
        this.internalUserId = internalUserId;
    }

    public String getInternalAccount() {
        return internalAccount;
    }

    public void setInternalAccount(String internalAccount) {
        this.internalAccount = internalAccount;
    }

    public String getInternalUserName() {
        return internalUserName;
    }

    public void setInternalUserName(String internalUserName) {
        this.internalUserName = internalUserName;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getDataScope() {
        return dataScope;
    }

    public void setDataScope(String dataScope) {
        this.dataScope = dataScope;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
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
