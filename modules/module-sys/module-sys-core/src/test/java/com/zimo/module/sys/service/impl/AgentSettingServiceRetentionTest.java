package com.zimo.module.sys.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.zimo.module.sys.entity.AgentSetting;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 验证短期会话记忆保留期限的默认值与可配置边界。 */
class AgentSettingServiceRetentionTest {

    private static final String RETENTION_KEY = "short-term-retention-days";

    @Test
    void definesSevenDaysAsTheDefaultRetention() {
        AgentSettingServiceImpl service = new AgentSettingServiceImpl();

        assertThat(service.listDefinitions())
                .anySatisfy(definition -> {
                    assertThat(definition.key()).isEqualTo(RETENTION_KEY);
                    assertThat(definition.defaultValue()).isEqualTo("7");
                });
    }

    @Test
    void acceptsOneAnd365Days() {
        AgentSettingServiceImpl service = spy(new AgentSettingServiceImpl());
        doReturn(List.of()).when(service).list();
        doReturn(true).when(service).save(any(AgentSetting.class));

        service.saveSettings(Map.of(RETENTION_KEY, "1"));
        service.saveSettings(Map.of(RETENTION_KEY, "365"));

        verify(service, org.mockito.Mockito.times(2)).save(any(AgentSetting.class));
    }

    @Test
    void rejectsBlankNonIntegerAndOutOfRangeRetentionBeforeDatabaseAccess() {
        AgentSettingServiceImpl service = new AgentSettingServiceImpl();

        for (String value : List.of("", "0", "366", "1.5", "seven")) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> service.saveSettings(Map.of(RETENTION_KEY, value)));
        }
    }
}
