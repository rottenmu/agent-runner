package com.zimo.module.auth.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthAutoConfigurationSourceTest {

    @Test
    void authAutoConfigurationOwnsSaTokenInterceptor() throws IOException {
        Path root = repositoryRoot();
        String authAutoConfiguration = read(root.resolve(
                "modules/module-auth/module-auth-autoconfig/src/main/java/com/zimo/module/auth/autoconfig/AuthAutoConfiguration.java"));
        String frameworkImports = read(root.resolve(
                "framework/framework-autoconfig/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"));

        assertThat(authAutoConfiguration).contains("new SaInterceptor");
        assertThat(authAutoConfiguration).contains("StpUtil.checkLogin()");
        assertThat(frameworkImports).doesNotContain("SaTokenConfig");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null
                && !(Files.exists(current.resolve("pom.xml"))
                        && Files.isDirectory(current.resolve("framework"))
                        && Files.isDirectory(current.resolve("modules")))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }
}
