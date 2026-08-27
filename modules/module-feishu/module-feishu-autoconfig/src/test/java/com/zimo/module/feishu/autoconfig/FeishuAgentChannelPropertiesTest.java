package com.zimo.module.feishu.autoconfig;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAgentChannelPropertiesTest {

    @Test
    void bindsChannelPropertiesWithDefaultsAndOverrides() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("feishu.agent.channel.enabled", "false")
                .withProperty("feishu.agent.channel.auto-start", "false")
                .withProperty("feishu.agent.channel.stream.chunk-size", "12")
                .withProperty("feishu.agent.channel.stream.interval-millis", "25")
                .withProperty("feishu.agent.channel.log.record-raw-payload", "false");

        FeishuAgentChannelProperties properties = Binder.get(environment)
                .bind("feishu.agent.channel", Bindable.of(FeishuAgentChannelProperties.class))
                .orElseThrow(() -> new IllegalStateException("feishu channel properties bind failed"));

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isAutoStart()).isFalse();
        assertThat(properties.isAutoReconnect()).isTrue();
        assertThat(properties.getStream().getChunkSize()).isEqualTo(12);
        assertThat(properties.getStream().getIntervalMillis()).isEqualTo(25);
        assertThat(properties.getLog().isRecordRawPayload()).isFalse();
        assertThat(properties.getLog().isEnabled()).isTrue();
    }
}
