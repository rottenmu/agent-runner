package com.zimo.framework.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.framework.common.storage.spi.FileStorageContext;
import com.zimo.framework.common.storage.spi.FileStorageProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

/**
 * FileStorageFactory 路由测试 + 本地磁盘存储功能验证。
 */
class FileStorageFactoryTest {

    @TempDir
    Path tempDir;

    private static final class FakeProvider implements FileStorageProvider {
        private final String engine;

        FakeProvider(String engine) {
            this.engine = engine;
        }

        @Override
        public String engine() {
            return engine;
        }

        @Override
        public FileStorageService create(FileStorageContext context) {
            return new FileStorageService() {
                @Override public void store(String key, byte[] data) { }
                @Override public byte[] get(String key) { return new byte[0]; }
                @Override public boolean delete(String key) { return true; }
                @Override public List<String> list(String prefix) { return List.of(); }
                @Override public boolean exists(String key) { return true; }
            };
        }
    }

    @Test
    void routesToConfiguredEngine() {
        FileStorageFactory factory = new FileStorageFactory(
                List.of(new FakeProvider("rocksdb"), new FakeProvider("minio")),
                new FileStorageContext("rocksdb", "data/rocksdb", java.util.Map.of()));
        assertThat(factory.create("minio")).isNotNull();
        assertThat(factory.create("rocksdb")).isNotNull();
    }

    @Test
    void fallsBackToDefaultWhenEngineUnknown() {
        FileStorageFactory factory = new FileStorageFactory(
                List.of(new FakeProvider("rocksdb")),
                new FileStorageContext("rocksdb", "data/rocksdb", java.util.Map.of()));
        assertThat(factory.create("oss")).isNotNull(); // 未知引擎 → 回退 rocksdb
    }
}
