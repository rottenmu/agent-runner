package com.zimo.module.feishu.cli.bitable;

public class BitableRecordQueryRequest {
    private final String appToken;
    private final String tableId;
    private final Integer pageSize;
    private final String pageToken;

    public BitableRecordQueryRequest(String appToken, String tableId, Integer pageSize, String pageToken) {
        this.appToken = requireText(appToken, "appToken must not be blank");
        this.tableId = requireText(tableId, "tableId must not be blank");
        this.pageSize = pageSize;
        this.pageToken = pageToken;
    }

    public String getAppToken() {
        return appToken;
    }

    public String getTableId() {
        return tableId;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public String getPageToken() {
        return pageToken;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
