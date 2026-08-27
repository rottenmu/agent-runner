package com.zimo.module.feishu.cli.task;

import java.util.List;

public class TaskAssigneeRequest {
    private final String taskGuid;
    private final List<String> userIds;

    public TaskAssigneeRequest(String taskGuid, List<String> userIds) {
        this.taskGuid = requireText(taskGuid, "taskGuid must not be blank");
        this.userIds = List.copyOf(userIds == null ? List.of() : userIds);
    }

    public String getTaskGuid() {
        return taskGuid;
    }

    public List<String> getUserIds() {
        return userIds;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
