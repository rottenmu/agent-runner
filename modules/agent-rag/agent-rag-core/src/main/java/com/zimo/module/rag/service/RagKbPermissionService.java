package com.zimo.module.rag.service;

import com.zimo.module.rag.entity.RagKbPermission;
import com.zimo.module.rag.entity.RagKnowledgeBase;
import com.zimo.module.rag.mapper.RagKbPermissionMapper;
import com.zimo.module.rag.mapper.RagKnowledgeBaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * 知识库权限隔离服务。
 *
 * <p>访问级别判定：管理员 &gt; 知识库拥有者 &gt; 显式授权(manage/write/read) &gt; shared 只读 &gt; 无权限。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class RagKbPermissionService {

    public static final String LEVEL_MANAGE = "manage";
    public static final String LEVEL_WRITE = "write";
    public static final String LEVEL_READ = "read";

    private final RagKbPermissionMapper permissionMapper;
    private final RagKnowledgeBaseMapper kbMapper;

    public RagKbPermissionService(
            RagKbPermissionMapper permissionMapper,
            RagKnowledgeBaseMapper kbMapper) {
        this.permissionMapper = permissionMapper;
        this.kbMapper = kbMapper;
    }

    /**
     * 授予权限。
     *
     * @param kbId 知识库 ID
     * @param principalType user=用户，role=角色
     * @param principalId 主体标识
     * @param accessLevel read/write/manage
     */
    public RagKbPermission grant(Long kbId, String principalType, String principalId, String accessLevel) {
        requireKb(kbId);
        if (!StringUtils.hasText(principalId)) {
            throw new IllegalArgumentException("主体标识不能为空");
        }
        String level = StringUtils.hasText(accessLevel) ? accessLevel.trim() : LEVEL_READ;
        if (!List.of(LEVEL_READ, LEVEL_WRITE, LEVEL_MANAGE).contains(level)) {
            throw new IllegalArgumentException("无效的访问级别: " + level);
        }
        RagKbPermission existing = permissionMapper.selectOne(Wrappers.<RagKbPermission>lambdaQuery()
                .eq(RagKbPermission::getKbId, kbId)
                .eq(RagKbPermission::getPrincipalType, principalType == null ? "user" : principalType)
                .eq(RagKbPermission::getPrincipalId, principalId));
        if (existing != null) {
            existing.setAccessLevel(level);
            permissionMapper.updateById(existing);
            return existing;
        }
        RagKbPermission permission = new RagKbPermission();
        permission.setKbId(kbId);
        permission.setPrincipalType(principalType == null ? "user" : principalType);
        permission.setPrincipalId(principalId.trim());
        permission.setAccessLevel(level);
        permission.setCreatedAt(LocalDateTime.now());
        permissionMapper.insert(permission);
        return permission;
    }

    /** 撤销权限。 */
    public boolean revoke(Long id) {
        return permissionMapper.deleteById(id) > 0;
    }

    /** 知识库权限列表。 */
    public List<RagKbPermission> listByKb(Long kbId) {
        return permissionMapper.selectList(Wrappers.<RagKbPermission>lambdaQuery()
                .eq(RagKbPermission::getKbId, kbId)
                .orderByAsc(RagKbPermission::getId));
    }

    /**
     * 计算用户对知识库的访问级别。
     *
     * @param userId 用户标识
     * @param isAdmin 是否管理员
     * @param kbId 知识库 ID
     * @return manage/write/read 或 null（无权限）
     */
    public String accessLevelOf(String userId, boolean isAdmin, Long kbId) {
        RagKnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            return null;
        }
        if (isAdmin || (userId != null && userId.equals(kb.getCreatedBy()))) {
            return LEVEL_MANAGE;
        }
        RagKbPermission permission = permissionMapper.selectOne(Wrappers.<RagKbPermission>lambdaQuery()
                .eq(RagKbPermission::getKbId, kbId)
                .eq(RagKbPermission::getPrincipalType, "user")
                .eq(RagKbPermission::getPrincipalId, userId == null ? "" : userId));
        if (permission != null) {
            return permission.getAccessLevel();
        }
        if (StringUtils.hasText(kb.getVisibility()) && "shared".equals(kb.getVisibility().trim())) {
            return LEVEL_READ;
        }
        return null;
    }

    /** 校验只读访问，不通过抛异常。 */
    public void requireRead(String userId, boolean isAdmin, Long kbId) {
        requireLevel(userId, isAdmin, kbId, LEVEL_READ);
    }

    /** 校验写权限（含管理）。 */
    public void requireWrite(String userId, boolean isAdmin, Long kbId) {
        requireLevel(userId, isAdmin, kbId, LEVEL_WRITE);
    }

    /** 校验管理权限。 */
    public void requireManage(String userId, boolean isAdmin, Long kbId) {
        requireLevel(userId, isAdmin, kbId, LEVEL_MANAGE);
    }

    private void requireLevel(String userId, boolean isAdmin, Long kbId, String level) {
        String actual = accessLevelOf(userId, isAdmin, kbId);
        if (actual == null) {
            throw new SecurityException("无权限访问知识库: kb=" + kbId);
        }
        int actualRank = rank(actual);
        int requiredRank = rank(level);
        if (actualRank < requiredRank) {
            throw new SecurityException("权限不足，需要 " + level + " 级别，当前为 " + actual);
        }
    }

    private int rank(String level) {
        return switch (level == null ? "" : level) {
            case LEVEL_MANAGE -> 3;
            case LEVEL_WRITE -> 2;
            case LEVEL_READ -> 1;
            default -> 0;
        };
    }

    private RagKnowledgeBase requireKb(Long kbId) {
        RagKnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new IllegalArgumentException("知识库不存在: " + kbId);
        }
        return kb;
    }
}
