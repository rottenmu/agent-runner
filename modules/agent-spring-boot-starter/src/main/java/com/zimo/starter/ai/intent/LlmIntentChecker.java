package com.zimo.starter.ai.intent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 方式二：大模型 Prompt 少样本意图校验器。
 *
 * <p>构造含技能意图声明 / few-shot 样例 / JSON Schema 的系统提示词，
 * 调用 OpenAI 兼容 /v1/chat/completions 端点，强制模型输出结构化 JSON，
 * 解析为 {@link IntentCheckResult}。适用于复杂口语化、模糊语义场景。</p>
 *
 * <p>未配置 {@code llm.base-url} 或调用失败时返回 unknown（不阻断其他 Checker /
 * 其他技能）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-15
 */
public class LlmIntentChecker implements IntentChecker {

    /** LLM 配置来源（可注入 Spring 配置或直接构建）。 */
    public record LlmConfig(String baseUrl, String apiKey, String model, int timeoutMs) {
        public static LlmConfig disabled() {
            return new LlmConfig("", "", "qwen-plus", 30000);
        }
    }

    private final LlmConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmIntentChecker(LlmConfig config) {
        this.config = config == null ? LlmConfig.disabled() : config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, this.config.timeoutMs())))
                .build();
    }

    @Override
    public String name() {
        return "llm";
    }

    @Override
    public IntentCheckResult check(String userQuery, ConversationContext context, IntentCheckableSkill skill) {
        if (userQuery == null || userQuery.isBlank()) {
            return IntentCheckResult.unknown("输入为空");
        }
        if (config.baseUrl() == null || config.baseUrl().isBlank()) {
            return IntentCheckResult.unknown("LLM 未配置 base-url，跳过 LLM 校验");
        }
        try {
            String prompt = buildPrompt(skill);
            String url = config.baseUrl().endsWith("/chat/completions")
                    ? config.baseUrl() : config.baseUrl().replaceAll("/+$", "") + "/v1/chat/completions";

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", config.model());
            payload.put("temperature", 0.1);
            payload.put("response_format", Map.of("type", "json_object"));
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", prompt),
                    Map.of("role", "user", "content", buildUserMessage(userQuery, context))));

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(config.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)));
            if (config.apiKey() != null && !config.apiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + config.apiKey());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return IntentCheckResult.unknown("LLM 调用失败 HTTP " + response.statusCode());
            }
            return parseResponse(response.body(), skill);
        } catch (Exception e) {
            return IntentCheckResult.unknown("LLM 校验异常: " + e.getMessage());
        }
    }

    /** 系统提示词：技能意图声明 + few-shot 样例 + JSON Schema 强制输出。 */
    private String buildPrompt(IntentCheckableSkill skill) {
        StringBuilder sb = new StringBuilder();
        sb.append(IntentCheckPrompts.SYSTEM_PROMPT).append("\n\n");
        sb.append("【目标技能】\n");
        sb.append("技能名: ").append(skill.name()).append("\n");
        sb.append("技能描述: ").append(descriptionOf(skill)).append("\n");
        sb.append("支持的意图: ").append(skill.supportedIntents()).append("\n");
        if (skill.intentSamples() != null && !skill.intentSamples().isEmpty()) {
            sb.append("意图示例(few-shot):\n");
            for (String sample : skill.intentSamples()) {
                sb.append("  - ").append(sample).append("\n");
            }
        }
        sb.append("置信阈值: ").append(skill.confidenceThreshold()).append("\n");
        return sb.toString();
    }

    private String buildUserMessage(String userQuery, ConversationContext context) {
        StringBuilder sb = new StringBuilder("用户输入：" + userQuery);
        if (context != null && context.history() != null && !context.history().isEmpty()) {
            sb.append("\n历史对话（供指代消解）：\n");
            for (String message : context.history()) {
                sb.append(message).append("\n");
            }
        }
        return sb.toString();
    }

    private IntentCheckResult parseResponse(String body, IntentCheckableSkill skill) throws Exception {
        // 提取 choices[0].message.content
        String content = extractContent(body);
        if (content == null) {
            return IntentCheckResult.unknown("LLM 响应无内容");
        }
        // 提取 JSON 对象
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return IntentCheckResult.unknown("LLM 响应非 JSON: " + truncate(content));
        }
        Map<String, Object> parsed = objectMapper.readValue(content.substring(start, end + 1), new TypeReference<>() {
        });
        boolean isMatch = Boolean.parseBoolean(String.valueOf(parsed.getOrDefault("isMatch", "false")));
        double confidence = parsed.get("confidence") instanceof Number n ? n.doubleValue() : 0;
        String reason = String.valueOf(parsed.getOrDefault("reason", "LLM 判定"));
        boolean needClarify = Boolean.parseBoolean(String.valueOf(parsed.getOrDefault("needClarify", "false")));
        String intentName = parsed.get("intentName") == null ? firstIntent(skill) : String.valueOf(parsed.get("intentName"));

        confidence = IntentCheckResult.normalize(confidence);
        if (!isMatch) {
            return IntentCheckResult.noMatch("LLM 判定不命中: " + reason);
        }
        double threshold = skill.confidenceThreshold();
        if (confidence < threshold) {
            return IntentCheckResult.matchWithClarify(intentName, confidence,
                    "LLM 置信 " + String.format("%.2f", confidence) + " 低于阈值 " + threshold + "，需澄清");
        }
        return needClarify
                ? IntentCheckResult.matchWithClarify(intentName, confidence, "LLM 判定命中但需澄清: " + reason)
                : IntentCheckResult.match(intentName, confidence, "LLM 判定命中: " + reason);
    }

    private String firstIntent(IntentCheckableSkill skill) {
        List<String> intents = skill.supportedIntents();
        return intents == null || intents.isEmpty() ? skill.name() : intents.get(0);
    }

    private String descriptionOf(IntentCheckableSkill skill) {
        String description = skill.description();
        return description == null ? "" : description;
    }

    /** 提取 choices[0].message.content（OpenAI 兼容响应结构）。 */
    private String extractContent(String body) {
        try {
            Map<String, Object> root = objectMapper.readValue(body, new TypeReference<>() {
            });
            Object choices = root.get("choices");
            if (!(choices instanceof List<?> list) || list.isEmpty()) {
                return null;
            }
            Object first = list.get(0);
            if (!(first instanceof Map<?, ?> choice)) {
                return null;
            }
            Object message = choice.get("message");
            if (!(message instanceof Map<?, ?> msg)) {
                return null;
            }
            Object content = msg.get("content");
            return content == null ? null : String.valueOf(content);
        } catch (Exception e) {
            return null;
        }
    }

    /** 请求体序列化（Jackson）。 */
    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String truncate(String text) {
        return text == null ? "" : (text.length() > 200 ? text.substring(0, 200) + "..." : text);
    }
}
