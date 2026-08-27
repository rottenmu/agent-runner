package com.zimo.framework.autoconfig.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RocksDB 文件存储配置属性。
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@ConfigurationProperties(prefix = "framework.storage.rocksdb")
public class RocksdbStorageProperties {

    /** 数据存储路径，默认为应用工作目录下的 data/rocksdb */
    private String path = "data/rocksdb";

    /** 是否启用 RocksDB 存储 */
    private boolean enabled = true;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
