package com.zimo.module.feishu.gateway;

import java.util.LinkedHashMap;
import java.util.Map;

public class FeishuAgentBusinessResult {
    private final boolean success;
    private final String title;
    private final String summary;
    private final String message;
    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final Map<String, Object> archiveFields = new LinkedHashMap<>();
    private final Map<String, String> links = new LinkedHashMap<>();

    private FeishuAgentBusinessResult(boolean success, String title, String summary, String message) {
        this.success = success;
        this.title = title;
        this.summary = summary;
        this.message = message;
    }

    public static FeishuAgentBusinessResult success(String title, String summary) {
        return new FeishuAgentBusinessResult(true, title, summary, null);
    }

    public static FeishuAgentBusinessResult failure(String title, String message) {
        return new FeishuAgentBusinessResult(false, title, null, message);
    }

    public FeishuAgentBusinessResult field(String name, Object value) {
        if (hasText(name)) {
            fields.put(name, value);
        }
        return this;
    }

    public FeishuAgentBusinessResult archiveField(String name, Object value) {
        if (hasText(name)) {
            archiveFields.put(name, value);
        }
        return this;
    }

    public FeishuAgentBusinessResult link(String text, String url) {
        if (hasText(text) && hasText(url)) {
            links.put(text, url);
        }
        return this;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public Map<String, Object> getArchiveFields() {
        return archiveFields;
    }

    public Map<String, String> getLinks() {
        return links;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
