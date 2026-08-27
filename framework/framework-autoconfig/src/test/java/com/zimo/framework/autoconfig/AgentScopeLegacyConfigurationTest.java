package com.zimo.framework.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentScopeLegacyConfigurationTest {

    @Test
    void legacyAgentScopeAutoConfigurationIsNotImported() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(input).isNotNull();
            List<String> imports = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .lines()
                    .toList();

            assertThat(imports).doesNotContain("com.zimo.framework.autoconfig.AgentScopeAutoConfiguration");
        }
    }

    @Test
    void legacyAgentScopeClientIsRemovedFromFramework() {
        assertThatThrownBy(() -> Class.forName("com.zimo.framework.autoconfig.AgentScopeClient"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
