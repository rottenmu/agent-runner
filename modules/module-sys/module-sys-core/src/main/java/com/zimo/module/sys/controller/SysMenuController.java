package com.zimo.module.sys.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.zimo.framework.common.ApiResponse;
import com.zimo.module.sys.annotation.HasPerm;
import com.zimo.module.sys.entity.SysMenu;
import com.zimo.module.sys.service.SysMenuService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/biz/sys/menu")
public class SysMenuController {

    private final SysMenuService menuService;

    public SysMenuController(SysMenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/tree")
    @SaCheckPermission("sys:menu:list")
    @HasPerm("sys:menu:list")
    public ApiResponse<List<SysMenu>> tree() {
        return ApiResponse.ok(menuService.listMenuTree());
    }

    @GetMapping("/list")
    @SaCheckPermission("sys:menu:list")
    @HasPerm("sys:menu:list")
    public ApiResponse<List<SysMenu>> list() {
        return ApiResponse.ok(menuService.list());
    }

    @GetMapping("/{id}")
    @SaCheckPermission("sys:menu:query")
    @HasPerm("sys:menu:query")
    public ApiResponse<SysMenu> getById(@PathVariable Long id) {
        return ApiResponse.ok(menuService.getById(id));
    }

    @PostMapping
    @SaCheckPermission("sys:menu:create")
    @HasPerm("sys:menu:create")
    public ApiResponse<Void> save(@RequestBody SysMenu menu) {
        menuService.save(menu);
        return ApiResponse.ok();
    }

    @PutMapping
    @SaCheckPermission("sys:menu:update")
    @HasPerm("sys:menu:update")
    public ApiResponse<Void> update(@RequestBody SysMenu menu) {
        menuService.updateById(menu);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("sys:menu:delete")
    @HasPerm("sys:menu:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        menuService.removeById(id);
        return ApiResponse.ok();
    }
}
