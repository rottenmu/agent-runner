package com.zimo.module.feishu.cli.task;

import com.zimo.module.feishu.cli.FeishuCliCommandRequest;
import com.zimo.module.feishu.cli.FeishuCliCommandResult;
import com.zimo.module.feishu.cli.FeishuCliTemplate;

import java.util.Objects;

public class FeishuTaskCliService {
    private final FeishuCliTemplate template;

    public FeishuTaskCliService(FeishuCliTemplate template) {
        this.template = Objects.requireNonNull(template, "template must not be null");
    }

    public FeishuCliCommandResult createTask(TaskCreateRequest request) {
        FeishuCliCommandRequest command = FeishuCliCommandRequest
                .api("task", "POST", "/open-apis/task/v2/tasks")
                .withData("summary", request.getSummary());
        if (request.getDescription() != null && !request.getDescription().trim().isEmpty()) {
            command = command.withData("description", request.getDescription());
        }
        return template.execute(command);
    }

    public FeishuCliCommandResult assignOwner(TaskAssigneeRequest request) {
        return template.execute(FeishuCliCommandRequest
                .api("task", "POST", "/open-apis/task/v2/tasks/" + request.getTaskGuid() + "/members")
                .withData("members", request.getUserIds()));
    }
}
