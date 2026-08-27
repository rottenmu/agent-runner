package com.zimo.module.feishu.agent;

import com.zimo.module.feishu.agent.dto.FeishuTenantScanInitRequest;
import java.util.List;

public class FeishuAppCreationRequest {
    private String tenantName;
    private String appName;
    private String appDescription;
    private String redirectUri;
    private String eventCallbackUrl;
    private List<String> permissionScopes;
    private List<String> eventSubscriptions;

    public static FeishuAppCreationRequest from(FeishuTenantScanInitRequest request) {
        FeishuAppCreationRequest creationRequest = new FeishuAppCreationRequest();
        creationRequest.setTenantName(request.getTenantName());
        creationRequest.setAppName(request.getAppName());
        creationRequest.setAppDescription(request.getAppDescription());
        creationRequest.setRedirectUri(request.getRedirectUri());
        creationRequest.setEventCallbackUrl(request.getEventCallbackUrl());
        creationRequest.setPermissionScopes(request.getPermissionScopes());
        creationRequest.setEventSubscriptions(request.getEventSubscriptions());
        return creationRequest;
    }

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
