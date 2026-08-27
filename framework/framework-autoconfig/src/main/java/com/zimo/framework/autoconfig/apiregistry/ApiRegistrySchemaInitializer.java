package com.zimo.framework.autoconfig.apiregistry;

import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * API 注册表 SQLite 数据结构初始化器。
 *
 * <p>表缺失时执行完整 DDL；表已存在时不重建，仅维护原子去重所需的 {@code hash} 唯一索引。
 * 历史重复记录保留最新行，旧行通过逻辑删除和重签名归档，不执行物理删除。</p>
 *
 * @author Codex
 * @since 2026-07-21
 */
public class ApiRegistrySchemaInitializer {

    private static final String TABLE_NAME = "api_registry";
    private static final String HASH_INDEX_NAME = "uk_api_registry_hash";
    private static final String TABLE_EXISTS_SQL = """
            SELECT COUNT(*)
            FROM sqlite_master
            WHERE type = 'table' AND name = ?
            """;

    private static final String INDEX_EXISTS_SQL = """
            SELECT COUNT(*)
            FROM sqlite_master
            WHERE type = 'index' AND tbl_name = ? AND name = ?
            """;
    private static final String ARCHIVE_DUPLICATES_SQL = """
            UPDATE api_registry
            SET is_deleted = 1, updated_at = datetime('now')
            WHERE id NOT IN (
              SELECT MIN(id) FROM api_registry GROUP BY hash
            )
            AND hash IN (
              SELECT hash FROM api_registry GROUP BY hash HAVING COUNT(*) > 1
            )
            """;

    private static final String CREATE_HASH_INDEX_SQL =
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_api_registry_hash ON api_registry (hash)";

    private final JdbcOperations jdbcOperations;
    private final Resource schemaResource;

    /**
     * 创建 API 注册表结构初始化器。
     * @param jdbcOperations 当前应用目标数据源的 JDBC 操作对象
     * @param schemaResource SQLite 建表脚本资源
     */
    public ApiRegistrySchemaInitializer(JdbcOperations jdbcOperations, Resource schemaResource) {
        this.jdbcOperations = Objects.requireNonNull(jdbcOperations, "jdbcOperations must not be null");
        this.schemaResource = Objects.requireNonNull(schemaResource, "schemaResource must not be null");
    }

    /**
     * 创建缺失的 API 注册表；表已存在时仅维护唯一签名索引。
     *
     * @return {@code true} 表示本次创建了数据表，{@code false} 表示表已存在
     * @throws IllegalStateException 无法读取建表脚本时抛出
     * @throws DataAccessException 表或索引结构检查失败时抛出
     */
    public boolean initializeIfNecessary() {
        Integer count = jdbcOperations.queryForObject(TABLE_EXISTS_SQL, Integer.class, TABLE_NAME);
        if (count != null && count > 0) {
            ensureHashUniqueIndex();
            return false;
        }
        jdbcOperations.execute(readSchemaSql());
        return true;
    }

    private void ensureHashUniqueIndex() {
        if (hasHashUniqueIndex()) {
            return;
        }
        jdbcOperations.execute(ARCHIVE_DUPLICATES_SQL);
        try {
            jdbcOperations.execute(CREATE_HASH_INDEX_SQL);
        } catch (DataAccessException exception) {
            if (!hasHashUniqueIndex()) {
                throw exception;
            }
        }
    }

    private boolean hasHashUniqueIndex() {
        Integer count = jdbcOperations.queryForObject(
                INDEX_EXISTS_SQL, Integer.class, TABLE_NAME, HASH_INDEX_NAME);
        return count == null || count > 0;
    }

    private String readSchemaSql() {
        try {
            return schemaResource.getContentAsString(StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw new IllegalStateException("读取 API 注册表建表脚本失败", exception);
        }
    }
}
