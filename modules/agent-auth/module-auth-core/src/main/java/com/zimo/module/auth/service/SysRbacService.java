package com.zimo.module.auth.service;

import java.util.List;

public interface SysRbacService {
    List<Long> getUserRoleIds(Long userId);

    void assignUserRoles(Long userId, List<Long> roleIds);

    List<Long> getRoleMenuIds(Long roleId);

    void assignRoleMenus(Long roleId, List<Long> menuIds);

    List<String> getRoleKeysByUserId(Long userId);

    List<String> getPermissionsByUserId(Long userId);
}
