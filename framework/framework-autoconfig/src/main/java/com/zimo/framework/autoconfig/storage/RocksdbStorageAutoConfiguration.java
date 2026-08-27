package com.zimo.framework.autoconfig.storage;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;



/**
 * RocksDB 文件存储自动装配（配置属性容器）。
 *
 * <p>存储服务统一由 {@link FileStorageAutoConfiguration} 的 {@link FileStorageFactory}
 * 按 {@code framework.storage.engine} 路由输出（engine=rocksdb 时使用本模块的
 * {@link RocksdbFileStorageProvider}）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@AutoConfiguration
@EnableConfigurationProperties(RocksdbStorageProperties.class)
public class RocksdbStorageAutoConfiguration {
}
