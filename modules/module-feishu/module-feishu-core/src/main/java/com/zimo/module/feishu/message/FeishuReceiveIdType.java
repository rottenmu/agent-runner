package com.zimo.module.feishu.message;

public enum FeishuReceiveIdType {
    USER_ID("user_id"),
    OPEN_ID("open_id"),
    CHAT_ID("chat_id");

    private final String apiValue;

    FeishuReceiveIdType(String apiValue) {
        this.apiValue = apiValue;
    }

    public String getApiValue() {
        return apiValue;
    }
}
