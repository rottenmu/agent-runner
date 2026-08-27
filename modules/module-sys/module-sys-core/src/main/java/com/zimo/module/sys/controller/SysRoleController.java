package com.zimo.module.sys.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zimo.framework.common.ApiResponse;
import com.zimo.module.sys.annotation.HasPerm;
import com.zimo.module.sys.entity.SysRole;
import com.zimo.module.sys.service.SysRoleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/biz/sys/role")
public class SysRoleController {

    private final SysRoleService roleService;

    public SysRoleController(SysRoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/list")
    @SaCheckPermission("sys:role:list")
    @HasPerm("sys:role:list")
    public ApiResponse<List<SysRole>> list() {
        return ApiResponse.ok(roleService.list());
    }

    @GetMapping("/page")
    @SaCheckPermission("sys:role:list")
    @HasPerm("sys:role:list")
    public ApiResponse<Page<SysRole>> page(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(roleService.page(new Page<>(current, size)));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("sys:role:query")
    @HasPerm("sys:role:query")
    public ApiResponse<SysRole> getById(@PathVariable Long id) {
        return ApiResponse.ok(roleService.getById(id));
    }

    @PostMapping
    @SaCheckPermission("sys:role:create")
    @HasPerm("sys:role:create")
    public ApiResponse<Void> save(@RequestBody SysRole role) {
        roleService.save(role);
        return ApiResponse.ok();
    }

    @PutMapping
    @SaCheckPermission("sys:role:update")
    @HasPerm("sys:role:update")
    public ApiResponse<Void> update(@RequestBody SysRole role) {
        roleService.updateById(role);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("sys:role:delete")
    @HasPerm("sys:role:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.removeById(id);
        return ApiResponse.ok();
    }
}
