package com.zimo.module.feishu.cli;

import java.util.Objects;

public class FeishuCliTemplate {
    private final FeishuCliExecutor executor;
    private final FeishuCliCallLogService logService;
    private final FeishuCliPolicy policy;
    private final int retryTimes;

    public FeishuCliTemplate(
            FeishuCliExecutor executor,
            FeishuCliCallLogService logService,
            FeishuCliPolicy policy,
            int retryTimes) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.logService = logService;
        this.policy = policy == null ? FeishuCliPolicy.allowAll() : policy;
        this.retryTimes = Math.max(0, retryTimes);
    }

    public FeishuCliCommandResult execute(FeishuCliCommandRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!policy.allows(request.getBusinessType())) {
            FeishuCliCommandResult denied = FeishuCliCommandResult.failure(
                    -1,
                    "",
                    "",
                    "business type is not allowed: " + request.getBusinessType(),
                    0L,
                    0
            );
            record(request, denied);
            return denied;
        }

        FeishuCliCommandResult latest = null;
        int maxAttempts = Math.max(1, retryTimes + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            latest = executeOnce(request, attempt);
            if (latest.isSuccess()) {
                break;
            }
        }
        record(request, latest);
        return latest;
    }

    private FeishuCliCommandResult executeOnce(FeishuCliCommandRequest request, int attempt) {
        try {
            return executor.execute(request).withAttempts(attempt);
        } catch (RuntimeException e) {
            return FeishuCliCommandResult.failure(-1, "", "", e.getMessage(), 0L, attempt);
        }
    }

    private void record(FeishuCliCommandRequest request, FeishuCliCommandResult result) {
        if (logService != null) {
            logService.record(request, result);
        }
    }
}
