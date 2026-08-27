package com.zimo.module.feishu.mapping;

import com.zimo.module.sys.enums.DataScopeEnum;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class FeishuInternalUserSnapshot {
    private final Long userId;
    private final String account;
    private final String organizationId;
    private final String organizationName;
    private final Set<String> permissions;
    private final DataScopeEnum dataScope;

    public FeishuInternalUserSnapshot(
            Long userId,
            String account,
            String organizationId,
            String organizationName,
            Set<String> permissions,
            DataScopeEnum dataScope) {
        this.userId = userId;
        this.account = account;
        this.organizationId = organizationId;
        this.organizationName = organizationName;
        this.permissions = permissions == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
        this.dataScope = dataScope == null ? DataScopeEnum.SELF : dataScope;
    }

    public Long getUserId() {
        return userId;
    }

    public String getAccount() {
        return account;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public DataScopeEnum getDataScope() {
        return dataScope;
    }
}
