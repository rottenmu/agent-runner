package com.zimo.module.feishu.cli.bitable;

public class BitableCellUpdateRequest {
    private final String appToken;
    private final String tableId;
    private final String recordId;
    private final String fieldName;
    private final Object value;

    public BitableCellUpdateRequest(String appToken, String tableId, String recordId, String fieldName, Object value) {
        this.appToken = requireText(appToken, "appToken must not be blank");
        this.tableId = requireText(tableId, "tableId must not be blank");
        this.recordId = requireText(recordId, "recordId must not be blank");
        this.fieldName = requireText(fieldName, "fieldName must not be blank");
        this.value = value;
    }

    public String getAppToken() {
        return appToken;
    }

    public String getTableId() {
        return tableId;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Object getValue() {
        return value;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
