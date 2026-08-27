package com.zimo.module.feishu;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuPluginRegisterTest {

    @Test
    void exposesStablePluginMetadata() {
        FeishuPluginRegister register = new FeishuPluginRegister();

        assertThat(register.getPluginId()).isEqualTo("feishu");
        assertThat(register.getPluginName()).isEqualTo("飞书平台");
        assertThat(register.getApiPrefix()).isEqualTo("/api/feishu");
        assertThat(register.getFrontendRoute()).isEqualTo("/integration/feishu");
        assertThat(register.getFrontendModule()).isEqualTo("feishu");
        assertThat(register.getAgentName()).isEqualTo("feishu-agent");
        assertThat(register.getOrder()).isEqualTo(5);
    }
}
