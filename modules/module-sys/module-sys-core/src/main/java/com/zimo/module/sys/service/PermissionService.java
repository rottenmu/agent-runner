package com.zimo.module.sys.service;

import java.util.ArrayList;
import cn.hutool.core.collection.CollUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Core service for permission merging and permission identifier checks.
 */
public class PermissionService {

    private static final Set<String> SUPER_PERMISSION_MARKS = Set.of("*", "*:*:*", "admin", "sys:admin");

    public PermissionSnapshot mergeRolePermissions(Collection<RolePermission> rolePermissions) {
        if (CollUtil.isEmpty(rolePermissions)) {
            return PermissionSnapshot.empty();
        }

        List<RolePermission> orderedRoles = rolePermissions.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(RolePermission::getPriority).reversed())
                .toList();
        Set<String> mergedPermissions = new LinkedHashSet<>();
        List<String> roleKeys = new ArrayList<>();
        for (RolePermission rolePermission : orderedRoles) {
            if (hasText(rolePermission.getRoleKey())) {
                roleKeys.add(rolePermission.getRoleKey());
            }
            for (String permission : rolePermission.getPermissions()) {
                if (hasText(permission)) {
                    mergedPermissions.add(permission.trim());
                }
            }
        }
        return new PermissionSnapshot(mergedPermissions, roleKeys);
    }

    public boolean hasPermission(String requiredPermission, Collection<String> ownedPermissions) {
        if (!hasText(requiredPermission) || ownedPermissions == null || ownedPermissions.isEmpty()) {
            return false;
        }
        String required = requiredPermission.trim();
        for (String ownedPermission : ownedPermissions) {
            if (matchesPermission(required, ownedPermission)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyPermission(Collection<String> requiredPermissions, Collection<String> ownedPermissions) {
        if (requiredPermissions == null || requiredPermissions.isEmpty()) {
            return false;
        }
        for (String requiredPermission : requiredPermissions) {
            if (hasPermission(requiredPermission, ownedPermissions)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAllPermissions(Collection<String> requiredPermissions, Collection<String> ownedPermissions) {
        if (requiredPermissions == null || requiredPermissions.isEmpty()) {
            return false;
        }
        for (String requiredPermission : requiredPermissions) {
            if (!hasPermission(requiredPermission, ownedPermissions)) {
                return false;
            }
        }
        return true;
    }

    public boolean matchesPermission(String requiredPermission, String ownedPermission) {
        if (!hasText(requiredPermission) || !hasText(ownedPermission)) {
            return false;
        }
        String required = requiredPermission.trim();
        String owned = ownedPermission.trim();
        if (SUPER_PERMISSION_MARKS.contains(owned) || owned.equals(required)) {
            return true;
        }
        if (owned.endsWith(":*")) {
            String prefix = owned.substring(0, owned.length() - 1);
            return required.startsWith(prefix);
        }
        if (owned.endsWith("*")) {
            return required.startsWith(owned.substring(0, owned.length() - 1));
        }
        if (owned.contains("*")) {
            return wildcardMatch(required, owned);
        }
        return false;
    }

    private boolean wildcardMatch(String requiredPermission, String ownedPermission) {
        String regex = ownedPermission
                .replace(".", "\\.")
                .replace("*", ".*");
        return requiredPermission.matches(regex);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static final class RolePermission {

        private final String roleKey;
        private final int priority;
        private final Set<String> permissions;

        private RolePermission(String roleKey, int priority, Collection<String> permissions) {
            this.roleKey = roleKey;
            this.priority = priority;
            this.permissions = immutableCleanSet(permissions);
        }

        public static RolePermission of(String roleKey, int priority, Collection<String> permissions) {
            return new RolePermission(roleKey, priority, permissions);
        }

        public String getRoleKey() {
            return roleKey;
        }

        public int getPriority() {
            return priority;
        }

        public Set<String> getPermissions() {
            return permissions;
        }
    }

    public static final class PermissionSnapshot {

        private static final PermissionSnapshot EMPTY = new PermissionSnapshot(Collections.emptySet(), Collections.emptyList());

        private final Set<String> permissions;
        private final List<String> roleKeys;

        private PermissionSnapshot(Collection<String> permissions, Collection<String> roleKeys) {
            this.permissions = immutableCleanSet(permissions);
            this.roleKeys = immutableCleanList(roleKeys);
        }

        public static PermissionSnapshot empty() {
            return EMPTY;
        }

        public Set<String> getPermissions() {
            return permissions;
        }

        public List<String> getRoleKeys() {
            return roleKeys;
        }

        public boolean hasPermission(String permission) {
            return new PermissionService().hasPermission(permission, permissions);
        }
    }

    private static Set<String> immutableCleanSet(Collection<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> clean = new LinkedHashSet<>();
        for (String value : source) {
            if (hasText(value)) {
                clean.add(value.trim());
            }
        }
        return clean.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(clean);
    }

    private static List<String> immutableCleanList(Collection<String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> clean = new ArrayList<>();
        for (String value : source) {
            if (hasText(value)) {
                clean.add(value.trim());
            }
        }
        return clean.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(clean);
    }
}
