package com.zimo.module.sys.controller;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.zimo.module.sys.annotation.HasPerm;
import com.zimo.module.sys.apiregistry.SysApiRegistryController;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

class SysControllerPermissionContractTest {

    @Test
    void sysUserControllerUsesPlatformPermissionCodes() {
        assertPermissions(SysUserController.class, Map.of(
                "page", "sys:user:list",
                "getById", "sys:user:query",
                "save", "sys:user:create",
                "update", "sys:user:update",
                "delete", "sys:user:delete"
        ));
    }

    @Test
    void sysRoleControllerUsesPlatformPermissionCodes() {
        assertPermissions(SysRoleController.class, Map.of(
                "list", "sys:role:list",
                "page", "sys:role:list",
                "getById", "sys:role:query",
                "save", "sys:role:create",
                "update", "sys:role:update",
                "delete", "sys:role:delete"
        ));
    }

    @Test
    void sysMenuControllerUsesPlatformPermissionCodes() {
        assertPermissions(SysMenuController.class, Map.of(
                "tree", "sys:menu:list",
                "list", "sys:menu:list",
                "getById", "sys:menu:query",
                "save", "sys:menu:create",
                "update", "sys:menu:update",
                "delete", "sys:menu:delete"
        ));
    }

    @Test
    void sysRbacControllerUsesPlatformPermissionCodes() {
        assertPermissions(SysRbacController.class, Map.of(
                "getUserRoles", "sys:user:role",
                "assignUserRoles", "sys:user:role",
                "getRoleMenus", "sys:role:menu",
                "assignRoleMenus", "sys:role:menu"
        ));
    }

    @Test
    void sysApiRegistryControllerUsesPlatformPermissionCodes() {
        assertPermissions(SysApiRegistryController.class, Map.of(
                "list", "sys:api:list",
                "grouped", "sys:api:list",
                "updateStatus", "sys:api:update"
        ));
    }
    private static void assertPermissions(Class<?> controllerType, Map<String, String> expectedPermissions) {
        for (Map.Entry<String, String> expectedPermission : expectedPermissions.entrySet()) {
            HasPerm annotation = findHasPerm(controllerType, expectedPermission.getKey());
            assertThat(annotation)
                    .as("%s.%s must declare @HasPerm", controllerType.getSimpleName(), expectedPermission.getKey())
                    .isNotNull();
            assertThat(annotation.value()).isEqualTo(expectedPermission.getValue());
            SaCheckPermission saPermission = findSaPermission(controllerType, expectedPermission.getKey());
            assertThat(saPermission)
                    .as("%s.%s must declare @SaCheckPermission",
                            controllerType.getSimpleName(), expectedPermission.getKey())
                    .isNotNull();
            assertThat(saPermission.value()).containsExactly(expectedPermission.getValue());
        }
    }

    private static HasPerm findHasPerm(Class<?> controllerType, String methodName) {
        for (Method method : controllerType.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return AnnotatedElementUtils.findMergedAnnotation(method, HasPerm.class);
            }
        }
        throw new AssertionError("Missing controller method: " + controllerType.getName() + "." + methodName);
    }

    private static SaCheckPermission findSaPermission(Class<?> controllerType, String methodName) {
        for (Method method : controllerType.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return AnnotatedElementUtils.findMergedAnnotation(method, SaCheckPermission.class);
            }
        }
        throw new AssertionError("Missing controller method: " + controllerType.getName() + "." + methodName);
    }
}