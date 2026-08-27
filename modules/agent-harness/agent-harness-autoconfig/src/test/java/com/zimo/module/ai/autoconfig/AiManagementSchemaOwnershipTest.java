package com.zimo.module.ai.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AiManagementSchemaOwnershipTest {

    @Test
    void doesNotInitializeManagementSchemaInsideApplication() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java", "com", "zimo", "module", "ai", "autoconfig");
        Path initializer = sourceRoot.resolve("AiManagementSchemaInitializer.java");
        Path autoConfiguration = sourceRoot.resolve("AiSkillAdminAutoConfiguration.java");

        assertThat(initializer).doesNotExist();
        assertThat(Files.readString(autoConfiguration))
                .doesNotContain("AiManagementSchemaInitializer")
                .doesNotContain("initMethod = \"initialize\"");
    }

    @Test
    void modelConfigDoesNotUseJdbcOrInitializeSchema() throws IOException {
        Path modelSourceRoot = Path.of("..", "module-ai-core", "src", "main", "java",
                "com", "zimo", "module", "ai", "modelconfig");
        Path initializer = modelSourceRoot.resolve("AiModelConfigSchemaInitializer.java");
        Path autoConfiguration = Path.of("src", "main", "java", "com", "zimo", "module", "ai",
                "autoconfig", "AiModelConfigAutoConfiguration.java");

        assertThat(initializer).doesNotExist();
        assertThat(Files.readString(autoConfiguration))
                .doesNotContain("JdbcTemplate")
                .doesNotContain("JdbcOperations")
                .doesNotContain("initMethod = \"initialize\"");
    }
}