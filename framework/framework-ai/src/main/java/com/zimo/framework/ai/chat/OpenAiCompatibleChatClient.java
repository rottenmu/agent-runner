package com.zimo.framework.ai.chat;

import com.zimo.module.agentmemory.chat.AiChatMessage;
import com.zimo.framework.ai.AiAgentProperties;
import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class OpenAiCompatibleChatClient implements AiChatClient {
    private final AiAgentProperties properties;
    private final RestClient restClient;

    public OpenAiCompatibleChatClient(AiAgentProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(trimTrailingSlash(properties.getBaseUrl())).build();
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        try {
            Map<String, Object> body = Map.of(
                    "model", request.modelName(),
                    "messages", buildMessages(request),
                    "temperature", request.temperature(),
                    "max_tokens", request.maxTokens());
            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            String content = extractContent(response);
            if (StrUtil.isBlank(content)) {
                return AiChatResponse.fail("大模型调用失败：响应内容为空");
            }
            return AiChatResponse.ok(content);
        } catch (RestClientException e) {
            return AiChatResponse.fail("大模型调用失败：" + safeMessage(e));
        } catch (Exception e) {
            return AiChatResponse.fail("大模型响应解析失败：" + safeMessage(e));
        }
    }

    private String extractContent(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            return null;
        }
        Object messageValue = choice.get("message");
        if (!(messageValue instanceof Map<?, ?> message)) {
            return null;
        }
        Object content = message.get("content");
        return content == null ? null : String.valueOf(content);
    }

    private List<Map<String, String>> buildMessages(AiChatRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        String systemPrompt = hasText(request.systemPrompt()) ? request.systemPrompt() : properties.getSystemPrompt();
        if (hasText(systemPrompt)) {
            messages.add(message("system", systemPrompt));
        }
        for (AiChatMessage historyMessage : request.history()) {
            if (historyMessage != null && hasText(historyMessage.role()) && hasText(historyMessage.content())) {
                messages.add(message(historyMessage.role(), historyMessage.content()));
            }
        }
        messages.add(message("user", request.message()));
        return messages;
    }

    private Map<String, String> message(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    private String trimTrailingSlash(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (StrUtil.isBlank(message)) {
            return e.getClass().getSimpleName();
        }
        String apiKey = properties.getApiKey();
        if (StrUtil.isNotBlank(apiKey)) {
            return message.replace(apiKey, "[redacted]");
        }
        return message;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
