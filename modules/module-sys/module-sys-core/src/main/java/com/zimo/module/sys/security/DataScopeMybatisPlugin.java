package com.zimo.module.sys.security;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import cn.hutool.core.util.StrUtil;
import com.zimo.module.sys.annotation.DataScope;
import com.zimo.module.sys.context.UserPermissionContext;
import com.zimo.module.sys.enums.DataScopeEnum;
import com.zimo.module.sys.util.SqlFilterUtil;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.util.AntPathMatcher;

/**
 * MyBatis Plus data scope interceptor for manufacturing organization isolation.
 */
public class DataScopeMybatisPlugin implements InnerInterceptor {

    private static final ThreadLocal<AccessProfile> ACCESS_PROFILE = new ThreadLocal<>();
    private static final Pattern SELECT_PATTERN = Pattern.compile("^\\s*select\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHERE_PATTERN = Pattern.compile("\\bwhere\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAIL_PATTERN = Pattern.compile(
            "\\b(order\\s+by|group\\s+by|having|limit|offset|fetch|for\\s+update)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final List<String> DEFAULT_ADMIN_PERMISSIONS = List.of("*", "*:*:*", "sys:admin", "admin");

    private final List<String> ipWhitelist;
    private final List<String> adminPermissions;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public DataScopeMybatisPlugin() {
        this(List.of());
    }

    public DataScopeMybatisPlugin(Collection<String> ipWhitelist) {
        this(ipWhitelist, DEFAULT_ADMIN_PERMISSIONS);
    }

    public DataScopeMybatisPlugin(Collection<String> ipWhitelist, Collection<String> adminPermissions) {
        this.ipWhitelist = immutableCleanList(ipWhitelist);
        this.adminPermissions = immutableCleanList(adminPermissions);
    }

    public static void setAccessProfile(AccessProfile accessProfile) {
        if (accessProfile == null) {
            ACCESS_PROFILE.remove();
            return;
        }
        ACCESS_PROFILE.set(accessProfile);
    }

    public static Optional<AccessProfile> currentAccessProfile() {
        return Optional.ofNullable(ACCESS_PROFILE.get());
    }

    public static void clearAccessProfile() {
        ACCESS_PROFILE.remove();
    }

    @Override
    public void beforeQuery(
            Executor executor,
            MappedStatement ms,
            Object parameter,
            RowBounds rowBounds,
            ResultHandler resultHandler,
            BoundSql boundSql
    ) throws SQLException {
        DataScope dataScope = resolveDataScope(ms);
        RewriteResult result = rewriteSql(boundSql.getSql(), dataScope, currentRequestIp());
        if (!result.isFiltered()) {
            return;
        }

        MetaObject metaObject = SystemMetaObject.forObject(boundSql);
        metaObject.setValue("sql", result.toJdbcSql());
        List<ParameterMapping> parameterMappings = new ArrayList<>(boundSql.getParameterMappings());
        for (Map.Entry<String, Object> entry : result.getParams().entrySet()) {
            parameterMappings.add(new ParameterMapping.Builder(ms.getConfiguration(), entry.getKey(), Object.class).build());
            boundSql.setAdditionalParameter(entry.getKey(), entry.getValue());
        }
        metaObject.setValue("parameterMappings", parameterMappings);
    }

    public RewriteResult rewriteSql(String sql, DataScope dataScope, String requestIp) {
        if (!hasText(sql) || !SELECT_PATTERN.matcher(sql).find() || dataScope == null || shouldSkip(requestIp)) {
            return RewriteResult.unchanged(sql);
        }

        String tableName = resolveTableName(dataScope);
        if (!hasText(tableName) || !containsTable(sql, tableName)) {
            return RewriteResult.unchanged(sql);
        }

        Optional<UserPermissionContext> currentUser = UserPermissionContext.current();
        if (currentUser.isEmpty()) {
            return RewriteResult.unchanged(sql);
        }

        DataScopeEnum effectiveScope = resolveEffectiveScope(currentUser.get());
        if (effectiveScope == DataScopeEnum.ALL) {
            return RewriteResult.unchanged(sql);
        }

        String alias = hasText(dataScope.tableAlias()) ? dataScope.tableAlias().trim() : resolveTableAlias(sql, tableName);
        SqlFilterUtil.SqlCondition baseCondition = SqlFilterUtil.buildDataScopeCondition(
                effectiveScope,
                alias,
                dataScope.deptColumn(),
                dataScope.userColumn(),
                currentUser.get().getOrganizationId(),
                currentUser.get().getUserId()
        );

        List<SqlFilterUtil.SqlCondition> conditions = new ArrayList<>();
        if (!baseCondition.isEmpty()) {
            conditions.add(baseCondition);
        }
        for (CustomSqlRule customSqlRule : currentCustomSqlRules()) {
            if (customSqlRule.matches(tableName) && hasText(customSqlRule.getSqlExpression())) {
                conditions.add(SqlFilterUtil.SqlCondition.of(customSqlRule.getSqlExpression(), "customSqlRule", true));
            }
        }
        if (conditions.isEmpty()) {
            return RewriteResult.unchanged(sql);
        }

        RewriteResult condition = combineConditions(conditions);
        return new RewriteResult(appendCondition(sql, condition.getSql()), condition.getParams(), true);
    }

    private boolean shouldSkip(String requestIp) {
        AccessProfile accessProfile = ACCESS_PROFILE.get();
        if (accessProfile != null && (accessProfile.isTemporaryAuthorized() || accessProfile.isSuperAdmin())) {
            return true;
        }
        if (matchesIpWhitelist(requestIp)) {
            return true;
        }
        return isCurrentUserSuperAdmin();
    }

    private DataScopeEnum resolveEffectiveScope(UserPermissionContext user) {
        List<DataScopeEnum> roleScopes = currentAccessProfile()
                .map(AccessProfile::getRoleDataScopes)
                .orElseGet(Collections::emptyList);
        if (roleScopes.isEmpty()) {
            return user.getDataScope();
        }
        DataScopeEnum effective = null;
        for (DataScopeEnum roleScope : roleScopes) {
            if (roleScope == null) {
                continue;
            }
            if (effective == null || scopeRank(roleScope) > scopeRank(effective)) {
                effective = roleScope;
            }
        }
        return effective == null ? user.getDataScope() : effective;
    }

    private List<CustomSqlRule> currentCustomSqlRules() {
        return currentAccessProfile()
                .map(AccessProfile::getCustomSqlRules)
                .orElseGet(Collections::emptyList);
    }

    private boolean isCurrentUserSuperAdmin() {
        for (String permission : adminPermissions) {
            if (UserPermissionContext.hasCurrentPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesIpWhitelist(String requestIp) {
        if (!hasText(requestIp) || ipWhitelist.isEmpty()) {
            return false;
        }
        for (String pattern : ipWhitelist) {
            if (pathMatcher.match(pattern, requestIp)) {
                return true;
            }
        }
        return false;
    }

    private String currentRequestIp() {
        return currentAccessProfile()
                .map(AccessProfile::getRequestIp)
                .orElse(null);
    }

    private DataScope resolveDataScope(MappedStatement mappedStatement) {
        if (mappedStatement == null || !hasText(mappedStatement.getId())) {
            return null;
        }
        int methodIndex = mappedStatement.getId().lastIndexOf('.');
        if (methodIndex <= 0 || methodIndex >= mappedStatement.getId().length() - 1) {
            return null;
        }
        String className = mappedStatement.getId().substring(0, methodIndex);
        String methodName = mappedStatement.getId().substring(methodIndex + 1);
        try {
            Class<?> mapperType = Class.forName(className);
            for (Method method : mapperType.getMethods()) {
                if (method.getName().equals(methodName) && method.isAnnotationPresent(DataScope.class)) {
                    return method.getAnnotation(DataScope.class);
                }
            }
            return mapperType.getAnnotation(DataScope.class);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private RewriteResult combineConditions(List<SqlFilterUtil.SqlCondition> conditions) {
        if (conditions.size() == 1) {
            SqlFilterUtil.SqlCondition condition = conditions.get(0);
            return new RewriteResult(condition.getSql(), condition.getParams(), true);
        }
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < conditions.size(); i++) {
            SqlFilterUtil.SqlCondition condition = conditions.get(i);
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append('(').append(condition.getSql()).append(')');
            params.putAll(condition.getParams());
        }
        return new RewriteResult(sql.toString(), params, true);
    }

    private String appendCondition(String sql, String condition) {
        int tailIndex = findTailIndex(sql);
        String head = tailIndex < 0 ? sql : sql.substring(0, tailIndex).stripTrailing();
        String tail = tailIndex < 0 ? "" : " " + sql.substring(tailIndex).stripLeading();
        String connector = WHERE_PATTERN.matcher(head).find() ? " AND " : " WHERE ";
        return head + connector + condition + tail;
    }

    private int findTailIndex(String sql) {
        Matcher matcher = TAIL_PATTERN.matcher(sql);
        return matcher.find() ? matcher.start() : -1;
    }

    private boolean containsTable(String sql, String tableName) {
        return tablePattern(tableName).matcher(sql).find();
    }

    private String resolveTableAlias(String sql, String tableName) {
        Matcher matcher = tablePattern(tableName).matcher(sql);
        if (!matcher.find()) {
            return tableName;
        }
        String alias = matcher.group(1);
        if (!hasText(alias)) {
            return tableName;
        }
        String cleanAlias = alias.trim();
        if (isSqlKeyword(cleanAlias)) {
            return tableName;
        }
        return cleanAlias;
    }

    private Pattern tablePattern(String tableName) {
        return Pattern.compile(
                "\\bfrom\\s+" + Pattern.quote(tableName) + "(?:\\s+(?:as\\s+)?([A-Za-z_][A-Za-z0-9_]*))?",
                Pattern.CASE_INSENSITIVE
        );
    }

    private String resolveTableName(DataScope dataScope) {
        if (dataScope == null) {
            return "";
        }
        if (hasText(dataScope.value())) {
            return dataScope.value().trim();
        }
        return hasText(dataScope.tableName()) ? dataScope.tableName().trim() : "";
    }

    private int scopeRank(DataScopeEnum dataScope) {
        return switch (dataScope) {
            case ALL -> 0;
            case GROUP -> 1;
            case FACTORY -> 2;
            case WORKSHOP -> 3;
            case SELF -> 4;
        };
    }

    private boolean isSqlKeyword(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals("where")
                || lower.equals("left")
                || lower.equals("right")
                || lower.equals("inner")
                || lower.equals("outer")
                || lower.equals("join")
                || lower.equals("order")
                || lower.equals("group")
                || lower.equals("limit");
    }

    private static List<String> immutableCleanList(Collection<String> values) {
        if (cn.hutool.core.collection.CollUtil.isEmpty(values)) {
            return Collections.emptyList();
        }
        List<String> clean = new ArrayList<>();
        for (String value : values) {
            if (hasText(value)) {
                clean.add(value.trim());
            }
        }
        return clean.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(clean);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static final class RewriteResult {

        private final String sql;
        private final Map<String, Object> params;
        private final boolean filtered;

        private RewriteResult(String sql, Map<String, Object> params, boolean filtered) {
            this.sql = sql;
            this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
            this.filtered = filtered;
        }

        public static RewriteResult unchanged(String sql) {
            return new RewriteResult(sql, Collections.emptyMap(), false);
        }

        public String getSql() {
            return sql;
        }

        public Map<String, Object> getParams() {
            return params;
        }

        public boolean isFiltered() {
            return filtered;
        }

        private String toJdbcSql() {
            String jdbcSql = sql;
            for (String paramName : params.keySet()) {
                jdbcSql = jdbcSql.replace(":" + paramName, "?");
            }
            return jdbcSql;
        }
    }

    public static final class AccessProfile {

        private final List<DataScopeEnum> roleDataScopes;
        private final List<CustomSqlRule> customSqlRules;
        private final boolean temporaryAuthorized;
        private final boolean superAdmin;
        private final String requestIp;

        private AccessProfile(Builder builder) {
            this.roleDataScopes = Collections.unmodifiableList(new ArrayList<>(builder.roleDataScopes));
            this.customSqlRules = Collections.unmodifiableList(new ArrayList<>(builder.customSqlRules));
            this.temporaryAuthorized = builder.temporaryAuthorized;
            this.superAdmin = builder.superAdmin;
            this.requestIp = builder.requestIp;
        }

        public static Builder builder() {
            return new Builder();
        }

        public List<DataScopeEnum> getRoleDataScopes() {
            return roleDataScopes;
        }

        public List<CustomSqlRule> getCustomSqlRules() {
            return customSqlRules;
        }

        public boolean isTemporaryAuthorized() {
            return temporaryAuthorized;
        }

        public boolean isSuperAdmin() {
            return superAdmin;
        }

        public String getRequestIp() {
            return requestIp;
        }

        public static final class Builder {

            private List<DataScopeEnum> roleDataScopes = Collections.emptyList();
            private List<CustomSqlRule> customSqlRules = Collections.emptyList();
            private boolean temporaryAuthorized;
            private boolean superAdmin;
            private String requestIp;

            public Builder roleDataScopes(Collection<DataScopeEnum> roleDataScopes) {
                this.roleDataScopes = roleDataScopes == null ? Collections.emptyList() : new ArrayList<>(roleDataScopes);
                return this;
            }

            public Builder customSqlRules(Collection<CustomSqlRule> customSqlRules) {
                this.customSqlRules = customSqlRules == null ? Collections.emptyList() : new ArrayList<>(customSqlRules);
                return this;
            }

            public Builder temporaryAuthorized(boolean temporaryAuthorized) {
                this.temporaryAuthorized = temporaryAuthorized;
                return this;
            }

            public Builder superAdmin(boolean superAdmin) {
                this.superAdmin = superAdmin;
                return this;
            }

            public Builder requestIp(String requestIp) {
                this.requestIp = requestIp;
                return this;
            }

            public AccessProfile build() {
                return new AccessProfile(this);
            }
        }
    }

    public static final class CustomSqlRule {

        private final String tableName;
        private final String sqlExpression;

        public CustomSqlRule(String tableName, String sqlExpression) {
            this.tableName = tableName;
            this.sqlExpression = sqlExpression;
        }

        public String getTableName() {
            return tableName;
        }

        public String getSqlExpression() {
            return sqlExpression;
        }

        private boolean matches(String tableName) {
            return hasText(this.tableName) && this.tableName.equalsIgnoreCase(tableName);
        }
    }
}
