package com.zimo.module.feishu.autoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feishu.agent.channel")
public class FeishuAgentChannelProperties {
    private boolean enabled = true;
    private boolean autoStart = true;
    private boolean autoReconnect = true;
    private long awaitReadyTimeoutSeconds = 10;
    private final Stream stream = new Stream();
    private final Log log = new Log();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public long getAwaitReadyTimeoutSeconds() {
        return awaitReadyTimeoutSeconds;
    }

    public void setAwaitReadyTimeoutSeconds(long awaitReadyTimeoutSeconds) {
        this.awaitReadyTimeoutSeconds = awaitReadyTimeoutSeconds;
    }

    public Stream getStream() {
        return stream;
    }

    public Log getLog() {
        return log;
    }

    public static class Stream {
        private int chunkSize = 80;
        private long intervalMillis = 300;

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public long getIntervalMillis() {
            return intervalMillis;
        }

        public void setIntervalMillis(long intervalMillis) {
            this.intervalMillis = intervalMillis;
        }
    }

    public static class Log {
        private boolean enabled = true;
        private boolean recordRawPayload = true;
        private boolean recordReplyPayload = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRecordRawPayload() {
            return recordRawPayload;
        }

        public void setRecordRawPayload(boolean recordRawPayload) {
            this.recordRawPayload = recordRawPayload;
        }

        public boolean isRecordReplyPayload() {
            return recordReplyPayload;
        }

        public void setRecordReplyPayload(boolean recordReplyPayload) {
            this.recordReplyPayload = recordReplyPayload;
        }
    }
}
