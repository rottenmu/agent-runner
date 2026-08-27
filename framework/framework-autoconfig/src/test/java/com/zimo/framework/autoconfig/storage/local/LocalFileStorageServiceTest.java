package com.zimo.framework.autoconfig.storage.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

/**
 * LocalFileStorageService（本地磁盘存储参考实现）功能测试。
 */
class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storeGetDeleteListExists() {
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString());
        String key = "agent-memory/demo/MEMORY.md";
        byte[] content = "# 智能体记忆\n".getBytes(StandardCharsets.UTF_8);

        storage.store(key, content);
        assertThat(storage.exists(key)).isTrue();
        assertThat(new String(storage.get(key), StandardCharsets.UTF_8)).startsWith("# 智能体记忆");

        assertThat(storage.list("agent-memory/demo/")).containsExactly(key);
        assertThat(storage.delete(key)).isTrue();
        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    void rejectsPathTraversal() {
        LocalFileStorageService storage = new LocalFileStorageService(tempDir.toString());
        assertThatThrownBy(() -> storage.store("../escape.txt", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
