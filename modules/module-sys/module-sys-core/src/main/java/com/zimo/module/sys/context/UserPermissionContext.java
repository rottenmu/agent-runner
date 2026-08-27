package com.zimo.module.sys.context;

import com.zimo.module.sys.enums.DataScopeEnum;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable read-only snapshot of the current user's permission information.
 */
public final class UserPermissionContext {

    private final Long userId;
    private final String account;
    private final String organizationId;
    private final String organizationName;
    private final Set<String> permissions;
    private final DataScopeEnum dataScope;
    private final Set<String> desensitizeFields;

    private UserPermissionContext(
            Long userId,
            String account,
            String organizationId,
            String organizationName,
            Set<String> permissions,
            DataScopeEnum dataScope,
            Set<String> desensitizeFields
    ) {
        this.userId = userId;
        this.account = account;
        this.organizationId = organizationId;
        this.organizationName = organizationName;
        this.permissions = immutableCleanSet(permissions);
        this.dataScope = dataScope == null ? DataScopeEnum.SELF : dataScope;
        this.desensitizeFields = immutableCleanSet(desensitizeFields);
    }

    public static UserPermissionContext of(
            Long userId,
            String account,
            String organizationId,
            String organizationName,
            Set<String> permissions,
            DataScopeEnum dataScope,
            Set<String> desensitizeFields
    ) {
        return new UserPermissionContext(
                userId,
                account,
                organizationId,
                organizationName,
                permissions,
                dataScope,
                desensitizeFields
        );
    }

    public static Optional<UserPermissionContext> current() {
        return PermissionCache.current();
    }

    public static Optional<Long> currentUserId() {
        return current().map(UserPermissionContext::getUserId);
    }

    public static Optional<String> currentAccount() {
        return current().map(UserPermissionContext::getAccount);
    }

    public static Optional<String> currentOrganizationId() {
        return current().map(UserPermissionContext::getOrganizationId);
    }

    public static Optional<String> currentOrganizationName() {
        return current().map(UserPermissionContext::getOrganizationName);
    }

    public static Optional<DataScopeEnum> currentDataScope() {
        return current().map(UserPermissionContext::getDataScope);
    }

    public static Set<String> currentPermissions() {
        return current()
                .map(UserPermissionContext::getPermissions)
                .orElseGet(Collections::emptySet);
    }

    public static Set<String> currentDesensitizeFields() {
        return current()
                .map(UserPermissionContext::getDesensitizeFields)
                .orElseGet(Collections::emptySet);
    }

    public static boolean hasCurrentPermission(String permission) {
        return current()
                .map(context -> context.hasPermission(permission))
                .orElse(false);
    }

    public static boolean hasAnyCurrentPermission(String... permissions) {
        return current()
                .map(context -> context.hasAnyPermission(permissions))
                .orElse(false);
    }

    public static boolean hasAllCurrentPermissions(String... permissions) {
        return current()
                .map(context -> context.hasAllPermissions(permissions))
                .orElse(false);
    }

    public static boolean currentNeedDesensitize(String fieldName) {
        return current()
                .map(context -> context.needDesensitize(fieldName))
                .orElse(false);
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

    public Set<String> getDesensitizeFields() {
        return desensitizeFields;
    }

    public boolean hasPermission(String permission) {
        return hasText(permission) && permissions.contains(permission.trim());
    }

    public boolean hasAnyPermission(String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }
        for (String permission : permissions) {
            if (hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAllPermissions(String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return false;
        }
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    public boolean needDesensitize(String fieldName) {
        return hasText(fieldName) && desensitizeFields.contains(fieldName.trim());
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof UserPermissionContext that)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
                && Objects.equals(account, that.account)
                && Objects.equals(organizationId, that.organizationId)
                && Objects.equals(organizationName, that.organizationName)
                && Objects.equals(permissions, that.permissions)
                && dataScope == that.dataScope
                && Objects.equals(desensitizeFields, that.desensitizeFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, account, organizationId, organizationName, permissions, dataScope, desensitizeFields);
    }

    private static Set<String> immutableCleanSet(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> clean = new LinkedHashSet<>();
        for (String value : source) {
            if (hasText(value)) {
                clean.add(value.trim());
            }
        }
        if (clean.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(clean);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
