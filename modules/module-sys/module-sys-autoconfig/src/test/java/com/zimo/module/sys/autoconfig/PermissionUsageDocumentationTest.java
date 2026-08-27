package com.zimo.module.sys.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PermissionUsageDocumentationTest {

    @Test
    void ymlExampleDocumentsEveryPermissionSwitch() throws IOException {
        String yml = Files.readString(moduleSysRoot()
                .resolve("module-sys-autoconfig/src/main/resources/examples/permission-application.yml"), StandardCharsets.UTF_8);

        assertThat(yml).contains("plugin:");
        assertThat(yml).contains("sys:");
        assertThat(yml).contains("manufacture:");
        assertThat(yml).contains("permission:");
        assertThat(yml).contains("data-scope:");
        assertThat(yml).contains("field-mask:");
        assertThat(yml).contains("operation-log:");
        assertThat(yml).contains("whitelist:");
    }

    private static Path moduleSysRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path direct = current.resolve("modules/module-sys");
            if (Files.isDirectory(direct)) {
                return direct;
            }
            if (current.endsWith(Path.of("modules/module-sys"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate modules/module-sys");
    }
}
