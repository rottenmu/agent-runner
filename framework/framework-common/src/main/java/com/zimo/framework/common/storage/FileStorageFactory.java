package com.zimo.framework.common.storage;

import com.zimo.framework.common.storage.spi.FileStorageContext;
import com.zimo.framework.common.storage.spi.FileStorageProvider;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件存储工厂：按配置（{@code framework.storage.engine}）从已注册的存储中间件 SPI 中
 * 路由并创建对应 {@link FileStorageService}；未知引擎回退默认 rocksdb 并告警。
 */
public class FileStorageFactory {

    private static final Logger log = LoggerFactory.getLogger(FileStorageFactory.class);

    /** 默认存储引擎（内置 RocksDB）。 */
    public static final String DEFAULT_ENGINE = "rocksdb";

    private final Map<String, FileStorageProvider> providers;
    private final FileStorageContext context;

    public FileStorageFactory(List<FileStorageProvider> providers, FileStorageContext context) {
        this.providers = providers == null ? Map.of()
                : providers.stream().collect(Collectors.toMap(
                        FileStorageProvider::engine, Function.identity(),
                        (left, right) -> left, java.util.LinkedHashMap::new));
        this.context = context;
    }

    /** 按配置创建文件存储服务（未知引擎回退默认 rocksdb）。 */
    public FileStorageService create(String configuredEngine) {
        String engine = configuredEngine == null || configuredEngine.isBlank()
                ? DEFAULT_ENGINE : configuredEngine;
        FileStorageProvider provider = providers.get(engine);
        if (provider == null) {
            log.warn("未找到文件存储引擎 [{}]，回退默认 [{}]，已注册引擎：{}",
                    engine, DEFAULT_ENGINE, providers.keySet());
            provider = providers.get(DEFAULT_ENGINE);
        }
        if (provider == null) {
            throw new IllegalStateException("无可用文件存储 Provider");
        }
        return provider.create(new FileStorageContext(engine, context.basePath(), context.options()));
    }
}
