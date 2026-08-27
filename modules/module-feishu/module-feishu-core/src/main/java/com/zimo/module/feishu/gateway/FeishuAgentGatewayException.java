package com.zimo.module.feishu.gateway;

public class FeishuAgentGatewayException extends RuntimeException {
    public FeishuAgentGatewayException(String message) {
        super(message);
    }

    public FeishuAgentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
