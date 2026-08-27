package com.zimo.module.feishu.agent.dto;

import java.util.List;

public class FeishuTenantScanInitRequest {
    private String tenantName;
    private String appName;
    private String appDescription;
    private String redirectUri;
    private String eventCallbackUrl;
    private List<String> permissionScopes;
    private List<String> eventSubscriptions;

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAppDescription() {
        return appDescription;
    }

    public void setAppDescription(String appDescription) {
        this.appDescription = appDescription;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getEventCallbackUrl() {
        return eventCallbackUrl;
    }

    public void setEventCallbackUrl(String eventCallbackUrl) {
        this.eventCallbackUrl = eventCallbackUrl;
    }

    public List<String> getPermissionScopes() {
        return permissionScopes;
    }

    public void setPermissionScopes(List<String> permissionScopes) {
        this.permissionScopes = permissionScopes;
    }

    public List<String> getEventSubscriptions() {
        return eventSubscriptions;
    }

    public void setEventSubscriptions(List<String> eventSubscriptions) {
        this.eventSubscriptions = eventSubscriptions;
    }
}
