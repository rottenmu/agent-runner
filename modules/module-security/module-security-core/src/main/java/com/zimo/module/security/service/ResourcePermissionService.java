package com.zimo.module.security.service;

import com.zimo.module.security.entity.SecResourcePermission;
import com.zimo.module.security.entity.SecUserDept;
import com.zimo.module.security.mapper.SecResourcePermissionMapper;
import com.zimo.module.security.mapper.SecUserDeptMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.util.StrUtil;
import com.zimo.module.auth.service.SysRbacService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 细粒度资源权限服务：管控智能体、知识库、工具、接口、敏感数据的查看/编辑/执行权限。
 *
 * <p>授权主体支持用户、角色、部门；资源类型支持 agent/knowledge_base/tool/api/sensitive_data；
 * 动作分级 view（查看）/ edit（编辑）/ execute（执行）。判定顺序：管理员全通 →
 * 显式授权（user &gt; role &gt; dept，action 级别）→ 整类通配授权 → 拒绝。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class ResourcePermissionService {

    private static final Logger log = LoggerFactory.getLogger(ResourcePermissionService.class);
    private static final String ADMIN = "admin";

    private final SecResourcePermissionMapper permissionMapper;
    private final SecUserDeptMapper userDeptMapper;
    private final SysRbacService rbacService;

    public ResourcePermissionService(SecResourcePermissionMapper permissionMapper,
                                     SecUserDeptMapper userDeptMapper,
                                     SysRbacService rbacService) {
        this.permissionMapper = permissionMapper;
        this.userDeptMapper = userDeptMapper;
        this.rbacService = rbacService;
    }

    /** 授予权限。 */
    public SecResourcePermission grant(String principalType, String principalId,
                                       String resourceType, String resourceId, String action) {
        SecResourcePermission existing = permissionMapper.selectOne(Wrappers.<SecResourcePermission>lambdaQuery()
                .eq(SecResourcePermission::getPrincipalType, principalType)
                .eq(SecResourcePermission::getPrincipalId, principalId)
                .eq(SecResourcePermission::getResourceType, resourceType)
                .eq(SecResourcePermission::getResourceId, resourceId == null ? "" : resourceId)
                .eq(SecResourcePermission::getAction, action));
        if (existing != null) {
            return existing;
        }
        SecResourcePermission permission = new SecResourcePermission();
        permission.setPrincipalType(principalType);
        permission.setPrincipalId(principalId);
        permission.setResourceType(resourceType);
        permission.setResourceId(resourceId == null ? "" : resourceId);
        permission.setAction(action);
        permission.setCreatedAt(LocalDateTime.now());
        permissionMapper.insert(permission);
        return permission;
    }

    /** 撤销权限。 */
    public void revoke(Long id) {
        permissionMapper.deleteById(id);
    }

    /** 权限列表（可按资源类型/主体过滤）。 */
    public List<SecResourcePermission> list(String resourceType, String principalType) {
        return permissionMapper.selectList(Wrappers.<SecResourcePermission>lambdaQuery()
                .eq(StrUtil.isNotBlank(resourceType), SecResourcePermission::getResourceType, resourceType)
                .eq(StrUtil.isNotBlank(principalType), SecResourcePermission::getPrincipalType, principalType)
                .orderByDesc(SecResourcePermission::getId)
                .last("LIMIT 500"));
    }

    /**
     * 权限校验。
     *
     * @param username 当前用户账号（admin 全通）
     * @param userId 当前用户 ID
     * @param resourceType 资源类型
     * @param resourceId 资源 ID
     * @param action 动作
     * @return true=允许
     */
    public boolean check(String username, Long userId, String resourceType, String resourceId, String action) {
        if (userId == null) {
            return false;
        }
        if (ADMIN.equals(username)) {
            return true;
        }
        String rid = resourceId == null ? "" : resourceId;
        // 1) 用户级显式授权
        if (hasExplicit("user", String.valueOf(userId), resourceType, rid, action)) {
            return true;
        }
        // 2) 角色级
        List<Long> roleIds = rbacService.getUserRoleIds(userId);
        for (Long roleId : roleIds) {
            if (hasExplicit("role", String.valueOf(roleId), resourceType, rid, action)) {
                return true;
            }
        }
        // 3) 部门级
        List<SecUserDept> deptLinks = userDeptMapper.selectList(Wrappers.<SecUserDept>lambdaQuery()
                .eq(SecUserDept::getUserId, userId));
        for (SecUserDept link : deptLinks) {
            if (hasExplicit("dept", String.valueOf(link.getDeptId()), resourceType, rid, action)) {
                return true;
            }
        }
        // 4) 整类通配授权（resource_id 为空）
        if (hasExplicit("user", String.valueOf(userId), resourceType, "", action)) {
            return true;
        }
        for (Long roleId : roleIds) {
            if (hasExplicit("role", String.valueOf(roleId), resourceType, "", action)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasExplicit(String principalType, String principalId,
                                String resourceType, String resourceId, String action) {
        SecResourcePermission p = permissionMapper.selectOne(Wrappers.<SecResourcePermission>lambdaQuery()
                .eq(SecResourcePermission::getPrincipalType, principalType)
                .eq(SecResourcePermission::getPrincipalId, principalId)
                .eq(SecResourcePermission::getResourceType, resourceType)
                .eq(SecResourcePermission::getResourceId, resourceId)
                .eq(SecResourcePermission::getAction, action));
        return p != null;
    }
}
