package com.zimo.module.ai.management;

import java.util.Map;

/**
 * AI API 技能远程接口配置请求体。
 */
public class AiSkillApiConfigRequest {
    /**
     * 关联的 API 注册表 ID，为空表示暂未绑定注册表来源。
     */
    private Long apiRegistryId;
    private boolean enabled = true;
    private String baseUrl;
    private String path;
    private String method = "POST";
    private Map<String, String> headers;
    private int timeoutMillis = 3000;

    public Long getApiRegistryId() {
        return apiRegistryId;
    }

    public void setApiRegistryId(Long apiRegistryId) {
        this.apiRegistryId = apiRegistryId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }
}
