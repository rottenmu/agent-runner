package com.zimo.module.feishu.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;

public class FeishuCliDebugResponse {
    private boolean success;
    private Integer exitCode;
    private String stdout;
    private String stderr;
    private String errorMessage;
    private JsonNode json;
    private Long costMillis;
    private int attempts;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public JsonNode getJson() {
        return json;
    }

    public void setJson(JsonNode json) {
        this.json = json;
    }

    public Long getCostMillis() {
        return costMillis;
    }

    public void setCostMillis(Long costMillis) {
        this.costMillis = costMillis;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }
}
