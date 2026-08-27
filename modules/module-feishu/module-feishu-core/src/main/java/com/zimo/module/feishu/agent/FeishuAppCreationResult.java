package com.zimo.module.feishu.agent;

public class FeishuAppCreationResult {
    private String scanUrl;
    private String scanTicket;
    private Integer expireSeconds;
    private String appId;
    private String appSecret;

    public FeishuAppCreationResult() {
    }

    public FeishuAppCreationResult(
            String scanUrl,
            String scanTicket,
            Integer expireSeconds,
            String appId,
            String appSecret) {
        this.scanUrl = scanUrl;
        this.scanTicket = scanTicket;
        this.expireSeconds = expireSeconds;
        this.appId = appId;
        this.appSecret = appSecret;
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
}
