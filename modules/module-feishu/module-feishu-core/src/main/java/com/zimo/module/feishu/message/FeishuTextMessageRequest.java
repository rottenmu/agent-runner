package com.zimo.module.feishu.message;

public class FeishuTextMessageRequest {
    private final String receiveIdType;
    private final String receiveId;
    private final String text;
    private final String contentJson;

    public FeishuTextMessageRequest(String receiveIdType, String receiveId, String text, String contentJson) {
        this.receiveIdType = receiveIdType;
        this.receiveId = receiveId;
        this.text = text;
        this.contentJson = contentJson;
    }

    public String getReceiveIdType() {
        return receiveIdType;
    }

    public String getReceiveId() {
        return receiveId;
    }

    public String getText() {
        return text;
    }

    public String getContentJson() {
        return contentJson;
    }
}
