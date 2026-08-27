package com.zimo.module.feishu.cli.task;

public class TaskCreateRequest {
    private final String summary;
    private final String description;

    public TaskCreateRequest(String summary, String description) {
        this.summary = requireText(summary, "summary must not be blank");
        this.description = description;
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
