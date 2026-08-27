package com.zimo.module.security.service;

import com.zimo.module.security.entity.SecDataScope;
import com.zimo.module.security.entity.SecUserDept;
import com.zimo.module.security.mapper.SecDataScopeMapper;
import com.zimo.module.security.mapper.SecUserDeptMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.util.StrUtil;
import com.zimo.module.auth.service.SysRbacService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行级数据权限服务：按角色/用户/部门配置数据可见范围，实现行级数据权限隔离。
 *
 * <p>范围：all（全量）/ dept（本部门及下级）/ self（仅本人）。解析结果返回
 * {@code {scope, deptIds, userId}} 供业务查询附加过滤条件。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class DataScopeService {

    private final SecDataScopeMapper dataScopeMapper;
    private final SecUserDeptMapper userDeptMapper;
    private final SysRbacService rbacService;

    public DataScopeService(SecDataScopeMapper dataScopeMapper,
                            SecUserDeptMapper userDeptMapper,
                            SysRbacService rbacService) {
        this.dataScopeMapper = dataScopeMapper;
        this.userDeptMapper = userDeptMapper;
        this.rbacService = rbacService;
    }

    /** 配置数据范围。 */
    public SecDataScope setScope(String principalType, String principalId,
                                 String resourceType, String scope) {
        if (!List.of("all", "dept", "self").contains(scope)) {
            throw new IllegalArgumentException("范围必须是 all/dept/self");
        }
        SecDataScope existing = dataScopeMapper.selectOne(Wrappers.<SecDataScope>lambdaQuery()
                .eq(SecDataScope::getPrincipalType, principalType)
                .eq(SecDataScope::getPrincipalId, principalId)
                .eq(SecDataScope::getResourceType, resourceType));
        if (existing != null) {
            existing.setScope(scope);
            dataScopeMapper.updateById(existing);
            return existing;
        }
        SecDataScope scopeEntity = new SecDataScope();
        scopeEntity.setPrincipalType(principalType);
        scopeEntity.setPrincipalId(principalId);
        scopeEntity.setResourceType(resourceType);
        scopeEntity.setScope(scope);
        scopeEntity.setCreatedAt(LocalDateTime.now());
        dataScopeMapper.insert(scopeEntity);
        return scopeEntity;
    }

    /** 数据范围列表。 */
    public List<SecDataScope> list(String resourceType) {
        return dataScopeMapper.selectList(Wrappers.<SecDataScope>lambdaQuery()
                .eq(StrUtil.isNotBlank(resourceType), SecDataScope::getResourceType, resourceType)
                .orderByDesc(SecDataScope::getId));
    }

    /**
     * 解析用户对某类数据的行级范围。
     *
     * @param userId 用户 ID
     * @param resourceType 数据资源类型
     * @return {@code {scope, deptIds, userId}}；scope=all 表示全量
     */
    public Map<String, Object> resolve(Long userId, String resourceType) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Long> deptIds = new ArrayList<>();
        userDeptMapper.selectList(Wrappers.<SecUserDept>lambdaQuery()
                        .eq(SecUserDept::getUserId, userId))
                .forEach(link -> deptIds.add(link.getDeptId()));
        result.put("scope", "self");
        result.put("deptIds", deptIds);
        result.put("userId", userId);

        List<SecDataScope> scopes = list(resourceType);
        // 1) 用户级显式配置优先
        for (SecDataScope scope : scopes) {
            if ("user".equals(scope.getPrincipalType())
                    && scope.getPrincipalId().equals(String.valueOf(userId))) {
                result.put("scope", scope.getScope());
                return result;
            }
        }
        // 2) 角色级：匹配当前用户角色；任一 all → 全量；否则取首个匹配角色范围
        List<Long> roleIds = rbacService.getUserRoleIds(userId);
        String roleScope = null;
        for (SecDataScope scope : scopes) {
            if ("role".equals(scope.getPrincipalType())
                    && roleIds.contains(Long.valueOf(scope.getPrincipalId()))) {
                if ("all".equals(scope.getScope())) {
                    result.put("scope", "all");
                    return result;
                }
                if (roleScope == null) {
                    roleScope = scope.getScope();
                }
            }
        }
        if (roleScope != null) {
            result.put("scope", roleScope);
            return result;
        }
        // 3) 部门级
        for (SecDataScope scope : scopes) {
            if ("dept".equals(scope.getPrincipalType())) {
                for (Long deptId : deptIds) {
                    if (scope.getPrincipalId().equals(String.valueOf(deptId))) {
                        result.put("scope", scope.getScope());
                        return result;
                    }
                }
            }
        }
        // 4) 默认：有部门归属则本部门，否则本人
        result.put("scope", deptIds.isEmpty() ? "self" : "dept");
        return result;
    }

    /** 删除范围配置。 */
    public void delete(Long id) {
        dataScopeMapper.deleteById(id);
    }
}
