package com.zimo.module.sys.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionServiceTest {

    @Test
    void mergesRolePermissionsByUnionAndKeepsHighestPriorityRoleFirst() {
        PermissionService service = new PermissionService();

        PermissionService.PermissionSnapshot snapshot = service.mergeRolePermissions(List.of(
                PermissionService.RolePermission.of("operator", 10, Set.of("wms:stock:list", "pm:project:view")),
                PermissionService.RolePermission.of("manager", 50, Set.of("pm:project:edit", "wms:stock:list"))
        ));

        assertThat(snapshot.getPermissions())
                .containsExactlyInAnyOrder("wms:stock:list", "pm:project:view", "pm:project:edit");
        assertThat(snapshot.getRoleKeys()).containsExactly("manager", "operator");
        assertThat(snapshot.hasPermission("pm:project:edit")).isTrue();
    }

    @Test
    void checksExactWildcardAndSuperPermissionMarks() {
        PermissionService service = new PermissionService();
        Set<String> permissions = Set.of("sys:role:*", "wms:*", "pm:admin", "*:*:*");

        assertThat(service.hasPermission("sys:role:add", permissions)).isTrue();
        assertThat(service.hasPermission("wms:stock:list", permissions)).isTrue();
        assertThat(service.hasPermission("pm:project:delete", permissions)).isTrue();
        assertThat(service.hasPermission("ai:agent:create", Set.of("ai:agent:list"))).isFalse();
    }

    @Test
    void supportsAnyAndAllPermissionChecks() {
        PermissionService service = new PermissionService();
        Set<String> permissions = Set.of("sys:user:list", "sys:role:list");

        assertThat(service.hasAnyPermission(List.of("missing", "sys:role:list"), permissions)).isTrue();
        assertThat(service.hasAllPermissions(List.of("sys:user:list", "sys:role:list"), permissions)).isTrue();
        assertThat(service.hasAllPermissions(List.of("sys:user:list", "sys:menu:list"), permissions)).isFalse();
    }
}
