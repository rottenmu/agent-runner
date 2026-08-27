package com.zimo.module.sys.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PermissionAutoConfigurationResourceTest {

    @Test
    void publishesAutoConfigurationThroughBootImportsAndSpringFactories() throws IOException {
        String imports = read("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        String factories = read("META-INF/spring.factories");

        assertThat(imports).contains("com.zimo.module.sys.autoconfig.PermissionAutoConfiguration");
        assertThat(factories).contains("org.springframework.boot.autoconfigure.EnableAutoConfiguration");
        assertThat(factories).contains("com.zimo.module.sys.autoconfig.PermissionAutoConfiguration");
    }

    private String read(String path) throws IOException {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
