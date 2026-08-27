package com.zimo.module.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.module.auth.service.SysRbacService;
import java.util.List;
import org.junit.jupiter.api.Test;

class SysStpInterfaceTest {

    @Test
    void returnsAdminPermissionsWhenUserHasAdminRole() {
        SysStpInterface stpInterface = new SysStpInterface(new FakeRbacService(List.of("admin"), List.of()));

        List<String> permissions = stpInterface.getPermissionList(1L, "login");

        assertThat(permissions).contains(
                "sys:user:list", "sys:role:list", "sys:menu:list", "sys:role:menu",
                "sys:api:list", "sys:api:update",
                "ai:mcp:list", "wf:workflow:list", "rag:document:list");
    }

    @Test
    void returnsStoredPermissionsForNormalUser() {
        SysStpInterface stpInterface = new SysStpInterface(new FakeRbacService(
                List.of("operator"),
                List.of("sys:menu:list")));

        assertThat(stpInterface.getRoleList(2L, "login")).containsExactly("operator");
        assertThat(stpInterface.getPermissionList(2L, "login")).containsExactly("sys:menu:list");
    }

    private record FakeRbacService(List<String> roleKeys, List<String> permissions) implements SysRbacService {
        @Override public List<Long> getUserRoleIds(Long userId) { return List.of(); }
        @Override public void assignUserRoles(Long userId, List<Long> roleIds) { }
        @Override public List<Long> getRoleMenuIds(Long roleId) { return List.of(); }
        @Override public void assignRoleMenus(Long roleId, List<Long> menuIds) { }
        @Override public List<String> getRoleKeysByUserId(Long userId) { return roleKeys; }
        @Override public List<String> getPermissionsByUserId(Long userId) { return permissions; }
    }
}
