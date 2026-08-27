package com.zimo.framework.autoconfig.storage;

import com.zimo.framework.common.storage.FileStorageService;
import com.zimo.framework.common.storage.spi.FileStorageContext;
import com.zimo.framework.common.storage.spi.FileStorageProvider;

/**
 * RocksDB 文件存储后端（engine = {@code rocksdb}，内置默认）。
 */
public class RocksdbFileStorageProvider implements FileStorageProvider {

    @Override
    public String engine() {
        return "rocksdb";
    }

    @Override
    public FileStorageService create(FileStorageContext context) {
        try {
            return new RocksdbFileStorageService(context.basePath());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("RocksDB 文件存储初始化失败: " + context.basePath(), e);
        }
    }
}
