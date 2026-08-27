package com.zimo.module.feishu.event;

public class NoopFeishuEventHandler implements FeishuEventHandler {
    @Override
    public void handleBotMention(FeishuBotMentionEvent event) {
        // Default extension point: applications can provide a real handler bean.
    }
}
