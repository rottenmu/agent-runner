package com.zimo.module.rag.service;

import com.zimo.module.rag.entity.RagChunk;
import com.zimo.module.rag.entity.RagDocument;
import com.zimo.module.rag.mapper.RagChunkMapper;
import com.zimo.module.rag.mapper.RagDocumentMapper;
import com.zimo.module.rag.pipeline.RagEmbedder;
import com.zimo.module.rag.pipeline.RagReranker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * 检索服务：查询向量化 → 余弦召回 → 可选 Rerank 重排。
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
public class RagRetrieveService {

    private static final TypeReference<float[]> FLOAT_ARRAY = new TypeReference<>() {
    };

    private final RagChunkMapper chunkMapper;
    private final RagDocumentMapper documentMapper;
    private final RagEmbedder embedder;
    private final RagReranker reranker;
    private final ObjectMapper objectMapper;

    public RagRetrieveService(
            RagChunkMapper chunkMapper,
            RagDocumentMapper documentMapper,
            RagEmbedder embedder,
            RagReranker reranker,
            ObjectMapper objectMapper) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.embedder = embedder;
        this.reranker = reranker;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /**
     * 检索知识库。
     *
     * @param query 查询文本
     * @param kbId 限定知识库；为空检索全部
     * @param docId 限定文档；为空检索知识库全部
     * @param topK 返回条数
     * @param rerank 是否启用重排
     * @return 结果列表（按分数降序）
     */
    public List<Map<String, Object>> retrieve(String query, Long kbId, Long docId, int topK, boolean rerank) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        int limit = Math.min(Math.max(topK, 1), 50);
        var chunkQuery = Wrappers.<RagChunk>lambdaQuery()
                .eq(docId != null, RagChunk::getDocId, docId)
                .isNotNull(RagChunk::getVectorJson)
                .orderByAsc(RagChunk::getId);
        if (kbId != null) {
            List<Long> docIds = documentMapper.selectList(Wrappers.<RagDocument>lambdaQuery()
                            .eq(RagDocument::getKbId, kbId))
                    .stream().map(RagDocument::getId).toList();
            if (docIds.isEmpty()) {
                return List.of();
            }
            chunkQuery.in(RagChunk::getDocId, docIds);
        }
        List<RagChunk> chunks = chunkMapper.selectList(chunkQuery);

        // 候选集：全部切片余弦打分，取 3 倍 topK 进入重排
        long start = System.currentTimeMillis();
        float[] queryVector = embedder.embed(query);
        List<Scored> scored = new ArrayList<>();
        for (RagChunk chunk : chunks) {
            float[] vector = parseVector(chunk.getVectorJson());
            double score = RagEmbedder.cosine(queryVector, vector);
            scored.add(new Scored(chunk, score));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        int candidateLimit = Math.min(scored.size(), limit * 3);
        List<Scored> candidates = scored.subList(0, candidateLimit);

        List<Map<String, Object>> results = new ArrayList<>();
        if (rerank && !candidates.isEmpty()) {
            List<String> texts = candidates.stream()
                    .map(s -> s.chunk().getContent())
                    .toList();
            List<RagReranker.ScoredChunk> reranked = reranker.rerank(query, texts);
            Map<Long, Scored> byId = new LinkedHashMap<>();
            for (Scored s : candidates) {
                byId.put(s.chunk().getId(), s);
            }
            for (RagReranker.ScoredChunk item : reranked) {
                if (results.size() >= limit) {
                    break;
                }
                Scored original = byId.get(candidates.get(item.index()).chunk().getId());
                if (original == null) {
                    continue;
                }
                results.add(toResult(original.chunk(), item.score(), true));
            }
        } else {
            for (Scored s : candidates) {
                if (results.size() >= limit) {
                    break;
                }
                results.add(toResult(s.chunk(), s.score(), false));
            }
        }
        com.zimo.starter.ai.observ.TraceCollector.step("knowledge_retrieval",
                kbId == null ? "全部知识库" : "知识库#" + kbId,
                "{\"query\":\"" + safeJson(query) + "\",\"kbId\":" + kbId + ",\"topK\":" + limit + "}",
                "{\"hits\":" + results.size() + ",\"topScore\":" + (results.isEmpty() ? 0 : results.get(0).get("score")) + "}",
                System.currentTimeMillis() - start, "ok");
        return results;
    }

    private String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Map<String, Object> toResult(RagChunk chunk, double score, boolean reranked) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chunkId", chunk.getId());
        result.put("docId", chunk.getDocId());
        result.put("docName", docName(chunk.getDocId()));
        result.put("seq", chunk.getSeq());
        result.put("content", chunk.getContent());
        result.put("tableInfo", chunk.getTableInfo());
        result.put("score", Math.round(score * 10000) / 10000.0);
        result.put("reranked", reranked);
        return result;
    }

    private String docName(Long docId) {
        RagDocument document = documentMapper.selectById(docId);
        return document == null ? "未知文档" : document.getName();
    }

    private float[] parseVector(String vectorJson) {
        if (!StringUtils.hasText(vectorJson)) {
            return new float[0];
        }
        try {
            return objectMapper.readValue(vectorJson, FLOAT_ARRAY);
        } catch (Exception e) {
            return new float[0];
        }
    }

    private record Scored(RagChunk chunk, double score) {
    }
}
