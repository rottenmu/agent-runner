package com.zimo.module.rag.service;

import com.zimo.module.rag.entity.RagDocument;
import com.zimo.module.rag.entity.RagKbPermission;
import com.zimo.module.rag.entity.RagKnowledgeBase;
import com.zimo.module.rag.mapper.RagDocumentMapper;
import com.zimo.module.rag.mapper.RagKbPermissionMapper;
import com.zimo.module.rag.mapper.RagKnowledgeBaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.util.StringUtils;

/**
 * 知识库管控服务：KB 生命周期、标签分类、文档归档与访问范围过滤。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class RagKbService {

    private final RagKnowledgeBaseMapper kbMapper;
    private final RagDocumentMapper documentMapper;
    private final RagKbPermissionService permissionService;
    private final RagKbPermissionMapper permissionMapper;
    private final ObjectMapper objectMapper;

    public RagKbService(
            RagKnowledgeBaseMapper kbMapper,
            RagDocumentMapper documentMapper,
            RagKbPermissionService permissionService,
            RagKbPermissionMapper permissionMapper,
            ObjectMapper objectMapper) {
        this.kbMapper = kbMapper;
        this.documentMapper = documentMapper;
        this.permissionService = permissionService;
        this.permissionMapper = permissionMapper;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /** 当前用户可访问的知识库列表（含访问级别）。 */
    public List<Map<String, Object>> listAccessibleKbs(String userId, boolean isAdmin) {
        List<RagKnowledgeBase> all = kbMapper.selectList(Wrappers.<RagKnowledgeBase>lambdaQuery()
                .orderByDesc(RagKnowledgeBase::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (RagKnowledgeBase kb : all) {
            String level = permissionService.accessLevelOf(userId, isAdmin, kb.getId());
            if (level == null) {
                continue;
            }
            Map<String, Object> item = toKbMap(kb);
            item.put("accessLevel", level);
            item.put("isOwner", userId != null && userId.equals(kb.getCreatedBy()));
            item.put("docCount", documentMapper.selectCount(Wrappers.<RagDocument>lambdaQuery()
                    .eq(RagDocument::getKbId, kb.getId())));
            result.add(item);
        }
        return result;
    }

    /** 创建知识库。 */
    public RagKnowledgeBase create(String name, String description, List<String> tags,
                                   String visibility, String createdBy, String tenantId) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("知识库名称不能为空");
        }
        RagKnowledgeBase kb = new RagKnowledgeBase();
        kb.setName(name.trim());
        kb.setDescription(description);
        kb.setTags(toJson(tags));
        kb.setVisibility(StringUtils.hasText(visibility) ? visibility.trim() : "private");
        kb.setCreatedBy(createdBy);
        kb.setTenantId(StringUtils.hasText(tenantId) ? tenantId : "default");
        kb.setEnabled(true);
        kb.setAutoSyncEnabled(false);
        kb.setAutoSyncCron("0 0 */6 * * ?");
        LocalDateTime now = LocalDateTime.now();
        kb.setCreatedAt(now);
        kb.setUpdatedAt(now);
        kbMapper.insert(kb);
        return kb;
    }

    /** 创建知识库（含自动更新配置）。 */
    public RagKnowledgeBase create(String name, String description, List<String> tags,
                                   String visibility, Boolean autoSyncEnabled, String autoSyncCron,
                                   String createdBy, String tenantId) {
        RagKnowledgeBase kb = create(name, description, tags, visibility, createdBy, tenantId);
        if (autoSyncEnabled != null) {
            kb.setAutoSyncEnabled(autoSyncEnabled);
        }
        if (StringUtils.hasText(autoSyncCron)) {
            kb.setAutoSyncCron(autoSyncCron.trim());
        }
        kbMapper.updateById(kb);
        return kb;
    }

    /** 更新知识库（校验管理权限）。 */
    public RagKnowledgeBase update(Long id, String name, String description, List<String> tags,
                                   String visibility, Boolean enabled,
                                   Boolean autoSyncEnabled, String autoSyncCron,
                                   String userId, boolean isAdmin) {
        RagKnowledgeBase kb = requireKb(id);
        permissionService.requireManage(userId, isAdmin, id);
        if (StringUtils.hasText(name)) {
            kb.setName(name.trim());
        }
        if (description != null) {
            kb.setDescription(description);
        }
        if (tags != null) {
            kb.setTags(toJson(tags));
        }
        if (StringUtils.hasText(visibility)) {
            kb.setVisibility(visibility.trim());
        }
        if (enabled != null) {
            kb.setEnabled(enabled);
        }
        if (autoSyncEnabled != null) {
            kb.setAutoSyncEnabled(autoSyncEnabled);
        }
        if (StringUtils.hasText(autoSyncCron)) {
            kb.setAutoSyncCron(autoSyncCron.trim());
        }
        kb.setUpdatedAt(LocalDateTime.now());
        kbMapper.updateById(kb);
        return kb;
    }

    /** 删除知识库（级联文档/权限/版本）。 */
    public boolean delete(Long id, String userId, boolean isAdmin) {
        requireKb(id);
        permissionService.requireManage(userId, isAdmin, id);
        // 级联删除：文档、权限（版本快照保留记录）
        documentMapper.delete(Wrappers.<RagDocument>lambdaQuery().eq(RagDocument::getKbId, id));
        permissionMapper.delete(Wrappers.<RagKbPermission>lambdaQuery().eq(RagKbPermission::getKbId, id));
        return kbMapper.deleteById(id) > 0;
    }

    /** 全部标签（跨 KB 统计）。 */
    public List<Map<String, Object>> allTags() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RagKnowledgeBase kb : kbMapper.selectList(null)) {
            for (String tag : parseTags(kb.getTags())) {
                counts.merge(tag, 1, Integer::sum);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        counts.forEach((tag, count) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tag", tag);
            item.put("count", count);
            result.add(item);
        });
        return result;
    }

    /** 文档列表（按知识库/标签筛选 + 访问校验）。 */
    public List<RagDocument> listDocuments(Long kbId, String tag, String userId, boolean isAdmin) {
        if (kbId != null) {
            permissionService.requireRead(userId, isAdmin, kbId);
        }
        return documentMapper.selectList(Wrappers.<RagDocument>lambdaQuery()
                .eq(kbId != null, RagDocument::getKbId, kbId)
                .orderByDesc(RagDocument::getId));
    }

    /** 归档文档到知识库（校验知识库写权限）。 */
    public RagDocument archive(Long docId, Long kbId, String userId, boolean isAdmin) {
        RagDocument document = documentMapper.selectById(docId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: " + docId);
        }
        permissionService.requireWrite(userId, isAdmin, kbId);
        document.setKbId(kbId);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        return document;
    }

    /** 设置文档标签。 */
    public RagDocument setDocumentTags(Long docId, List<String> tags) {
        RagDocument document = documentMapper.selectById(docId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: " + docId);
        }
        document.setTags(toJson(tags));
        documentMapper.updateById(document);
        return document;
    }

    private RagKnowledgeBase requireKb(Long id) {
        RagKnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new IllegalArgumentException("知识库不存在: " + id);
        }
        return kb;
    }

    private Map<String, Object> toKbMap(RagKnowledgeBase kb) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", kb.getId());
        map.put("name", kb.getName());
        map.put("description", kb.getDescription());
        map.put("tags", parseTags(kb.getTags()));
        map.put("visibility", kb.getVisibility());
        map.put("createdBy", kb.getCreatedBy());
        map.put("tenantId", kb.getTenantId());
        map.put("enabled", kb.getEnabled());
        map.put("autoSyncEnabled", kb.getAutoSyncEnabled());
        map.put("autoSyncCron", kb.getAutoSyncCron());
        map.put("lastSyncAt", kb.getLastSyncAt());
        map.put("createdAt", kb.getCreatedAt());
        map.put("updatedAt", kb.getUpdatedAt());
        return map;
    }

    private List<String> parseTags(String tagsJson) {
        if (!StringUtils.hasText(tagsJson)) {
            return List.of();
        }
        try {
            List<String> tags = objectMapper.readValue(tagsJson, List.class);
            return tags == null ? List.of() : tags;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(List<String> tags) {
        if (CollUtil.isEmpty(tags)) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (Exception e) {
            return "[]";
        }
    }
}
