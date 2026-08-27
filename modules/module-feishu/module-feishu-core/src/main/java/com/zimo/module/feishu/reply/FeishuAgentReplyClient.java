package com.zimo.module.feishu.reply;

import com.zimo.module.feishu.message.FeishuMessageResponse;

public interface FeishuAgentReplyClient {
    FeishuMessageResponse replyText(String messageId, String text);

    FeishuMessageResponse updateText(String messageId, String text);

    FeishuMessageResponse replyCard(String messageId, String cardJson);
}
