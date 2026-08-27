package com.zimo.module.feishu.cli;

import com.fasterxml.jackson.databind.JsonNode;

public class FeishuCliCommandResult {
    private final boolean success;
    private final Integer exitCode;
    private final String stdout;
    private final String stderr;
    private final String errorMessage;
    private final JsonNode json;
    private final Long costMillis;
    private final int attempts;

    private FeishuCliCommandResult(
            boolean success,
            Integer exitCode,
            String stdout,
            String stderr,
            String errorMessage,
            JsonNode json,
            Long costMillis,
            int attempts) {
        this.success = success;
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.errorMessage = errorMessage;
        this.json = json;
        this.costMillis = costMillis;
        this.attempts = attempts;
    }

    public static FeishuCliCommandResult success(String stdout, JsonNode json, Long costMillis, int attempts) {
        return new FeishuCliCommandResult(true, 0, stdout, null, null, json, costMillis, attempts);
    }

    public static FeishuCliCommandResult failure(
            Integer exitCode,
            String stdout,
            String stderr,
            String errorMessage,
            Long costMillis,
            int attempts) {
        return new FeishuCliCommandResult(false, exitCode, stdout, stderr, errorMessage, null, costMillis, attempts);
    }

    public FeishuCliCommandResult withAttempts(int attempts) {
        return new FeishuCliCommandResult(success, exitCode, stdout, stderr, errorMessage, json, costMillis, attempts);
    }

    public boolean isSuccess() {
        return success;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public JsonNode getJson() {
        return json;
    }

    public Long getCostMillis() {
        return costMillis;
    }

    public int getAttempts() {
        return attempts;
    }
}
