package com.zimo.module.sys.util;

import com.zimo.module.sys.enums.DataScopeEnum;
import cn.hutool.core.util.StrUtil;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SQL condition builder for data scope and organization hierarchy filters.
 */
public final class SqlFilterUtil {

    public static final String ORGANIZATION_PARAM = "permissionOrganizationId";
    public static final String ORGANIZATION_PATH_PARAM = "permissionOrganizationPath";
    public static final String USER_PARAM = "permissionUserId";

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private SqlFilterUtil() {
    }

    public static SqlCondition buildDataScopeCondition(
            DataScopeEnum dataScope,
            String tableAlias,
            String organizationColumn,
            String userColumn,
            Object organizationId,
            Long userId
    ) {
        DataScopeEnum actualScope = dataScope == null ? DataScopeEnum.SELF : dataScope;
        return switch (actualScope) {
            case ALL -> SqlCondition.empty();
            case GROUP, FACTORY, WORKSHOP -> SqlCondition.of(
                    qualifyColumn(tableAlias, organizationColumn),
                    "=",
                    ORGANIZATION_PARAM,
                    organizationId
            );
            case SELF -> SqlCondition.of(
                    qualifyColumn(tableAlias, userColumn),
                    "=",
                    USER_PARAM,
                    userId
            );
        };
    }

    public static SqlCondition buildOrganizationHierarchyCondition(
            String tableAlias,
            String hierarchyColumn,
            String organizationPath
    ) {
        String value = organizationPath == null ? "" : organizationPath;
        String likeValue = value.endsWith("%") ? value : value + "%";
        return SqlCondition.of(
                qualifyColumn(tableAlias, hierarchyColumn),
                "LIKE",
                ORGANIZATION_PATH_PARAM,
                likeValue
        );
    }

    public static String appendWhereCondition(String sql, SqlCondition condition) {
        if (StrUtil.isBlank(sql)) {
            return condition == null || condition.isEmpty() ? "" : "WHERE " + condition.getSql();
        }
        if (condition == null || condition.isEmpty()) {
            return sql;
        }
        String connector = containsWhereClause(sql) ? " AND " : " WHERE ";
        return sql + connector + condition.getSql();
    }

    public static String qualifyColumn(String tableAlias, String column) {
        validateIdentifier(column, "column");
        if (StrUtil.isBlank(tableAlias)) {
            return column;
        }
        validateIdentifier(tableAlias, "tableAlias");
        return tableAlias + "." + column;
    }

    public static void validateIdentifier(String identifier, String name) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException(name + " contains unsafe SQL identifier: " + identifier);
        }
    }

    private static boolean containsWhereClause(String sql) {
        return Pattern.compile("\\bwhere\\b", Pattern.CASE_INSENSITIVE)
                .matcher(sql.toLowerCase(Locale.ROOT))
                .find();
    }

    public static final class SqlCondition {

        private static final SqlCondition EMPTY = new SqlCondition("", Collections.emptyMap());

        private final String sql;
        private final Map<String, Object> params;

        private SqlCondition(String sql, Map<String, Object> params) {
            this.sql = sql;
            this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        }

        public static SqlCondition empty() {
            return EMPTY;
        }

        public static SqlCondition of(String leftExpression, String operator, String paramName, Object paramValue) {
            if (StrUtil.isBlank(leftExpression)) {
                return empty();
            }
            validateIdentifier(paramName, "paramName");
            Map<String, Object> params = new LinkedHashMap<>();
            params.put(paramName, paramValue);
            return new SqlCondition(leftExpression + " " + operator + " :" + paramName, params);
        }

        public static SqlCondition of(String sql, String paramName, Object paramValue) {
            if (StrUtil.isBlank(sql)) {
                return empty();
            }
            validateIdentifier(paramName, "paramName");
            Map<String, Object> params = new LinkedHashMap<>();
            params.put(paramName, paramValue);
            return new SqlCondition(sql, params);
        }

        public String getSql() {
            return sql;
        }

        public Map<String, Object> getParams() {
            return params;
        }

        public boolean isEmpty() {
            return StrUtil.isBlank(sql);
        }
    }
}
