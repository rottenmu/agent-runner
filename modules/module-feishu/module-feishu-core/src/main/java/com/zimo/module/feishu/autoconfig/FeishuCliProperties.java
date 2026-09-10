package com.zimo.module.feishu.autoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "feishu.cli")
public class FeishuCliProperties {
    public enum Mode {
        NPX,
        WRAPPER
    }

    private boolean enabled = true;
    private Mode mode = Mode.NPX;
    private String command = "npx";
    private String packageName = "@larksuite/cli@latest";
    private String wrapperPath;
    private long timeoutSeconds = 30;
    private int retryTimes = 1;
    private boolean logEnabled = true;
    private String workingDirectory;
    private Set<String> allowedBusinessTypes = new LinkedHashSet<>(
            Set.of("bitable", "document", "calendar", "task")
    );

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.NPX : mode;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getWrapperPath() {
        return wrapperPath;
    }

    public void setWrapperPath(String wrapperPath) {
        this.wrapperPath = wrapperPath;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    public boolean isLogEnabled() {
        return logEnabled;
    }

    public void setLogEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public Set<String> getAllowedBusinessTypes() {
        return allowedBusinessTypes;
    }

    public void setAllowedBusinessTypes(Set<String> allowedBusinessTypes) {
        this.allowedBusinessTypes = allowedBusinessTypes == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(allowedBusinessTypes);
    }
}
