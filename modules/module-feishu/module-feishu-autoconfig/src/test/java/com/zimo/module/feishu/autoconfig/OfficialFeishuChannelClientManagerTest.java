package com.zimo.module.feishu.autoconfig;

import com.zimo.module.feishu.channel.FeishuChannelMessageListener;
import com.zimo.module.feishu.config.FeishuConfigProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfficialFeishuChannelClientManagerTest {

    @Test
    void startDoesNotFailWhenActiveConfigIsMissing() {
        FeishuConfigProvider configProvider = mock(FeishuConfigProvider.class);
        when(configProvider.getActiveConfig()).thenReturn(null);
        FeishuAgentChannelProperties properties = new FeishuAgentChannelProperties();

        OfficialFeishuChannelClientManager manager = new OfficialFeishuChannelClientManager(
                configProvider,
                mock(FeishuChannelMessageListener.class),
                properties);

        manager.start();

        assertThat(manager.isRunning()).isFalse();
    }
}
