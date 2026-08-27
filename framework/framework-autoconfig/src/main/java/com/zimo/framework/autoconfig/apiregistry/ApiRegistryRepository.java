package com.zimo.framework.autoconfig.apiregistry;

import org.springframework.jdbc.core.JdbcOperations;

import java.util.List;
import java.util.Objects;

/**
 * API 注册表 JDBC 持久化组件。
 *
 * <p>仓储依赖 {@code hash} 唯一索引，通过 SQLite 原子 UPSERT 写入接口元数据。扫描更新会恢复
 * 逻辑删除状态，但保留人工维护的 {@code status} 和 {@code sort}。</p>
 *
 * @author Codex
 * @since 2026-07-21
 */
public class ApiRegistryRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO api_registry (
              module_code, module_name, module_base_path, module_desc,
              controller_desc, method, path,
              api_name, summary, description, tags, request_params, response_example,
              auth_required, deprecated, hash, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(hash) DO UPDATE SET
              module_code = excluded.module_code, module_name = excluded.module_name,
              module_base_path = excluded.module_base_path, module_desc = excluded.module_desc,
              controller_desc = excluded.controller_desc, method = excluded.method, path = excluded.path,
              api_name = excluded.api_name, summary = excluded.summary, description = excluded.description,
              tags = excluded.tags, request_params = excluded.request_params,
              response_example = excluded.response_example, auth_required = excluded.auth_required,
              deprecated = excluded.deprecated, version = excluded.version,
              is_deleted = 0, updated_at = datetime('now')
            """;

    private final JdbcOperations jdbcOperations;

    /**
     * 创建 API 注册表仓储。
     *
     * @param jdbcOperations 当前应用目标数据源的 JDBC 操作对象
     */
    public ApiRegistryRepository(JdbcOperations jdbcOperations) {
        this.jdbcOperations = Objects.requireNonNull(jdbcOperations, "jdbcOperations must not be null");
    }

    /**
     * 按接口签名批量原子新增或更新扫描结果。
     *
     * <p>MySQL 返回值为 1 时计为新增，返回值为 0 或 2 时计为已有记录更新或保持不变。
     * 统计结果仅用于启动日志，不参与业务判断。</p>
     *
     * @param endpoints 待保存接口列表，不允许为 {@code null}；空列表不访问数据库
     * @return 本次新增和已有记录处理数量
     */
    public ApiRegistrySaveResult saveAll(List<ApiEndpointMetadata> endpoints) {
        int inserted = 0;
        int updated = 0;
        for (ApiEndpointMetadata endpoint : endpoints) {
            int affectedRows = upsert(endpoint);
            if (affectedRows == 1) {
                inserted++;
            } else {
                updated++;
            }
        }
        return new ApiRegistrySaveResult(inserted, updated);
    }

    private int upsert(ApiEndpointMetadata endpoint) {
        return jdbcOperations.update(UPSERT_SQL,
                endpoint.moduleCode(), endpoint.moduleName(), endpoint.moduleBasePath(), endpoint.moduleDescription(),
                endpoint.controllerDescription(), endpoint.method(), endpoint.path(), endpoint.apiName(),
                endpoint.summary(), endpoint.description(), endpoint.tags(), endpoint.requestParams(),
                endpoint.responseExample(), bool(endpoint.authRequired()), bool(endpoint.deprecated()),
                endpoint.hash(), endpoint.version());
    }

    private int bool(boolean value) {
        return value ? 1 : 0;
    }
}
