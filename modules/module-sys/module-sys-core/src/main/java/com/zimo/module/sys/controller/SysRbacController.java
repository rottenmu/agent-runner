package com.zimo.module.sys.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.zimo.framework.common.ApiResponse;
import com.zimo.module.auth.service.SysRbacService;
import com.zimo.module.sys.annotation.HasPerm;
import java.util.Collections;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/biz/sys")
public class SysRbacController {

    private final SysRbacService rbacService;

    public SysRbacController(SysRbacService rbacService) {
        this.rbacService = rbacService;
    }

    @GetMapping("/user/{userId}/roles")
    @SaCheckPermission("sys:user:role")
    @HasPerm("sys:user:role")
    public ApiResponse<List<Long>> getUserRoles(@PathVariable Long userId) {
        return ApiResponse.ok(rbacService.getUserRoleIds(userId));
    }

    @PutMapping("/user/{userId}/roles")
    @SaCheckPermission("sys:user:role")
    @HasPerm("sys:user:role")
    public ApiResponse<Void> assignUserRoles(@PathVariable Long userId, @RequestBody AssignIdsRequest request) {
        rbacService.assignUserRoles(userId, request.idsOrEmpty());
        return ApiResponse.ok();
    }

    @GetMapping("/role/{roleId}/menus")
    @SaCheckPermission("sys:role:menu")
    @HasPerm("sys:role:menu")
    public ApiResponse<List<Long>> getRoleMenus(@PathVariable Long roleId) {
        return ApiResponse.ok(rbacService.getRoleMenuIds(roleId));
    }

    @PutMapping("/role/{roleId}/menus")
    @SaCheckPermission("sys:role:menu")
    @HasPerm("sys:role:menu")
    public ApiResponse<Void> assignRoleMenus(@PathVariable Long roleId, @RequestBody AssignIdsRequest request) {
        rbacService.assignRoleMenus(roleId, request.idsOrEmpty());
        return ApiResponse.ok();
    }

    public record AssignIdsRequest(List<Long> ids) {
        List<Long> idsOrEmpty() {
            return ids == null ? Collections.emptyList() : ids;
        }
    }
}
