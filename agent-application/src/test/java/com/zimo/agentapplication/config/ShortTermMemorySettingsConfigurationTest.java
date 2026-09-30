package com.zimo.agentapplication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zimo.framework.ai.agent.memory.ShortTermMemoryRetentionDaysProvider;
import com.zimo.module.sys.service.AgentSettingService;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** 验证短期记忆保留设置从系统配置动态传递到清理端口。 */
class ShortTermMemorySettingsConfigurationTest {

    @Test
    void returnsDefaultAndCurrentValidSettingWithInvalidValuesFallingBackToSevenDays() {
        AgentSettingService settingService = mock(AgentSettingService.class);
        AtomicReference<Map<String, String>> settings = new AtomicReference<>(Map.of());
        when(settingService.getEffectiveSettings()).thenAnswer(invocation -> settings.get());
        ShortTermMemoryRetentionDaysProvider provider =
                new ShortTermMemorySettingsConfiguration().shortTermMemoryRetentionDaysProvider(settingService);

        assertThat(provider.getRetentionDays()).isEqualTo(7);

        settings.set(Map.of("short-term-retention-days", "14"));
        assertThat(provider.getRetentionDays()).isEqualTo(14);

        settings.set(Map.of("short-term-retention-days", "366"));
        assertThat(provider.getRetentionDays()).isEqualTo(7);

        settings.set(Map.of("short-term-retention-days", "invalid"));
        assertThat(provider.getRetentionDays()).isEqualTo(7);
    }
}
