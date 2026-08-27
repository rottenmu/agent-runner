package com.zimo.module.rag.service;

import com.zimo.starter.ai.AiAgentProperties;
import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

/**
 * 知识库问答服务：检索相关片段 + 调用大模型生成答案（带来源引用）。
 *
 * <p>模型不可用（未配置或网络失败）时降级为「检索摘要」模式，仍返回检索到的知识片段。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class RagChatService {

    private static final Logger log = LoggerFactory.getLogger(RagChatService.class);

    private final RagRetrieveService retrieveService;
    private final AiAgentProperties aiProperties;
    private final RestTemplate restTemplate;
    /** 模型熔断标志：首次失败后本进程内直接走检索摘要模式。 */
    private volatile boolean modelDown = false;

    public RagChatService(RagRetrieveService retrieveService, AiAgentProperties aiProperties) {
        this(retrieveService, aiProperties, new RestTemplate());
    }

    public RagChatService(RagRetrieveService retrieveService, AiAgentProperties aiProperties,
                          RestTemplate restTemplate) {
        this.retrieveService = retrieveService;
        this.aiProperties = aiProperties;
        this.restTemplate = restTemplate;
        if (restTemplate.getRequestFactory() instanceof org.springframework.http.client.SimpleClientHttpRequestFactory factory) {
            factory.setConnectTimeout(10000);
            factory.setReadTimeout(30000);
        }
    }

    /**
     * 知识库问答。
     *
     * @param question 问题
     * @param kbId 知识库（可空，全库）
     * @param topK 检索条数
     * @param rerank 是否重排
     * @return { answer, sources, modelUsed }
     */
    public Map<String, Object> chat(String question, Long kbId, int topK, boolean rerank) {
        List<Map<String, Object>> sources = retrieveService.retrieve(question, kbId, null, topK, rerank);
        String answer;
        String modelUsed;
        if (!modelDown && modelConfigured()) {
            try {
                answer = generate(question, sources);
                modelUsed = aiProperties.getModelName();
                return result(answer, sources, modelUsed);
            } catch (Exception e) {
                modelDown = true;
                log.warn("问答模型调用失败，降级检索摘要: {}", safeMessage(e));
            }
        }
        answer = fallbackAnswer(question, sources);
        modelUsed = "retrieval-only";
        return result(answer, sources, modelUsed);
    }

    private Map<String, Object> result(String answer, List<Map<String, Object>> sources, String modelUsed) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer);
        result.put("sources", sources);
        result.put("modelUsed", modelUsed);
        return result;
    }

    private boolean modelConfigured() {
        return aiProperties.getBaseUrl() != null && !aiProperties.getBaseUrl().isBlank()
                && aiProperties.getApiKey() != null && !aiProperties.getApiKey().isBlank();
    }

    private String generate(String question, List<Map<String, Object>> sources) {
        StringBuilder context = new StringBuilder();
        if (!sources.isEmpty()) {
            context.append("以下是知识库检索到的参考资料：\n");
            for (int i = 0; i < sources.size(); i++) {
                Map<String, Object> source = sources.get(i);
                context.append("[").append(i + 1).append("] 来源：")
                        .append(source.get("docName")).append(" #").append(source.get("seq"))
                        .append("\n").append(source.get("content")).append("\n\n");
            }
        } else {
            context.append("（知识库中未检索到相关内容）");
        }
        String systemPrompt = "你是企业知识库问答助手。请仅依据提供的参考资料回答用户问题；"
                + "若资料不足以回答，请明确说明。回答末尾用 [来源编号] 标注引用。";
        String userPrompt = "参考资料：\n" + context + "\n问题：" + question;

        String endpoint = aiProperties.getBaseUrl().endsWith("/")
                ? aiProperties.getBaseUrl() + "chat/completions"
                : aiProperties.getBaseUrl() + "/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiProperties.getApiKey());
        Map<String, Object> body = Map.of(
                "model", aiProperties.getModelName(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.3);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(endpoint, request, Map.class);
        if (response == null) {
            throw new IllegalStateException("chat/completions 无响应");
        }
        List<?> choices = (List<?>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("chat/completions 无 choices");
        }
        Map<?, ?> choice = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) choice.get("message");
        Object content = message == null ? null : message.get("content");
        return content == null ? "" : String.valueOf(content).trim();
    }

    /** 检索摘要降级答案。 */
    private String fallbackAnswer(String question, List<Map<String, Object>> sources) {
        StringBuilder builder = new StringBuilder();
        builder.append("（模型调用不可用，以下为知识库检索结果摘要）\n");
        builder.append("问题：").append(question).append("\n\n");
        if (sources.isEmpty()) {
            builder.append("知识库中未检索到相关内容。");
            return builder.toString();
        }
        for (int i = 0; i < sources.size(); i++) {
            Map<String, Object> source = sources.get(i);
            builder.append("[").append(i + 1).append("] ").append(source.get("docName"))
                    .append(" #").append(source.get("seq"))
                    .append(" (score=").append(source.get("score")).append(")\n")
                    .append(source.get("content")).append("\n\n");
        }
        return builder.toString();
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return StrUtil.isBlank(message)
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}
