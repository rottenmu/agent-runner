package com.zimo.module.feishu.reply;

import java.time.Duration;

public class FeishuStreamReplyOptions {
    private final int chunkSize;
    private final Duration interval;

    public FeishuStreamReplyOptions(int chunkSize, Duration interval) {
        this.chunkSize = chunkSize <= 0 ? 80 : chunkSize;
        this.interval = interval == null || interval.isNegative() ? Duration.ZERO : interval;
    }

    public static FeishuStreamReplyOptions defaults() {
        return new FeishuStreamReplyOptions(80, Duration.ofMillis(300));
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public Duration getInterval() {
        return interval;
    }
}
