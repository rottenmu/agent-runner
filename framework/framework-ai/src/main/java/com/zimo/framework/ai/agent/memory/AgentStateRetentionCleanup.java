package com.zimo.framework.ai.agent.memory;

import com.zimo.framework.common.storage.FileStorageService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Harness 短期会话状态定时清理器。
 *
 * <p>每小时读取最新保留期限并清除过期的 AgentState；缺少存储时跳过本轮，设置读取失败时回退为 7 天。</p>
 *
 * @author Codex
 * @since 2026-09-29
 */
public class AgentStateRetentionCleanup {

    private static final Logger log = LoggerFactory.getLogger(AgentStateRetentionCleanup.class);
    /** 系统设置未接入或无效时使用的默认保留天数。 */
    private static final int DEFAULT_RETENTION_DAYS = 7;

    private final ObjectProvider<FileStorageService> storageProvider;
    private final ObjectProvider<ShortTermMemoryRetentionDaysProvider> retentionProvider;

    /**
     * 创建清理器。
     *
     * @param storageProvider RocksDB 文件存储提供器，可为空以支持未启用持久化的应用
     * @param retentionProvider 动态保留期限提供器，可为空；为空时使用 7 天
     */
    public AgentStateRetentionCleanup(
            ObjectProvider<FileStorageService> storageProvider,
            ObjectProvider<ShortTermMemoryRetentionDaysProvider> retentionProvider) {
        this.storageProvider = storageProvider;
        this.retentionProvider = retentionProvider;
    }

    /**
     * 按当前有效保留期限清除过期会话状态。
     *
     * <p>每小时扫描一次；启动后先等待一小时，避免启动过程执行全量键扫描。缺少存储时跳过本轮，失败时记录日志并等待下轮重试。</p>
     */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 3_600_000L)
    public void cleanupExpiredSessions() {
        FileStorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            return;
        }
        try {
            int retentionDays = resolveRetentionDays();
            int deleted = new RocksdbAgentStateStore(storage)
                    .cleanupExpiredSessions(Instant.now(), retentionDays);
            if (deleted > 0) {
                log.info("AgentState cleanup removed {} idle sessions (retentionDays={})", deleted, retentionDays);
            }
        } catch (RuntimeException e) {
            log.warn("AgentState cleanup failed; it will retry on the next scheduled run", e);
        }
    }

    /** 读取动态设置并回退非法值，保证清理周期使用的期限在允许范围内。 */
    private int resolveRetentionDays() {
        ShortTermMemoryRetentionDaysProvider provider = retentionProvider.getIfAvailable();
        if (provider == null) {
            return DEFAULT_RETENTION_DAYS;
        }
        try {
            int days = provider.getRetentionDays();
            if (days >= 1 && days <= 365) {
                return days;
            }
            log.warn("Invalid short-term memory retention days {}; use default {}", days, DEFAULT_RETENTION_DAYS);
        } catch (RuntimeException e) {
            log.warn("Cannot read short-term memory retention setting; use default {}", DEFAULT_RETENTION_DAYS, e);
        }
        return DEFAULT_RETENTION_DAYS;
    }
}
