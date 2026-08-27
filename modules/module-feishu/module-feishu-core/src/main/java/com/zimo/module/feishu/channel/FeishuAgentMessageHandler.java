package com.zimo.module.feishu.channel;

import com.zimo.module.feishu.reply.FeishuAgentReplyService;

public interface FeishuAgentMessageHandler {
    void handle(FeishuAgentCommandMessage message, FeishuAgentReplyService replyService);

    default boolean requiresInternalAccount() {
        return true;
    }
}
