package com.zimo.module.ds.storage;

/**
 * 存储后端 SPI：将任意存储引擎（关系库 / 列式库 / 文件存储）插拔化。
 *
 * <p>接入新引擎只需三步：</p>
 * <ol>
 *   <li>实现本接口并声明 {@link #engine()}（如 {@code "mysql"} / {@code "duckdb"}）；</li>
 *   <li>将实现注册为 Spring Bean（或加入 Provider 集合）；</li>
 *   <li>配置引擎名切换。</li>
 * </ol>
 *
 * <p>业务代码只依赖具体仓储接口（如 {@code OltpMemoryRepository}），
 * 经 {@link StorageRouter} 路由到实际后端，与底层数据库实现完全解耦。</p>
 *
 * @param <T> 该后端产出的仓储 / 服务类型（如 OLTP 仓储、OLAP 分析仓储）
 */
public interface StorageProvider<T> {

    /** 引擎名（配置匹配值，如 h2 / mysql / arrow / duckdb）。 */
    String engine();

    /**
     * 创建仓储实例。
     *
     * @param context 装配上下文（含数据源、数据文件路径与引擎专属配置）
     * @return 仓储实例
     */
    T create(StorageContext context);
}
