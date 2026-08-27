package com.zimo.module.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiPluginRegisterTest {
    @Test
    void exposesAiPluginMetadataForShellMenu() {
        AiPluginRegister register = new AiPluginRegister();

        assertThat(register.getPluginId()).isEqualTo("ai");
        assertThat(register.getApiPrefix()).isEqualTo("/api/ai");
        assertThat(register.getFrontendRoute()).isEqualTo("/ai");
        assertThat(register.getFrontendModule()).isEqualTo("ai");
        assertThat(register.getAgentName()).isEqualTo("ai-agent");
        assertThat(register.getOrder()).isEqualTo(4);
    }
}
