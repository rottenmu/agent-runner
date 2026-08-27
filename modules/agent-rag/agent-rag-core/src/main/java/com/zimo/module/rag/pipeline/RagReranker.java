package com.zimo.module.rag.pipeline;

import com.zimo.module.rag.RagProperties;
import java.util.ArrayList;
import cn.hutool.core.util.StrUtil;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

/**
 * 检索结果重排序器（Rerank）。
 *
 * <p>远程模式：调用兼容 {@code POST /rerank}（Cohere/Jina 风格），以 {@code model + query + documents}
 * 获取重排分数；未配置远程接口时使用本地余弦相似度重排（基于查询与切片向量）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
public class RagReranker {

    private static final Logger log = LoggerFactory.getLogger(RagReranker.class);

    private final RagProperties ragProperties;
    private final RagEmbedder embedder;
    private final RestTemplate restTemplate;

    public RagReranker(RagProperties ragProperties, RagEmbedder embedder) {
        this(ragProperties, embedder, new RestTemplate());
    }

    public RagReranker(RagProperties ragProperties, RagEmbedder embedder, RestTemplate restTemplate) {
        this.ragProperties = ragProperties;
        this.embedder = embedder;
        this.restTemplate = restTemplate;
    }

    /**
     * 重排结果项。
     *
     * @param index 原始索引
     * @param content 内容
     * @param score 重排分数
     */
    public record ScoredChunk(int index, String content, double score) {
    }

    /**
     * 对候选结果执行重排序。
     *
     * @param query 查询
     * @param candidates 候选内容列表
     * @return 重排后的结果（按分数降序）
     */
    public List<ScoredChunk> rerank(String query, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ScoredChunk> result = remoteRerank(query, candidates);
        if (result != null) {
            return result;
        }
        return localRerank(query, candidates);
    }

    /** 远程重排；未配置或失败返回 null。 */
    private List<ScoredChunk> remoteRerank(String query, List<String> candidates) {
        String baseUrl = ragProperties.getRerankBaseUrl();
        String model = ragProperties.getRerankModel();
        if (StrUtil.isBlank(baseUrl) || StrUtil.isBlank(model)) {
            return null;
        }
        try {
            String endpoint = baseUrl.endsWith("/") ? baseUrl + "rerank" : baseUrl + "/rerank";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of(
                    "model", model,
                    "query", query,
                    "documents", candidates);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(endpoint, request, Map.class);
            if (response == null) {
                return null;
            }
            List<?> results = (List<?>) response.get("results");
            List<ScoredChunk> chunks = new ArrayList<>();
            for (Object item : results) {
                Map<?, ?> entry = (Map<?, ?>) item;
                int index = ((Number) entry.get("index")).intValue();
                double score = entry.get("relevance_score") instanceof Number n ? n.doubleValue() : 0;
                if (index >= 0 && index < candidates.size()) {
                    chunks.add(new ScoredChunk(index, candidates.get(index), score));
                }
            }
            chunks.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
            return chunks;
        } catch (Exception e) {
            log.warn("远程重排失败，降级本地重排: {}", safeMessage(e));
            return null;
        }
    }

    /** 本地余弦相似度重排。 */
    private List<ScoredChunk> localRerank(String query, List<String> candidates) {
        float[] queryVector = embedder.embed(query);
        List<ScoredChunk> chunks = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            float[] candidateVector = embedder.embed(candidates.get(i));
            double score = RagEmbedder.cosine(queryVector, candidateVector);
            chunks.add(new ScoredChunk(i, candidates.get(i), score));
        }
        chunks.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return chunks;
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return StrUtil.isBlank(message)
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}
