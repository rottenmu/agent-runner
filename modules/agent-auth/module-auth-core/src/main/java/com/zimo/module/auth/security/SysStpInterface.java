package com.zimo.module.auth.security;

import cn.dev33.satoken.stp.StpInterface;
import com.zimo.module.auth.service.SysRbacService;
import java.util.Collections;
import java.util.List;

/**
 * 认证模块 Sa-Token 角色与权限数据提供器。
 *
 * <p>管理员角色使用平台内置权限集合，普通角色继续从 RBAC 服务读取已配置权限，
 * 不改变数据库角色与菜单关联规则。</p>
 *
 * @author Codex
 * @since 2026-07-21
 */
public class SysStpInterface implements StpInterface {

    private static final String ADMIN_ROLE = "admin";
    private static final List<String> ADMIN_PERMISSIONS = List.of(
            "sys:user:list", "sys:user:query", "sys:user:create", "sys:user:update", "sys:user:delete", "sys:user:role",
            "sys:role:list", "sys:role:query", "sys:role:create", "sys:role:update", "sys:role:delete", "sys:role:menu",
            "sys:menu:list", "sys:menu:query", "sys:menu:create", "sys:menu:update", "sys:menu:delete",
            "sys:api:list", "sys:api:update",
            "sys:agent-setting:list", "sys:agent-setting:update",
            "ai:mcp:list", "ai:mcp:create", "ai:mcp:update", "ai:mcp:delete",
            "ai:api-doc:list", "ai:api-doc:create", "ai:api-doc:update",
            "ai:api-doc:delete", "ai:api-doc:enable", "ai:api-doc:import",
            "ai:agent-capability:list", "ai:agent-capability:update",
            "ai:prompt-version:list", "ai:prompt-version:create", "ai:prompt-version:update",
            "ai:ab-test:list", "ai:ab-test:create", "ai:ab-test:update", "ai:ab-test:delete",
            "wf:workflow:list", "wf:workflow:create", "wf:workflow:update",
            "wf:workflow:delete", "wf:workflow:publish", "wf:workflow:run",
            "wf:template:list", "wf:template:create", "wf:template:update",
            "wf:template:delete", "wf:template:apply",
            "ds:datasource:list", "ds:datasource:create", "ds:datasource:update",
            "ds:datasource:delete", "ds:datasource:test", "ds:datasource:preview",
            "rag:document:list", "rag:document:create", "rag:document:process",
            "rag:document:delete", "rag:retrieve",
            "rag:kb:list", "rag:kb:create", "rag:kb:update", "rag:kb:delete",
            "rag:kb:permission", "rag:kb:sync", "rag:version:rollback",
            "rag:evaluation:manage", "rag:log:view",
            "tool:invoke", "tool:plugin:manage", "tool:script:manage",
            "tool:permission:manage", "tool:log:view", "tool:governance:manage",
            "collab:session:manage", "collab:task:manage", "collab:message:send",
            "collab:approve", "collab:template:use",
            "observ:dashboard:view", "observ:trace:view", "observ:test:manage",
            "observ:alert:view", "observ:alert:handle",
            "sec:org:manage", "sec:permission:manage", "sec:data-scope:manage",
            "sec:audit:view", "sec:audit:verify", "sec:content:manage",
            "sec:approval:manage",
            "chan:deploy:manage", "chan:conversation:view"
    );

    private final SysRbacService rbacService;

    /**
     * 创建 Sa-Token 权限数据提供器。
     *
     * @param rbacService 系统 RBAC 查询服务，用于加载用户角色和普通角色权限
     */
    public SysStpInterface(SysRbacService rbacService) {
        this.rbacService = rbacService;
    }

    /**
     * 查询登录用户的功能权限标识。
     *
     * <p>拥有 {@code admin} 角色时返回平台内置管理员权限；其他角色返回 RBAC 服务保存的权限，
     * 多角色合并规则由 RBAC 服务负责。无法解析登录 ID 时返回空列表。</p>
     *
     * @param loginId Sa-Token 登录标识，支持数值或可转换为 Long 的字符串
     * @param loginType Sa-Token 登录类型，本实现不按登录类型区分权限
     * @return 当前用户功能权限列表；登录 ID 无效时返回空列表
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = toLong(loginId);
        if (userId == null) {
            return Collections.emptyList();
        }
        List<String> roles = rbacService.getRoleKeysByUserId(userId);
        if (roles.contains(ADMIN_ROLE)) {
            return ADMIN_PERMISSIONS;
        }
        return rbacService.getPermissionsByUserId(userId);
    }

    /**
     * 查询登录用户的角色标识。
     *
     * @param loginId Sa-Token 登录标识，支持数值或可转换为 Long 的字符串
     * @param loginType Sa-Token 登录类型，本实现不按登录类型区分角色
     * @return 当前用户角色标识列表；登录 ID 无效时返回空列表
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = toLong(loginId);
        if (userId == null) {
            return Collections.emptyList();
        }
        return rbacService.getRoleKeysByUserId(userId);
    }

    private Long toLong(Object loginId) {
        if (loginId == null) {
            return null;
        }
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(loginId));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}