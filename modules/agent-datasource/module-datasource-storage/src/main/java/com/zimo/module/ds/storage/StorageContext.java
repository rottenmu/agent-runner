package com.zimo.module.ds.storage;

import java.nio.file.Path;
import java.util.Map;
import javax.sql.DataSource;

/**
 * 存储装配上下文：承载底层存储实现创建所需的依赖与配置，屏蔽各后端的装配差异。
 *
 * <p>存储 Provider（{@link StorageProvider}）只依赖本上下文，不感知 Spring 容器与配置类细节，
 * 便于 SPI 独立实现与测试。</p>
 *
 * <p>字段覆盖三类后端形态：JDBC 类（{@link #jdbcUrl()} + {@link #dataSource()}）、
 * 列式/文件类（{@link #dataFilePath()}）、以及需要额外参数的引擎（{@link #options()}）。</p>
 *
 * @param engine       本后端引擎名（如 h2 / mysql / arrow / duckdb）
 * @param jdbcUrl      JDBC 连接串（JDBC 类引擎使用，可为 {@code null}）
 * @param dataFilePath 数据文件路径（列式 / 文件类引擎使用，可为 {@code null}）
 * @param dataSource   JDBC 数据源（JDBC 类引擎使用，非 JDBC 引擎可为 {@code null}）
 * @param options      引擎专属配置（由 Provider 自行解析，可为 {@code null} 表示无）
 */
public record StorageContext(
        String engine,
        String jdbcUrl,
        String dataFilePath,
        DataSource dataSource,
        Map<String, String> options) {

    public StorageContext {
        options = options == null ? Map.of() : options;
    }

    /** 便捷构造：无引擎专属配置。 */
    public StorageContext(String engine, String jdbcUrl, String dataFilePath, DataSource dataSource) {
        this(engine, jdbcUrl, dataFilePath, dataSource, Map.of());
    }

    /** 数据文件路径对象（列式 / 文件类引擎使用）；未配置时返回 {@code null}。 */
    public Path dataFile() {
        return dataFilePath == null ? null : Path.of(dataFilePath);
    }

    /**
     * 复制上下文并替换引擎名。
     *
     * <p>路由回退时使用：确保 Provider 收到的是**实际生效**的引擎名，
     * 而非配置中未注册的原始值。</p>
     *
     * @param newEngine 实际生效的引擎名
     * @return 替换引擎名后的新上下文
     */
    public StorageContext withEngine(String newEngine) {
        return new StorageContext(newEngine, jdbcUrl, dataFilePath, dataSource, options);
    }
}
