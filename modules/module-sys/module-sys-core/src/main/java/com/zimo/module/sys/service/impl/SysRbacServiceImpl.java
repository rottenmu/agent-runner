package com.zimo.module.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zimo.framework.common.BizException;
import com.zimo.module.sys.entity.SysMenu;
import com.zimo.module.sys.entity.SysRole;
import com.zimo.module.sys.entity.SysRoleMenu;
import com.zimo.module.sys.entity.SysUserRole;
import com.zimo.module.sys.mapper.SysMenuMapper;
import com.zimo.module.sys.mapper.SysRoleMapper;
import com.zimo.module.sys.mapper.SysRoleMenuMapper;
import com.zimo.module.sys.mapper.SysUserRoleMapper;
import com.zimo.module.auth.service.SysRbacService;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class SysRbacServiceImpl implements SysRbacService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;

    public SysRbacServiceImpl(SysUserRoleMapper userRoleMapper,
                              SysRoleMenuMapper roleMenuMapper,
                              SysRoleMapper roleMapper,
                              SysMenuMapper menuMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
    }

    @Override
    public List<Long> getUserRoleIds(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public void assignUserRoles(Long userId, List<Long> roleIds) {
        if (userId == null) {
            throw new BizException(400, "用户 ID 不能为空");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        normalizedIds(roleIds).forEach(roleId -> {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        });
    }

    @Override
    public List<Long> getRoleMenuIds(Long roleId) {
        if (roleId == null) {
            return Collections.emptyList();
        }
        return roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, roleId))
                .stream()
                .map(SysRoleMenu::getMenuId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public void assignRoleMenus(Long roleId, List<Long> menuIds) {
        if (roleId == null) {
            throw new BizException(400, "角色 ID 不能为空");
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        normalizedIds(menuIds).forEach(menuId -> {
            SysRoleMenu relation = new SysRoleMenu();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            roleMenuMapper.insert(relation);
        });
    }

    @Override
    public List<String> getRoleKeysByUserId(Long userId) {
        List<Long> roleIds = getUserRoleIds(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getId, roleIds)
                        .eq(SysRole::getStatus, 1))
                .stream()
                .map(SysRole::getRoleKey)
                .filter(this::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public List<String> getPermissionsByUserId(Long userId) {
        List<Long> roleIds = getUserRoleIds(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .in(SysRoleMenu::getRoleId, roleIds))
                .stream()
                .map(SysRoleMenu::getMenuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                        .in(SysMenu::getId, menuIds)
                        .eq(SysMenu::getStatus, 1))
                .stream()
                .map(SysMenu::getPermission)
                .filter(this::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    private List<Long> normalizedIds(List<Long> ids) {
        if (ids == null) {
            return Collections.emptyList();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
