package com.zimo.module.feishu.channel;

public interface FeishuChannelClientManager {
    void start();

    void stop();

    boolean isRunning();
}
