package com.zimo.module.feishu.cli.bitable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class BitableRecordCreateRequest {
    private final String appToken;
    private final String tableId;
    private final Map<String, Object> fields;

    public BitableRecordCreateRequest(String appToken, String tableId, Map<String, Object> fields) {
        this.appToken = requireText(appToken, "appToken must not be blank");
        this.tableId = requireText(tableId, "tableId must not be blank");
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields == null ? Collections.emptyMap() : fields));
    }

    public String getAppToken() {
        return appToken;
    }

    public String getTableId() {
        return tableId;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
