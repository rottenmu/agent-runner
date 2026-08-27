package com.zimo.module.feishu.admin.dto;

import java.util.Map;

public class FeishuBitableWriteTestRequest {
    private String appToken;
    private String tableId;
    private Map<String, Object> fields;

    public String getAppToken() {
        return appToken;
    }

    public void setAppToken(String appToken) {
        this.appToken = appToken;
    }

    public String getTableId() {
        return tableId;
    }

    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }
}
