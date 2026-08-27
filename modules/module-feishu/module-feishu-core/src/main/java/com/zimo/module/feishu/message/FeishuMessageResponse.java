package com.zimo.module.feishu.message;

public class FeishuMessageResponse {
    private final boolean success;
    private final String messageId;
    private final Integer code;
    private final String message;

    private FeishuMessageResponse(boolean success, String messageId, Integer code, String message) {
        this.success = success;
        this.messageId = messageId;
        this.code = code;
        this.message = message;
    }

    public static FeishuMessageResponse success(String messageId) {
        return new FeishuMessageResponse(true, messageId, 0, null);
    }

    public static FeishuMessageResponse failure(Integer code, String message) {
        return new FeishuMessageResponse(false, null, code, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessageId() {
        return messageId;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
