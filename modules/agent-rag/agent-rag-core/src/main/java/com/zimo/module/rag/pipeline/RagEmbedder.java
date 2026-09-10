package com.zimo.module.rag.pipeline;

import com.zimo.module.rag.RagProperties;
import com.zimo.framework.ai.AiAgentProperties;
import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

/**
 * 文本向量化器。
 *
 * <p>调用 OpenAI 兼容 {@code /embeddings} 接口（baseUrl/apiKey 复用 {@link AiAgentProperties}）；
 * 网络不可用时降级为本地字符 n-gram 哈希向量，保证离线可用。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
public class RagEmbedder {

    private static final Logger log = LoggerFactory.getLogger(RagEmbedder.class);

    private final AiAgentProperties aiProperties;
    private final RagProperties ragProperties;
    private final RestTemplate restTemplate;
    /** 远程接口熔断标志：首次失败后本进程内后续调用直接走本地向量，避免重复长超时。 */
    private volatile boolean remoteDown = false;

    public RagEmbedder(AiAgentProperties aiProperties, RagProperties ragProperties) {
        this(aiProperties, ragProperties, new RestTemplate());
    }

    public RagEmbedder(AiAgentProperties aiProperties, RagProperties ragProperties, RestTemplate restTemplate) {
        this.aiProperties = aiProperties;
        this.ragProperties = ragProperties;
        this.restTemplate = restTemplate;
        if (restTemplate.getRequestFactory() instanceof org.springframework.http.client.SimpleClientHttpRequestFactory factory) {
            factory.setConnectTimeout(10000);
            factory.setReadTimeout(10000);
        }
    }

    /**
     * 单条文本向量化。
     *
     * @param text 文本
     * @return 向量
     */
    public float[] embed(String text) {
        return embedBatch(List.of(text == null ? "" : text)).get(0);
    }

    /**
     * 批量向量化。
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    public List<float[]> embedBatch(List<String> texts) {
        String baseUrl = aiProperties.getBaseUrl();
        String apiKey = aiProperties.getApiKey();
        if (!remoteDown && StrUtil.isNotBlank(baseUrl)
                && StrUtil.isNotBlank(apiKey)) {
            try {
                return embedRemote(baseUrl, apiKey, texts);
            } catch (Exception e) {
                remoteDown = true;
                log.warn("远程向量化失败，已熔断，后续使用本地哈希向量: {}", safeMessage(e));
            }
        } else if (!remoteDown) {
            log.warn("未配置模型 API，使用本地哈希向量");
        }
        return localEmbed(texts);
    }

    private List<float[]> embedRemote(String baseUrl, String apiKey, List<String> texts) {
        String endpoint = baseUrl.endsWith("/") ? baseUrl + "embeddings" : baseUrl + "/embeddings";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        Map<String, Object> body = Map.of(
                "model", ragProperties.getEmbeddingModel(),
                "input", texts);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(endpoint, request, Map.class);
        if (response == null) {
            throw new IllegalStateException("embeddings 接口无响应");
        }
        List<?> data = (List<?>) response.get("data");
        List<float[]> vectors = new ArrayList<>();
        for (Object item : data) {
            Map<?, ?> entry = (Map<?, ?>) item;
            List<?> embedding = (List<?>) entry.get("embedding");
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = ((Number) embedding.get(i)).floatValue();
            }
            vectors.add(vector);
        }
        return vectors;
    }

    /** 本地 n-gram 哈希向量（离线降级）。 */
    private List<float[]> localEmbed(List<String> texts) {
        int dim = ragProperties.getEmbeddingDimension();
        List<float[]> vectors = new ArrayList<>();
        for (String text : texts) {
            float[] vector = new float[dim];
            String normalized = text == null ? "" : text.toLowerCase();
            for (int i = 0; i + 2 <= normalized.length(); i++) {
                String gram = normalized.substring(i, i + 2);
                int hash = Math.floorMod(gram.hashCode(), dim);
                vector[hash] += 1.0f;
            }
            normalize(vector);
            vectors.add(vector);
        }
        return vectors;
    }

    private void normalize(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += v * v;
        }
        if (sum <= 0) {
            return;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
    }

    /**
     * 计算余弦相似度。
     *
     * @param a 向量 a
     * @param b 向量 b
     * @return 相似度 [-1, 1]
     */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return StrUtil.isBlank(message)
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}
