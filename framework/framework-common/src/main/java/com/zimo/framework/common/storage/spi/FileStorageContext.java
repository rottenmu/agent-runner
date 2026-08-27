package com.zimo.framework.common.storage.spi;

import java.util.Map;

/**
 * 文件存储装配上下文：承载存储中间件创建所需的配置。
 *
 * <p>存储 Provider（{@link FileStorageProvider}）只依赖本上下文，不感知 Spring 容器细节。</p>
 *
 * @param engine    存储引擎名（如 rocksdb / local / minio / s3 / oss）
 * @param basePath  基础路径（rocksdb 数据目录 / local 根目录 / 远端 bucket 前缀等）
 * @param options   中间件专属配置（端点、密钥、bucket 等，由 Provider 自行解析）
 */
public record FileStorageContext(
        String engine,
        String basePath,
        Map<String, String> options) {

    public FileStorageContext {
        engine = engine == null ? "rocksdb" : engine;
        basePath = basePath == null ? "data/rocksdb" : basePath;
        options = options == null ? Map.of() : options;
    }
}
