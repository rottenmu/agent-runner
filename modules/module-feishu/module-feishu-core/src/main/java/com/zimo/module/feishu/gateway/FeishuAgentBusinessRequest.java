package com.zimo.module.feishu.gateway;

import com.zimo.module.feishu.channel.FeishuAgentCommandMessage;

import java.util.Objects;

public class FeishuAgentBusinessRequest {
    private final FeishuAgentCommandMessage message;

    public FeishuAgentBusinessRequest(FeishuAgentCommandMessage message) {
        this.message = Objects.requireNonNull(message, "message must not be null");
    }

    public FeishuAgentCommandMessage getMessage() {
        return message;
    }

    public String getCommandText() {
        return message.getCommandText();
    }

    public String getTenantKey() {
        return message.getTenantKey();
    }

    public String getSenderUserId() {
        return message.getSenderUserId();
    }
}
