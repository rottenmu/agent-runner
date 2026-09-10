package com.zimo.module.feishu.autoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "feishu.agent.gateway")
public class FeishuAgentGatewayProperties {
    private boolean enabled = true;
    private boolean rateLimitEnabled = true;
    private long rateLimitWindowSeconds = 60;
    private int rateLimitMaxRequests = 10;
    private boolean archiveEnabled = true;
    private boolean progressReplyEnabled = true;
    private String archiveAppToken;
    private String archiveTableId;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    public long getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    public void setRateLimitWindowSeconds(long rateLimitWindowSeconds) {
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
    }

    public int getRateLimitMaxRequests() {
        return rateLimitMaxRequests;
    }

    public void setRateLimitMaxRequests(int rateLimitMaxRequests) {
        this.rateLimitMaxRequests = rateLimitMaxRequests;
    }

    public boolean isArchiveEnabled() {
        return archiveEnabled;
    }

    public void setArchiveEnabled(boolean archiveEnabled) {
        this.archiveEnabled = archiveEnabled;
    }

    public boolean isProgressReplyEnabled() {
        return progressReplyEnabled;
    }

    public void setProgressReplyEnabled(boolean progressReplyEnabled) {
        this.progressReplyEnabled = progressReplyEnabled;
    }

    public String getArchiveAppToken() {
        return archiveAppToken;
    }

    public void setArchiveAppToken(String archiveAppToken) {
        this.archiveAppToken = archiveAppToken;
    }

    public String getArchiveTableId() {
        return archiveTableId;
    }

    public void setArchiveTableId(String archiveTableId) {
        this.archiveTableId = archiveTableId;
    }
}
