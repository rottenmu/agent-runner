package com.zimo.module.rag.service;

import com.zimo.module.rag.entity.RagChunk;
import com.zimo.module.rag.entity.RagDocument;
import com.zimo.module.rag.entity.RagVersion;
import com.zimo.module.rag.mapper.RagChunkMapper;
import com.zimo.module.rag.mapper.RagDocumentMapper;
import com.zimo.module.rag.mapper.RagVersionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * 知识库版本回溯服务：处理前快照、版本列表、回滚恢复。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class RagVersionService {

    private final RagVersionMapper versionMapper;
    private final RagChunkMapper chunkMapper;
    private final RagDocumentMapper documentMapper;
    private final ObjectMapper objectMapper;

    public RagVersionService(
            RagVersionMapper versionMapper,
            RagChunkMapper chunkMapper,
            RagDocumentMapper documentMapper,
            ObjectMapper objectMapper) {
        this.versionMapper = versionMapper;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /**
     * 在处理前记录当前切片快照为新版本。
     *
     * @param doc 文档
     * @param chunks 当前切片
     * @param summary 摘要
     * @param createdBy 操作人
     * @return 版本号
     */
    public int snapshot(RagDocument doc, List<RagChunk> chunks, String summary, String createdBy) {
        int nextVersion = (doc.getVersion() == null ? 0 : doc.getVersion()) + 1;
        RagVersion version = new RagVersion();
        version.setKbId(doc.getKbId());
        version.setDocId(doc.getId());
        version.setVersion(nextVersion);
        version.setSummary(summary);
        version.setSnapshotJson(toJson(chunks == null ? List.of() : chunks));
        version.setAction("process");
        version.setCreatedBy(createdBy);
        version.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(version);
        return nextVersion;
    }

    /** 文档版本列表。 */
    public List<RagVersion> listByDocument(Long docId) {
        return versionMapper.selectList(Wrappers.<RagVersion>lambdaQuery()
                .eq(RagVersion::getDocId, docId)
                .orderByDesc(RagVersion::getVersion));
    }

    /**
     * 回滚到指定版本（恢复该版本的切片）。
     *
     * @param versionId 版本 ID
     * @param userId 操作人
     * @param isAdmin 是否管理员
     * @return 恢复的切片数
     */
    public int rollback(Long versionId, String userId, boolean isAdmin) {
        RagVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new IllegalArgumentException("版本不存在: " + versionId);
        }
        RagDocument document = documentMapper.selectById(version.getDocId());
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: " + version.getDocId());
        }
        List<RagChunk> restored = parseChunks(version.getSnapshotJson());
        chunkMapper.delete(Wrappers.<RagChunk>lambdaQuery().eq(RagChunk::getDocId, document.getId()));
        for (RagChunk chunk : restored) {
            chunk.setId(null);
            chunk.setCreatedAt(LocalDateTime.now());
            chunkMapper.insert(chunk);
        }
        // 记录回滚点
        RagVersion rollbackPoint = new RagVersion();
        rollbackPoint.setKbId(document.getKbId());
        rollbackPoint.setDocId(document.getId());
        rollbackPoint.setVersion((document.getVersion() == null ? 0 : document.getVersion()) + 1);
        rollbackPoint.setSummary("回滚自版本 v" + version.getVersion() + "，恢复切片 " + restored.size() + " 条");
        rollbackPoint.setSnapshotJson(toJson(restored));
        rollbackPoint.setAction("rollback");
        rollbackPoint.setCreatedBy(userId);
        rollbackPoint.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(rollbackPoint);

        document.setVersion(rollbackPoint.getVersion());
        document.setChunkCount(restored.size());
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        return restored.size();
    }

    private List<RagChunk> parseChunks(String snapshotJson) {
        if (!StringUtils.hasText(snapshotJson)) {
            return List.of();
        }
        try {
            List<RagChunk> chunks = objectMapper.readValue(snapshotJson, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, RagChunk.class));
            return chunks == null ? List.of() : chunks;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(List<RagChunk> chunks) {
        try {
            return objectMapper.writeValueAsString(chunks);
        } catch (Exception e) {
            return "[]";
        }
    }
}
