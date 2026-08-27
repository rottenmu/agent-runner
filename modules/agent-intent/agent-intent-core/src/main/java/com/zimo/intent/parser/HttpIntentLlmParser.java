package com.zimo.intent.parser;

import com.zimo.intent.IntentProperties;
import com.zimo.intent.model.IntentLlmResult;
import com.zimo.intent.model.IntentParseResult;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 HTTP 的真实 LLM 意图解析实现（OpenAI 兼容 /v1/chat/completions）。
 *
 * <p>系统提示词优先级：{@link IntentProperties#getLlmPrompt()} 显式配置 →
 * classpath 模板 ai-intent/prompt-parse.txt（模式二完整提示词，{rules} 注入规则库）→
 * 内置简化提示词兜底。同时实现模式一（generateRules）与模式三（reviewRules）的 LLM 调用。</p>
 *
 * <p>请求失败、响应非 JSON、意图编码为空时返回 null，引擎自动降级为规则/启发式结果。
 * 适用于 qwen / deepseek / openai / 通义等 OpenAI 兼容端点，支持 HTTP 裸调用与 Bash+Curl 等价协议。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-14
 */
public class HttpIntentLlmParser implements IntentLlmParser {

    private static final Logger log = LoggerFactory.getLogger(HttpIntentLlmParser.class);

    private static final String DEFAULT_PROMPT = """
            你是企业业务大模型意图解析引擎。根据用户输入与历史对话，精准识别意图、抽取业务实体，
            输出严格 JSON（无解释无多余字符）。意图枚举：DATA_QUERY 业务数据查询 / DATA_EXPORT 数据导出 /
            ORDER_OPERATE 单据操作 / FAQ_ANSWER 知识问答 / GENERAL_CHAT 闲聊 / PARAM_CLARIFY 参数追问 /
            UNSUPPORTED 不支持 / RISK_REJECT 风险拒绝。
            判定规则：含删除/篡改/越权/敏感内容直接 RISK_REJECT；纯闲聊 GENERAL_CHAT 不调用工具；
            必填槽位缺失标记 requiredSlotMissing 且 needClarify=true；置信度 0-1；实体仅抽取已知槽位。
            输出格式：{"intentCode":"...","intentName":"...","confidence":0.0,"entities":{},
            "requiredSlotMissing":[],"needTool":false,"toolList":[],"needClarify":false,"clarifyPrompt":"",
            "isReject":false,"rejectReason":"","routeStrategy":"...","reason":"..."}
            """;

    private final ObjectMapper objectMapper;
    private final IntentProperties properties;
    private final HttpClient httpClient;

    public HttpIntentLlmParser() {
        this(new ObjectMapper(), new IntentProperties());
    }

    public HttpIntentLlmParser(ObjectMapper objectMapper, IntentProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, properties.getLlmTimeoutMs())))
                .build();
    }

    @Override
    public IntentLlmResult parse(String query, List<String> history, IntentParseResult ruleHint) {
        return parse(query, history, ruleHint, null);
    }

    @Override
    public IntentLlmResult parse(String query, List<String> history, IntentParseResult ruleHint, String rulesJson) {
        if (StrUtil.isBlank(properties.getLlmBaseUrl())) {
            log.debug("未配置 llm-base-url，跳过 LLM 解析");
            return null;
        }
        String prompt = IntentPromptTemplates.render(
                StrUtil.blankToDefault(properties.getLlmPrompt(), IntentPromptTemplates.parseTemplate()), rulesJson);
        String content = chat(StrUtil.blankToDefault(prompt, DEFAULT_PROMPT), buildUserContent(query, history));
        if (StrUtil.isBlank(content)) {
            return null;
        }
        return parseLlmResponse(content);
    }

    @Override
    public String generateRules(String scenario, String rulesJson) {
        if (StrUtil.isBlank(properties.getLlmBaseUrl())) {
            return null;
        }
        String prompt = IntentPromptTemplates.render(IntentPromptTemplates.generateTemplate(), rulesJson);
        if (StrUtil.isBlank(prompt)) {
            return null;
        }
        return chat(prompt, "业务场景描述：" + scenario);
    }

    @Override
    public String reviewRules(String rulesJson) {
        if (StrUtil.isBlank(properties.getLlmBaseUrl())) {
            return null;
        }
        String prompt = IntentPromptTemplates.render(IntentPromptTemplates.reviewTemplate(), rulesJson);
        if (StrUtil.isBlank(prompt)) {
            return null;
        }
        return chat(prompt, "请对输入规则库执行冲突校验、补全与优化，仅输出规范 JSON。");
    }

    /** 通用 OpenAI 兼容对话调用：返回首个 choice 文本内容，失败返回 null。 */
    private String chat(String systemPrompt, String userContent) {
        String url = completionsUrl(properties.getLlmBaseUrl());
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", StrUtil.blankToDefault(properties.getLlmModel(), "qwen-plus"));
            payload.put("temperature", 0.1);
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userContent)));
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(properties.getLlmTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            if (StrUtil.isNotBlank(properties.getLlmApiKey())) {
                builder.header("Authorization", "Bearer " + properties.getLlmApiKey());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("LLM 调用失败: HTTP {} body={}", response.statusCode(), truncate(response.body(), 200));
                return null;
            }
            return extractContent(response.body());
        } catch (Exception e) {
            log.warn("LLM 调用异常: {}", e.getMessage());
            return null;
        }
    }

    private String buildUserContent(String query, List<String> history) {
        StringBuilder context = new StringBuilder();
        if (history != null) {
            int start = Math.max(0, history.size() - 6);
            for (int i = start; i < history.size(); i++) {
                context.append(history.get(i)).append("\n");
            }
        }
        return (context.length() > 0 ? "历史对话：\n" + context + "\n" : "")
                + "当前用户输入：" + query;
    }

    private static String completionsUrl(String baseUrl) {
        if (baseUrl.endsWith("/chat/completions") || baseUrl.endsWith("/completions")) {
            return baseUrl;
        }
        return baseUrl.replaceAll("/+$", "") + "/v1/chat/completions";
    }

    private String extractContent(String body) throws Exception {
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
    }

    /** 解析 LLM 返回的模式二完整 JSON 契约（兼容代码块包裹与缺失字段）。 */
    private IntentLlmResult parseLlmResponse(String content) {
        String json = stripCodeFence(content);
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            String intentCode = String.valueOf(parsed.getOrDefault("intentCode", ""));
            if (StrUtil.isBlank(intentCode)) {
                return null;
            }
            double confidence = parsed.get("confidence") instanceof Number n ? n.doubleValue() : 0.6;
            String reason = parsed.get("reason") == null ? "LLM 解析" : String.valueOf(parsed.get("reason"));
            return IntentLlmResult.full(intentCode, stringOf(parsed.get("intentName")), confidence,
                    stringMap(parsed.get("entities")), stringList(parsed.get("requiredSlotMissing")),
                    boolOf(parsed.get("needClarify")), stringOf(parsed.get("clarifyPrompt")),
                    boolOf(parsed.get("isReject")), stringOf(parsed.get("rejectReason")),
                    boolOf(parsed.get("needTool")), stringList(parsed.get("toolList")),
                    stringOf(parsed.get("routeStrategy")), reason);
        } catch (Exception e) {
            log.warn("LLM 响应解析失败: {} content={}", e.getMessage(), truncate(content, 300));
            return null;
        }
    }

    /** 去除 ``` 代码块包裹并截取首尾大括号内的 JSON 主体。 */
    private static String stripCodeFence(String content) {
        String json = content.trim();
        if (json.startsWith("```")) {
            int first = json.indexOf('\n');
            int last = json.lastIndexOf("```");
            if (first > 0 && last > first) {
                json = json.substring(first + 1, last).trim();
            }
        }
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        return json;
    }

    private static boolean boolOf(Object value) {
        return value instanceof Boolean b && b;
    }

    private static String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Map<String, String> stringMap(Object raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            map.forEach((k, v) -> result.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
        }
        return result;
    }

    private static List<String> stringList(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            list.forEach(item -> {
                if (item != null && StrUtil.isNotBlank(String.valueOf(item))) {
                    result.add(String.valueOf(item));
                }
            });
        }
        return result;
    }

    private static String truncate(String text, int max) {
        return text == null ? "" : (text.length() > max ? text.substring(0, max) + "..." : text);
    }
}
