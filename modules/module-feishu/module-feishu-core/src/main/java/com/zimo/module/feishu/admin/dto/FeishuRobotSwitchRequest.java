package com.zimo.module.feishu.admin.dto;

public class FeishuRobotSwitchRequest {
    private boolean enabled;
    private Long configId;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
    }
}
