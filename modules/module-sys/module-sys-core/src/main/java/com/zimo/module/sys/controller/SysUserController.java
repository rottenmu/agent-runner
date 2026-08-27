package com.zimo.module.sys.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zimo.framework.common.ApiResponse;
import com.zimo.module.auth.entity.SysUser;
import com.zimo.module.sys.annotation.HasPerm;
import com.zimo.module.sys.service.SysUserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/biz/sys/user")
public class SysUserController {

    private final SysUserService userService;

    public SysUserController(SysUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/page")
    @SaCheckPermission("sys:user:list")
    @HasPerm("sys:user:list")
    public ApiResponse<Page<SysUser>> page(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(userService.pageUsers(current, size));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("sys:user:query")
    @HasPerm("sys:user:query")
    public ApiResponse<SysUser> getById(@PathVariable Long id) {
        return ApiResponse.ok(userService.getById(id));
    }

    @PostMapping
    @SaCheckPermission("sys:user:create")
    @HasPerm("sys:user:create")
    public ApiResponse<Void> save(@RequestBody SysUser user) {
        userService.save(user);
        return ApiResponse.ok();
    }

    @PutMapping
    @SaCheckPermission("sys:user:update")
    @HasPerm("sys:user:update")
    public ApiResponse<Void> update(@RequestBody SysUser user) {
        userService.updateById(user);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("sys:user:delete")
    @HasPerm("sys:user:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.removeById(id);
        return ApiResponse.ok();
    }
}
