package com.zimo.agentapplication.config;

import com.zimo.framework.ai.agent.memory.ShortTermMemoryRetentionDaysProvider;
import com.zimo.module.sys.service.AgentSettingService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 应用级短期记忆设置桥接配置。
 *
 * <p>将系统模块保存的智能体设置提供给 framework-ai 清理任务，维持插件与框架的依赖方向。</p>
 *
 * @author Codex
 * @since 2026-09-29
 */
@Configuration(proxyBeanMethods = false)
public class ShortTermMemorySettingsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ShortTermMemorySettingsConfiguration.class);
    /** 系统设置中的短期会话保留期限键。 */
    private static final String RETENTION_KEY = "short-term-retention-days";
    /** 缺少有效数据库配置时采用的默认值。 */
    private static final int DEFAULT_RETENTION_DAYS = 7;

    /**
     * 注册从系统设置读取短期记忆保留期限的适配器。
     *
     * @param settingService 智能体运行设置服务，负责返回默认值与数据库覆盖值
     * @return 定时读取当前有效设置的保留期限提供器
     */
    @Bean
    public ShortTermMemoryRetentionDaysProvider shortTermMemoryRetentionDaysProvider(
            AgentSettingService settingService) {
        return () -> resolveRetentionDays(settingService.getEffectiveSettings());
    }

    /** 将系统设置转换为受范围约束的天数，数据库异常值回退至默认期限。 */
    private int resolveRetentionDays(Map<String, String> settings) {
        String configured = settings.get(RETENTION_KEY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_RETENTION_DAYS;
        }
        try {
            int days = Integer.parseInt(configured.trim());
            if (days >= 1 && days <= 365) {
                return days;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid short-term memory retention setting; use default {}", DEFAULT_RETENTION_DAYS);
            return DEFAULT_RETENTION_DAYS;
        }
        log.warn("Short-term memory retention setting is outside 1..365; use default {}", DEFAULT_RETENTION_DAYS);
        return DEFAULT_RETENTION_DAYS;
    }
}
