package com.zimo.framework.autoconfig.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储通用配置（引擎选择）。
 *
 * @author WorkBuddy
 * @since 2026-08-20
 */
@ConfigurationProperties(prefix = "framework.storage")
public class FileStorageProperties {

    /** 存储引擎：rocksdb（默认）/ local / minio / s3 / oss 等（由 FileStorageProvider SPI 提供） */
    private String engine = "rocksdb";

    /** 基础路径：rocksdb 数据目录 / local 根目录（远端中间件可为 bucket 前缀） */
    private String basePath = "data/rocksdb";

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }
}
