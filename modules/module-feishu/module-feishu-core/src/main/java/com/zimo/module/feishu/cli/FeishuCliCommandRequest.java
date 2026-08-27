package com.zimo.module.feishu.cli;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class FeishuCliCommandRequest {
    private final String businessType;
    private final String method;
    private final String apiPath;
    private final Map<String, Object> params;
    private final Map<String, Object> data;
    private final Duration timeout;

    private FeishuCliCommandRequest(
            String businessType,
            String method,
            String apiPath,
            Map<String, Object> params,
            Map<String, Object> data,
            Duration timeout) {
        this.businessType = requireText(businessType, "businessType must not be blank");
        this.method = requireText(method, "method must not be blank").toUpperCase();
        this.apiPath = requireText(apiPath, "apiPath must not be blank");
        this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        this.data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
        this.timeout = timeout;
    }

    public static FeishuCliCommandRequest api(String businessType, String method, String apiPath) {
        return new FeishuCliCommandRequest(
                businessType,
                method,
                apiPath,
                Collections.emptyMap(),
                Collections.emptyMap(),
                null
        );
    }

    public FeishuCliCommandRequest withParam(String key, Object value) {
        Map<String, Object> next = new LinkedHashMap<>(params);
        next.put(requireText(key, "param key must not be blank"), value);
        return new FeishuCliCommandRequest(businessType, method, apiPath, next, data, timeout);
    }

    public FeishuCliCommandRequest withData(String key, Object value) {
        Map<String, Object> next = new LinkedHashMap<>(data);
        next.put(requireText(key, "data key must not be blank"), value);
        return new FeishuCliCommandRequest(businessType, method, apiPath, params, next, timeout);
    }

    public FeishuCliCommandRequest withTimeout(Duration timeout) {
        return new FeishuCliCommandRequest(businessType, method, apiPath, params, data, timeout);
    }

    public String getBusinessType() {
        return businessType;
    }

    public String getMethod() {
        return method;
    }

    public String getApiPath() {
        return apiPath;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Duration getTimeout() {
        return timeout;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
