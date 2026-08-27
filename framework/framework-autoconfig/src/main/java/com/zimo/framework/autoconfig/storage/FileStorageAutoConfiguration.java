package com.zimo.framework.autoconfig.storage;

import com.zimo.framework.common.storage.FileStorageFactory;
import com.zimo.framework.common.storage.FileStorageService;
import com.zimo.framework.common.storage.spi.FileStorageContext;
import com.zimo.framework.common.storage.spi.FileStorageProvider;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 文件存储自动装配（SPI 可插拔）。
 *
 * <p>注册存储引擎 Provider 与 {@link FileStorageFactory}，按 {@code framework.storage.engine}
 * 路由生成 {@link FileStorageService} Bean。接入新文件存储中间件（MinIO/S3/OSS）只需
 * 实现 {@link FileStorageProvider} 并注册为 Bean。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-20
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "framework.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({FileStorageProperties.class, RocksdbStorageProperties.class})
public class FileStorageAutoConfiguration {

    /** 存储装配上下文（引擎 + 基础路径）。 */
    @Bean
    @ConditionalOnMissingBean
    public FileStorageContext fileStorageContext(FileStorageProperties properties) {
        return new FileStorageContext(properties.getEngine(), properties.getBasePath(), java.util.Map.of());
    }

    /** 内置存储后端：本地磁盘（engine=local，文件系统中间件参考实现）。 */
    @Bean
    @ConditionalOnMissingBean
    public FileStorageProvider localFileStorageProvider() {
        return new LocalFileStorageProvider();
    }

    /** 内置存储后端：RocksDB（engine=rocksdb，默认；仅当 RocksDB 类可用且 rocksdb 开关开启）。 */
    @Bean
    @ConditionalOnClass(name = "org.rocksdb.RocksDB")
    @ConditionalOnProperty(prefix = "framework.storage.rocksdb", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "rocksdbFileStorageProvider")
    public FileStorageProvider rocksdbFileStorageProvider() {
        return new RocksdbFileStorageProvider();
    }

    /** 存储工厂：按 framework.storage.engine 路由 Provider（未知引擎回退 rocksdb）。 */
    @Bean
    @ConditionalOnMissingBean
    public FileStorageFactory fileStorageFactory(
            List<FileStorageProvider> providers, FileStorageContext fileStorageContext) {
        return new FileStorageFactory(providers, fileStorageContext);
    }

    /** 文件存储服务（工厂输出；外部可自行注册 FileStorageService 覆盖）。
     * 销毁由 Spring 自动检测（RocksdbFileStorageService.close 等）。 */
    @Bean
    @ConditionalOnMissingBean(FileStorageService.class)
    public FileStorageService fileStorageService(
            FileStorageFactory fileStorageFactory, FileStorageProperties properties) {
        return fileStorageFactory.create(properties.getEngine());
    }
}
