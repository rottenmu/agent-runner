package com.zimo.module.feishu.gateway;

import com.zimo.module.feishu.channel.FeishuAgentCommandMessage;
import com.zimo.module.feishu.channel.FeishuAgentMessageHandler;
import com.zimo.module.feishu.reply.FeishuAgentReplyService;

import java.util.Objects;

public class FeishuAgentGatewayMessageHandler implements FeishuAgentMessageHandler {
    private final FeishuAgentDispatchService dispatchService;

    public FeishuAgentGatewayMessageHandler(FeishuAgentDispatchService dispatchService) {
        this.dispatchService = Objects.requireNonNull(dispatchService, "dispatchService must not be null");
    }

    @Override
    public void handle(FeishuAgentCommandMessage message, FeishuAgentReplyService replyService) {
        dispatchService.dispatch(message, replyService);
    }
}
