package com.zimo.module.sys.demo;

import com.zimo.module.sys.annotation.DataScope;
import com.zimo.module.sys.annotation.FieldDesensitize;
import com.zimo.module.sys.annotation.HasPerm;
import com.zimo.module.sys.annotation.IgnorePermission;
import com.zimo.module.sys.context.PermissionCache;
import com.zimo.module.sys.context.UserPermissionContext;
import com.zimo.module.sys.enums.DataScopeEnum;
import com.zimo.module.sys.service.PermissionService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;

public class PermissionStarterUsageDemo {

    public UserPermissionContext createLoginContext() {
        return UserPermissionContext.of(
                1001L,
                "demo.operator",
                "factory-01",
                "factory-one",
                Set.of("sys:role:list", "wms:stock:*"),
                DataScopeEnum.FACTORY,
                Set.of("contactPhone", "costAmount")
        );
    }

    public void injectContextAfterLogin(UserPermissionContext context) {
        PermissionCache.put(context, Duration.ofHours(2));
        PermissionCache.bindCurrent(context);
    }

    public void clearRequestContext() {
        PermissionCache.unbindCurrent();
    }

    public PermissionService.PermissionSnapshot mergeRolePermissions() {
        return new PermissionService().mergeRolePermissions(List.of(
                PermissionService.RolePermission.of(
                        "warehouse-manager",
                        90,
                        Set.of("wms:stock:*", "sys:role:list")
                ),
                PermissionService.RolePermission.of(
                        "warehouse-operator",
                        30,
                        Set.of("wms:stock:query", "wms:inbound:create")
                )
        ));
    }

    public boolean hasWildcardPermission(String requiredPermission) {
        return new PermissionService().hasPermission(requiredPermission, mergeRolePermissions().getPermissions());
    }

    public static class AnnotatedPermissionApi {

        @HasPerm("sys:role:create")
        public void createRole() {
        }

        @IgnorePermission
        public void publicHealth() {
        }
    }

    public interface ProjectDataScopeMapper {

        @DataScope(value = "pm_project", tableAlias = "p")
        List<String> selectProjects();
    }

    public static class SupplierContactView {

        @FieldDesensitize(type = FieldDesensitize.Type.PHONE)
        private String contactPhone;

        @FieldDesensitize(type = FieldDesensitize.Type.CUSTOM, mask = "****")
        private BigDecimal costAmount;
    }
}
