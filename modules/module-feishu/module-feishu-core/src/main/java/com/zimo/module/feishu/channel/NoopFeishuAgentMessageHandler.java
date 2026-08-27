package com.zimo.module.feishu.channel;

import com.zimo.module.feishu.reply.FeishuAgentReplyService;

public class NoopFeishuAgentMessageHandler implements FeishuAgentMessageHandler {
    @Override
    public void handle(FeishuAgentCommandMessage message, FeishuAgentReplyService replyService) {
        if (!message.hasCommandText()) {
            replyService.replyText(message.getMessageId(), "Please enter a command.");
            return;
        }
        replyService.replyText(message.getMessageId(), "Command received: " + message.getCommandText());
    }
}
