package com.zimo.framework.autoconfig.storage;

import com.zimo.framework.common.storage.FileStorageService;
import com.zimo.framework.common.storage.spi.FileStorageContext;
import com.zimo.framework.common.storage.spi.FileStorageProvider;
import com.zimo.framework.autoconfig.storage.local.LocalFileStorageService;

/**
 * 本地磁盘文件存储后端（engine = {@code local}）。
 *
 * <p>用于无 RocksDB 依赖场景或文件系统直连；也作为接入其他文件存储中间件的参考范式。</p>
 */
public class LocalFileStorageProvider implements FileStorageProvider {

    @Override
    public String engine() {
        return "local";
    }

    @Override
    public FileStorageService create(FileStorageContext context) {
        return new LocalFileStorageService(context.basePath());
    }
}
