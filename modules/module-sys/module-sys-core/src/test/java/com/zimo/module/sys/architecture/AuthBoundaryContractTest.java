package com.zimo.module.sys.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthBoundaryContractTest {

    @Test
    void identityModelMovedOutOfSysPackage() throws IOException {
        Path root = repositoryRoot();

        assertThat(Files.exists(root.resolve("modules/module-sys/module-sys-core/src/main/java/com/xingju/module/sys/entity/SysUser.java"))).isFalse();
        assertThat(Files.exists(root.resolve("modules/module-sys/module-sys-core/src/main/java/com/xingju/module/sys/mapper/SysUserMapper.java"))).isFalse();
        assertThat(Files.exists(root.resolve("modules/module-sys/module-sys-core/src/main/java/com/xingju/module/sys/service/SysRbacService.java"))).isFalse();
        assertThat(read(root.resolve("modules/module-sys/module-sys-core/src/main/java/com/zimo/module/sys/service/impl/SysRbacServiceImpl.java")))
                .contains("com.zimo.module.auth.service.SysRbacService");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("modules/module-sys"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }
}
