package com.zimo.framework.ai.autoconfig;

import com.zimo.framework.ai.agent.memory.AgentStateRetentionCleanup;
import com.zimo.framework.ai.agent.memory.ShortTermMemoryRetentionDaysProvider;
import com.zimo.framework.common.storage.FileStorageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Harness 短期会话状态清理自动配置。
 *
 * <p>清理器使用可选的文件存储和应用级保留期限端口；缺少任一运行能力时仍可安全装配。</p>
 *
 * @author Codex
 * @since 2026-09-29
 */
@AutoConfiguration
@EnableScheduling
public class AgentStateRetentionAutoConfiguration {

    /**
     * 创建 AgentState 定时清理器。
     *
     * @param storageProvider 可选的 RocksDB 文件存储
     * @param retentionProvider 可选的应用设置读取端口
     * @return 清理器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentStateRetentionCleanup agentStateRetentionCleanup(
            ObjectProvider<FileStorageService> storageProvider,
            ObjectProvider<ShortTermMemoryRetentionDaysProvider> retentionProvider) {
        return new AgentStateRetentionCleanup(storageProvider, retentionProvider);
    }
}
