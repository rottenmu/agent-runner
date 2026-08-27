package com.zimo.module.feishu.agent.dto;

import java.util.List;

public class FeishuTenantScanInitResponse {
    private String scanUrl;
    private String scanTicket;
    private Integer expireSeconds;
    private List<String> permissionScopes;
    private List<String> eventSubscriptions;

    public FeishuTenantScanInitResponse() {
    }

    public FeishuTenantScanInitResponse(
            String scanUrl,
            String scanTicket,
            Integer expireSeconds,
            List<String> permissionScopes,
            List<String> eventSubscriptions) {
        this.scanUrl = scanUrl;
        this.scanTicket = scanTicket;
        this.expireSeconds = expireSeconds;
        this.permissionScopes = permissionScopes;
        this.eventSubscriptions = eventSubscriptions;
    }

    public String getScanUrl() {
        return scanUrl;
    }

    public void setScanUrl(String scanUrl) {
        this.scanUrl = scanUrl;
    }

    public String getScanTicket() {
        return scanTicket;
    }

    public void setScanTicket(String scanTicket) {
        this.scanTicket = scanTicket;
    }

    public Integer getExpireSeconds() {
        return expireSeconds;
    }

    public void setExpireSeconds(Integer expireSeconds) {
        this.expireSeconds = expireSeconds;
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
