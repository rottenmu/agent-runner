package com.zimo.module.feishu.autoconfig;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feishu.agent.credential")
public class FeishuAgentCredentialProperties {
    private boolean enabled = true;
    private boolean validateBeforeAgentCall = true;
    private int scanExpireSeconds = 600;
    private List<String> defaultPermissionScopes = new ArrayList<>(
            List.of("im:message", "sheets:spreadsheet", "docs:document"));
    private List<String> defaultEventSubscriptions = new ArrayList<>(
            List.of("im.message.receive_v1"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isValidateBeforeAgentCall() {
        return validateBeforeAgentCall;
    }

    public void setValidateBeforeAgentCall(boolean validateBeforeAgentCall) {
        this.validateBeforeAgentCall = validateBeforeAgentCall;
    }

    public int getScanExpireSeconds() {
        return scanExpireSeconds;
    }

    public void setScanExpireSeconds(int scanExpireSeconds) {
        this.scanExpireSeconds = scanExpireSeconds;
    }

    public List<String> getDefaultPermissionScopes() {
        return defaultPermissionScopes;
    }

    public void setDefaultPermissionScopes(List<String> defaultPermissionScopes) {
        this.defaultPermissionScopes = defaultPermissionScopes;
    }

    public List<String> getDefaultEventSubscriptions() {
        return defaultEventSubscriptions;
    }

    public void setDefaultEventSubscriptions(List<String> defaultEventSubscriptions) {
        this.defaultEventSubscriptions = defaultEventSubscriptions;
    }
}
