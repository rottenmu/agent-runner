package com.zimo.module.rag.service;

import com.zimo.module.rag.RagProperties;
import com.zimo.module.rag.entity.RagChunk;
import com.zimo.module.rag.entity.RagDocument;
import com.zimo.module.rag.mapper.RagChunkMapper;
import com.zimo.module.rag.mapper.RagDocumentMapper;
import com.zimo.module.rag.pipeline.DsFileParser;
import com.zimo.module.rag.pipeline.RagChunker;
import com.zimo.module.rag.pipeline.RagCleaner;
import com.zimo.module.rag.pipeline.RagEmbedder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * RAG 处理管道：文件解析 → 表格识别 → 清洗 → 自定义切片 → 向量化 → 入库。
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
public class RagPipelineService {

    private static final Logger log = LoggerFactory.getLogger(RagPipelineService.class);

    private final RagDocumentMapper documentMapper;
    private final RagChunkMapper chunkMapper;
    private final DsFileParser fileParser;
    private final RagCleaner cleaner;
    private final RagChunker chunker;
    private final RagEmbedder embedder;
    private final RagVersionService versionService;
    private final ObjectMapper objectMapper;

    public RagPipelineService(
            RagDocumentMapper documentMapper,
            RagChunkMapper chunkMapper,
            DsFileParser fileParser,
            RagCleaner cleaner,
            RagChunker chunker,
            RagEmbedder embedder,
            RagVersionService versionService,
            ObjectMapper objectMapper) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.fileParser = fileParser;
        this.cleaner = cleaner;
        this.chunker = chunker;
        this.embedder = embedder;
        this.versionService = versionService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /** 文档列表。 */
    public List<RagDocument> listDocuments() {
        return documentMapper.selectList(Wrappers.<RagDocument>lambdaQuery()
                .orderByDesc(RagDocument::getId));
    }

    /**
     * 注册文档（指定文件路径，状态 pending；可归档到知识库）。
     *
     * @param name 名称
     * @param sourcePath 文件路径
     * @param kbId 知识库 ID（可空）
     * @param tags 标签（可空）
     */
    public RagDocument createDocument(String name, String sourcePath, Long kbId, List<String> tags) {
        if (!StringUtils.hasText(sourcePath)) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        File file = new File(sourcePath);
        if (!file.isFile()) {
            throw new IllegalArgumentException("文件不存在: " + sourcePath);
        }
        RagDocument document = new RagDocument();
        document.setName(StringUtils.hasText(name) ? name : file.getName());
        document.setDocType(extension(file.getName()));
        document.setSourcePath(file.getAbsolutePath());
        document.setKbId(kbId);
        document.setTags(toJson(tags));
        document.setStatus("pending");
        LocalDateTime now = LocalDateTime.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        documentMapper.insert(document);
        return document;
    }

    /**
     * 执行处理管道：解析 → 清洗 → 切片 → 向量化。
     *
     * @param id 文档 ID
     * @param options 可选切片参数 {splitBy, chunkSize, overlap}
     * @return 处理摘要
     */
    public Map<String, Object> processDocument(Long id, Map<String, Object> options) {
        RagDocument document = requireDocument(id);
        document.setStatus("processing");
        document.setError(null);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        try {
            DsFileParser.DocumentContent content = fileParser.parse(new File(document.getSourcePath()));
            String cleanedText = cleaner.clean(content.text(), true);
            List<String> cleanedTables = content.tables() == null
                    ? List.of()
                    : content.tables().stream()
                            .map(table -> cleaner.clean(table, true))
                            .filter(StringUtils::hasText)
                            .toList();

            RagProperties effectiveProps = effectiveProperties(options);
            List<RagChunker.Chunk> chunks = chunker.chunk(cleanedText, cleanedTables, effectiveProps);

            // 批量向量化
            List<String> texts = chunks.stream().map(RagChunker.Chunk::content).toList();
            List<float[]> vectors = embedder.embedBatch(texts);

            // 处理前记录当前切片快照（版本回溯）
            String createdBy = options == null ? null : str(options.get("createdBy"));
            if (StrUtil.isBlank(createdBy)) {
                createdBy = "system";
            }
            List<RagChunk> existing = chunkMapper.selectList(Wrappers.<RagChunk>lambdaQuery()
                    .eq(RagChunk::getDocId, id));
            int newVersion = 0;
            if (!existing.isEmpty()) {
                newVersion = versionService.snapshot(document, existing,
                        "处理前快照：切片 " + existing.size() + " 条", createdBy);
            }

            // 清旧切片并写入
            chunkMapper.delete(Wrappers.<RagChunk>lambdaQuery().eq(RagChunk::getDocId, id));
            for (int i = 0; i < chunks.size(); i++) {
                RagChunker.Chunk chunk = chunks.get(i);
                RagChunk entity = new RagChunk();
                entity.setDocId(id);
                entity.setSeq(i + 1);
                entity.setContent(chunk.content());
                entity.setTableInfo(chunk.tableInfo());
                entity.setMetadata(toJson(chunk.metadata()));
                entity.setVectorJson(toJson(vectors.get(i)));
                entity.setCreatedAt(LocalDateTime.now());
                chunkMapper.insert(entity);
            }

            document.setStatus("done");
            document.setTotalChars(cleanedText.length());
            document.setTableCount(cleanedTables.size());
            document.setChunkCount(chunks.size());
            document.setVectorized(true);
            document.setVersion(newVersion > 0 ? newVersion : 1);
            document.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(document);
            log.info("RAG 文档处理完成: id={}, chunks={}, tables={}", id, chunks.size(), cleanedTables.size());

            return Map.of(
                    "documentId", id,
                    "totalChars", cleanedText.length(),
                    "tableCount", cleanedTables.size(),
                    "chunkCount", chunks.size(),
                    "vectorized", true);
        } catch (Exception e) {
            document.setStatus("failed");
            document.setError(safeMessage(e));
            document.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(document);
            log.warn("RAG 文档处理失败: id={}", id, e);
            throw new IllegalStateException("文档处理失败: " + safeMessage(e));
        }
    }

    /** 删除文档及切片。 */
    public boolean deleteDocument(Long id) {
        chunkMapper.delete(Wrappers.<RagChunk>lambdaQuery().eq(RagChunk::getDocId, id));
        return documentMapper.deleteById(id) > 0;
    }

    /** 文档切片列表。 */
    public List<RagChunk> chunksByDocument(Long id) {
        return chunkMapper.selectList(Wrappers.<RagChunk>lambdaQuery()
                .eq(RagChunk::getDocId, id)
                .orderByAsc(RagChunk::getSeq));
    }

    private RagProperties effectiveProperties(Map<String, Object> options) {
        RagProperties props = new RagProperties();
        props.setSplitBy(options != null && StringUtils.hasText(str(options.get("splitBy")))
                ? str(options.get("splitBy")) : "paragraph");
        if (options != null && options.get("chunkSize") instanceof Number n && n.intValue() > 0) {
            props.setChunkSize(n.intValue());
        } else {
            props.setChunkSize(800);
        }
        if (options != null && options.get("overlap") instanceof Number n) {
            props.setOverlap(n.intValue());
        } else {
            props.setOverlap(100);
        }
        return props;
    }

    public RagDocument requireDocument(Long id) {
        RagDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: id=" + id);
        }
        return document;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "unknown";
    }

    private String toJson(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return StrUtil.isBlank(message)
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}
