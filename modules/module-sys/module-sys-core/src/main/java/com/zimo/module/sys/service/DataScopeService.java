package com.zimo.module.sys.service;

import com.zimo.module.sys.enums.DataScopeEnum;
import cn.hutool.core.collection.CollUtil;
import com.zimo.module.sys.security.DataScopeMybatisPlugin;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Core service for data scope priority, intersection, and temporary authorization resolution.
 */
public class DataScopeService {

    private final Clock clock;

    public DataScopeService() {
        this(Clock.systemDefaultZone());
    }

    public DataScopeService(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public DataScopeEnum resolveEffectiveScope(Collection<RoleDataScope> roleDataScopes) {
        if (CollUtil.isEmpty(roleDataScopes)) {
            return DataScopeEnum.SELF;
        }
        DataScopeEnum effectiveScope = null;
        for (RoleDataScope roleDataScope : roleDataScopes) {
            if (roleDataScope == null || roleDataScope.getDataScope() == null) {
                continue;
            }
            effectiveScope = effectiveScope == null
                    ? roleDataScope.getDataScope()
                    : minScope(effectiveScope, roleDataScope.getDataScope());
        }
        return effectiveScope == null ? DataScopeEnum.SELF : effectiveScope;
    }

    public DataScopeEnum minScope(DataScopeEnum first, DataScopeEnum second) {
        if (first == null) {
            return second == null ? DataScopeEnum.SELF : second;
        }
        if (second == null) {
            return first;
        }
        return scopeRank(first) >= scopeRank(second) ? first : second;
    }

    public boolean isNarrowerOrEqual(DataScopeEnum candidate, DataScopeEnum baseline) {
        if (candidate == null || baseline == null) {
            return false;
        }
        return scopeRank(candidate) >= scopeRank(baseline);
    }

    public boolean isTemporaryAuthorizationValid(TemporaryAuthorization authorization) {
        if (authorization == null || authorization.getExpiresAt() == null) {
            return false;
        }
        return authorization.getExpiresAt().isAfter(Instant.now(clock));
    }

    public DataScopeEnum resolveTemporaryScope(DataScopeEnum originalScope, TemporaryAuthorization authorization) {
        if (isTemporaryAuthorizationValid(authorization) && authorization.getDataScope() != null) {
            return authorization.getDataScope();
        }
        return originalScope == null ? DataScopeEnum.SELF : originalScope;
    }

    public DataScopeMybatisPlugin.AccessProfile buildAccessProfile(
            Collection<RoleDataScope> roleDataScopes,
            Collection<DataScopeMybatisPlugin.CustomSqlRule> customSqlRules,
            TemporaryAuthorization temporaryAuthorization,
            boolean superAdmin,
            String requestIp
    ) {
        List<DataScopeEnum> orderedRoleScopes = cleanRoleScopes(roleDataScopes);
        return DataScopeMybatisPlugin.AccessProfile.builder()
                .roleDataScopes(orderedRoleScopes)
                .customSqlRules(customSqlRules)
                .temporaryAuthorized(isTemporaryAuthorizationValid(temporaryAuthorization))
                .superAdmin(superAdmin)
                .requestIp(requestIp)
                .build();
    }

    private List<DataScopeEnum> cleanRoleScopes(Collection<RoleDataScope> roleDataScopes) {
        if (CollUtil.isEmpty(roleDataScopes)) {
            return Collections.emptyList();
        }
        return roleDataScopes.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(RoleDataScope::getPriority).reversed())
                .map(RoleDataScope::getDataScope)
                .filter(Objects::nonNull)
                .toList();
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

    public static final class RoleDataScope {

        private final String roleKey;
        private final int priority;
        private final DataScopeEnum dataScope;

        private RoleDataScope(String roleKey, int priority, DataScopeEnum dataScope) {
            this.roleKey = roleKey;
            this.priority = priority;
            this.dataScope = dataScope;
        }

        public static RoleDataScope of(String roleKey, int priority, DataScopeEnum dataScope) {
            return new RoleDataScope(roleKey, priority, dataScope);
        }

        public String getRoleKey() {
            return roleKey;
        }

        public int getPriority() {
            return priority;
        }

        public DataScopeEnum getDataScope() {
            return dataScope;
        }
    }

    public static final class TemporaryAuthorization {

        private final String authorizationNo;
        private final DataScopeEnum dataScope;
        private final Instant expiresAt;

        private TemporaryAuthorization(String authorizationNo, DataScopeEnum dataScope, Instant expiresAt) {
            this.authorizationNo = authorizationNo;
            this.dataScope = dataScope;
            this.expiresAt = expiresAt;
        }

        public static TemporaryAuthorization of(String authorizationNo, DataScopeEnum dataScope, Instant expiresAt) {
            return new TemporaryAuthorization(authorizationNo, dataScope, expiresAt);
        }

        public String getAuthorizationNo() {
            return authorizationNo;
        }

        public DataScopeEnum getDataScope() {
            return dataScope;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }
    }
}
