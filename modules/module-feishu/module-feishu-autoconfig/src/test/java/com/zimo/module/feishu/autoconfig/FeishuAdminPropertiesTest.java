package com.zimo.module.feishu.autoconfig;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAdminPropertiesTest {

    @Test
    void shouldUseLocalDebugDefaults() {
        FeishuAdminProperties properties = new FeishuAdminProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getApiToken()).isNull();
    }

    @Test
    void shouldAllowOverrideValues() {
        FeishuAdminProperties properties = new FeishuAdminProperties();
        properties.setEnabled(false);
        properties.setApiToken("secret-token");

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getApiToken()).isEqualTo("secret-token");
    }
}
