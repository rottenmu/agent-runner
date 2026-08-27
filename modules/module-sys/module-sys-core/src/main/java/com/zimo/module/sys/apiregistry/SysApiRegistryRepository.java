package com.zimo.module.sys.apiregistry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.util.StringUtils;

/**
 * 系统管理模块 API 注册表 JDBC 数据访问组件。
 *
 * <p>本组件只访问 MySQL {@code api_registry} 表，负责查询未逻辑删除记录和维护展示状态，
 * 不参与真实 HTTP 接口的运行时拦截。Bean 生命周期由系统模块自动装配层管理。</p>
 *
 * @author Codex
 * @since 2026-07-21
 */
public class SysApiRegistryRepository {

    private static final String BASE_SQL = """
            SELECT id, module_code, module_name, module_base_path, module_desc,
                   controller_desc, method, path,
                   api_name, summary, description, tags, request_params, response_example,
                   auth_required, deprecated, version, status, sort
            FROM api_registry
            WHERE is_deleted = 0
            """;
    private static final String UPDATE_STATUS_SQL =
            "UPDATE api_registry SET status = ?, updated_at = CURRENT_TIMESTAMP "
                    + "WHERE id = ? AND is_deleted = 0";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> PARAM_LIST = new TypeReference<>() {
    };

    private final JdbcOperations jdbcOperations;
    private final ObjectMapper objectMapper;

    /**
     * 创建 API 注册表数据访问组件。
     *
     * @param jdbcOperations 指向 API 注册表 MySQL 数据源的 JDBC 操作对象，不允许为 {@code null}
     * @param objectMapper 用于解析注册表 JSON 列的映射器，不允许为 {@code null}
     * @throws NullPointerException 当任一依赖为 {@code null} 时抛出
     */
    public SysApiRegistryRepository(JdbcOperations jdbcOperations, ObjectMapper objectMapper) {
        this.jdbcOperations = Objects.requireNonNull(jdbcOperations, "jdbcOperations must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 查询 API 注册表中所有符合条件的未逻辑删除记录。
     *
     * <p>查询条件允许为空；方法名会转换为大写，关键字同时匹配路径、接口名和摘要。
     * 结果按模块、排序值、路径和 HTTP 方法升序排列，不会默认排除停用记录。</p>
     *
     * @param query 查询条件，允许为 {@code null}
     * @return 匹配的 API 注册信息列表；无匹配数据时返回空列表
     */
    public List<SysApiRegistryItem> find(SysApiRegistryQuery query) {
        SysApiRegistryQuery actual = query == null
                ? new SysApiRegistryQuery(null, null, null, null)
                : query;
        SqlAndArgs sqlAndArgs = buildQuery(actual);
        return jdbcOperations.query(sqlAndArgs.sql(), this::mapRow, sqlAndArgs.args());
    }

    /**
     * 更新指定未逻辑删除 API 的注册表展示状态。
     *
     * <p>本操作同步刷新 {@code updated_at}，只修改注册表元数据，不影响真实接口调用。</p>
     *
     * @param id API 注册表主键 ID
     * @param status 目标状态；业务层应保证仅传入 {@code 0} 或 {@code 1}
     * @return 受影响记录数；记录不存在或已逻辑删除时返回 {@code 0}
     */
    public int updateStatus(long id, int status) {
        return jdbcOperations.update(UPDATE_STATUS_SQL, status, id);
    }

    private SqlAndArgs buildQuery(SysApiRegistryQuery query) {
        StringBuilder sql = new StringBuilder(BASE_SQL);
        List<Object> args = new ArrayList<>();
        appendTextFilter(sql, args, "module_code", query.moduleCode(), false);
        appendTextFilter(sql, args, "method", query.method(), true);
        appendStatusFilter(sql, args, query.status());
        appendKeywordFilter(sql, args, query.keyword());
        sql.append(" ORDER BY module_code ASC, sort ASC, path ASC, method ASC");
        return new SqlAndArgs(sql.toString(), args.toArray());
    }

    private void appendTextFilter(StringBuilder sql, List<Object> args, String column,
                                  String value, boolean uppercase) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        sql.append(" AND ").append(column).append(" = ?");
        String normalized = value.trim();
        args.add(uppercase ? normalized.toUpperCase(Locale.ROOT) : normalized);
    }

    private void appendStatusFilter(StringBuilder sql, List<Object> args, Integer status) {
        if (status == null) {
            return;
        }
        sql.append(" AND status = ?");
        args.add(status);
    }

    private void appendKeywordFilter(StringBuilder sql, List<Object> args, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        sql.append(" AND (path LIKE ? OR api_name LIKE ? OR summary LIKE ?)");
        String pattern = "%" + keyword.trim() + "%";
        for (int index = 0; index < 3; index++) {
            args.add(pattern);
        }
    }

    private SysApiRegistryItem mapRow(ResultSet row, int rowNum) throws SQLException {
        return new SysApiRegistryItem(row.getLong("id"), row.getString("module_code"),
                row.getString("module_name"), row.getString("module_base_path"), row.getString("module_desc"),
                row.getString("controller_desc"), row.getString("method"), row.getString("path"),
                row.getString("api_name"), row.getString("summary"), row.getString("description"),
                parseTags(row.getString("tags")), parseParams(row.getString("request_params")),
                parseObject(row.getString("response_example")), row.getInt("auth_required") == 1,
                row.getInt("deprecated") == 1, row.getString("version"), row.getInt("status"),
                row.getInt("sort"));
    }

    private List<String> parseTags(String json) {
        return parseList(json, STRING_LIST);
    }

    private List<Map<String, Object>> parseParams(String json) {
        return parseList(json, PARAM_LIST);
    }

    private <T> List<T> parseList(String json, TypeReference<List<T>> type) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<T> parsed = objectMapper.readValue(json, type);
            return parsed == null ? List.of() : parsed;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Object parseObject(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private record SqlAndArgs(String sql, Object[] args) {
    }
}
