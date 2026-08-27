package com.zimo.module.feishu.config;

public class FeishuRuntimeConfig {
    private final String appId;
    private final String appSecret;
    private final String verificationToken;
    private final String encryptKey;

    public FeishuRuntimeConfig(String appId, String appSecret, String verificationToken, String encryptKey) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.verificationToken = verificationToken;
        this.encryptKey = encryptKey;
    }

    public String getAppId() {
        return appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public String getEncryptKey() {
        return encryptKey;
    }
}
