package com.zimo.module.sys.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.module.sys.annotation.DataScope;
import com.zimo.module.sys.annotation.FieldDesensitize;
import com.zimo.module.sys.annotation.HasPerm;
import com.zimo.module.sys.annotation.IgnorePermission;
import com.zimo.module.sys.context.PermissionCache;
import com.zimo.module.sys.context.UserPermissionContext;
import com.zimo.module.sys.enums.DataScopeEnum;
import com.zimo.module.sys.service.PermissionService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PermissionStarterUsageDemoTest {

    private static final String DEMO_CLASS = "com.zimo.module.sys.demo.PermissionStarterUsageDemo";

    @AfterEach
    void tearDown() {
        PermissionCache.clear();
        PermissionCache.unbindCurrent();
    }

    @Test
    void demoShowsLoginContextInjectionAndStaticContextReads() throws Exception {
        Object demo = demo();
        Method createLoginContext = demo.getClass().getMethod("createLoginContext");
        Method injectContextAfterLogin = demo.getClass().getMethod("injectContextAfterLogin", UserPermissionContext.class);

        UserPermissionContext context = (UserPermissionContext) createLoginContext.invoke(demo);
        injectContextAfterLogin.invoke(demo, context);

        assertThat(UserPermissionContext.currentUserId()).contains(1001L);
        assertThat(UserPermissionContext.currentAccount()).contains("demo.operator");
        assertThat(UserPermissionContext.currentOrganizationId()).contains("factory-01");
        assertThat(UserPermissionContext.currentDataScope()).contains(DataScopeEnum.FACTORY);
        assertThat(UserPermissionContext.currentPermissions()).contains("sys:role:list", "wms:stock:*");
        assertThat(UserPermissionContext.currentNeedDesensitize("contactPhone")).isTrue();
    }

    @Test
    void demoShowsRolePermissionUnionAndWildcardChecks() throws Exception {
        Object demo = demo();
        Method mergeRolePermissions = demo.getClass().getMethod("mergeRolePermissions");
        Method hasWildcardPermission = demo.getClass().getMethod("hasWildcardPermission", String.class);

        PermissionService.PermissionSnapshot snapshot =
                (PermissionService.PermissionSnapshot) mergeRolePermissions.invoke(demo);

        assertThat(snapshot.getRoleKeys()).containsExactly("warehouse-manager", "warehouse-operator");
        assertThat(snapshot.getPermissions()).contains("wms:stock:*", "sys:role:list");
        assertThat((Boolean) hasWildcardPermission.invoke(demo, "wms:stock:query")).isTrue();
        assertThat((Boolean) hasWildcardPermission.invoke(demo, "sys:menu:delete")).isFalse();
    }

    @Test
    void demoShowsPermissionAnnotationsOnControllerMapperAndView() throws Exception {
        Class<?> apiType = Class.forName(DEMO_CLASS + "$AnnotatedPermissionApi");
        Method createRole = apiType.getDeclaredMethod("createRole");
        Method publicHealth = apiType.getDeclaredMethod("publicHealth");
        assertThat(createRole.getAnnotation(HasPerm.class).value()).isEqualTo("sys:role:create");
        assertThat(publicHealth.isAnnotationPresent(IgnorePermission.class)).isTrue();

        Class<?> mapperType = Class.forName(DEMO_CLASS + "$ProjectDataScopeMapper");
        Method selectProjects = mapperType.getDeclaredMethod("selectProjects");
        DataScope dataScope = selectProjects.getAnnotation(DataScope.class);
        assertThat(dataScope.value()).isEqualTo("pm_project");
        assertThat(dataScope.tableAlias()).isEqualTo("p");

        Class<?> viewType = Class.forName(DEMO_CLASS + "$SupplierContactView");
        Field contactPhone = viewType.getDeclaredField("contactPhone");
        Field costAmount = viewType.getDeclaredField("costAmount");
        assertThat(contactPhone.getAnnotation(FieldDesensitize.class).type()).isEqualTo(FieldDesensitize.Type.PHONE);
        assertThat(costAmount.getAnnotation(FieldDesensitize.class).type()).isEqualTo(FieldDesensitize.Type.CUSTOM);
    }

    private static Object demo() throws Exception {
        return Class.forName(DEMO_CLASS).getConstructor().newInstance();
    }
}
