package com.zimo.module.feishu.agent.dto;

import java.time.LocalDateTime;

public class FeishuCredentialValidateResponse {
    private boolean valid;
    private String message;
    private LocalDateTime validateTime;

    public FeishuCredentialValidateResponse() {
    }

    public FeishuCredentialValidateResponse(boolean valid, String message, LocalDateTime validateTime) {
        this.valid = valid;
        this.message = message;
        this.validateTime = validateTime;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getValidateTime() {
        return validateTime;
    }

    public void setValidateTime(LocalDateTime validateTime) {
        this.validateTime = validateTime;
    }
}
