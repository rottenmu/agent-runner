package com.zimo.module.feishu.message;

public interface FeishuMessageClient {
    FeishuMessageResponse sendText(FeishuTextMessageRequest request);
}
