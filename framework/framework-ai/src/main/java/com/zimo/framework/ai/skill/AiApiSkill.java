package com.zimo.framework.ai.skill;

import java.util.Map;
import cn.hutool.core.util.StrUtil;
import java.util.Objects;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * AI API 技能实现，将技能调用参数转发到配置的远程 HTTP API。
 */
public class AiApiSkill implements AiSkill {
    private final AiApiSkillConfig config;
    private final RestClient restClient;

    /**
     * 创建远程 API 技能。
     *
     * @param config API 技能配置，名称和地址不能为空
     * @param builder Spring RestClient 构造器，用于继承应用侧 HTTP 配置
     */
    public AiApiSkill(AiApiSkillConfig config, RestClient.Builder builder) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        RestClient.Builder safeBuilder = builder == null ? RestClient.builder() : builder;
        this.restClient = safeBuilder.clone().baseUrl(config.baseUrl()).build();
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String description() {
        return config.description();
    }

    @Override
    public boolean readOnly() {
        return config.readOnly();
    }

    @Override
    public java.util.List<String> inputParameters() {
        return java.util.List.of("_body");
    }

    @Override
    public AiSkillResult call(Map<String, Object> arguments) {
        if (!config.enabled()) {
            return AiSkillResult.fail("API skill is disabled: " + config.name());
        }
        try {
            return AiSkillResult.ok(execute(arguments == null ? Map.of() : arguments));
        } catch (RestClientResponseException exception) {
            return AiSkillResult.fail(sanitizedFailure(exception.getResponseBodyAsString()));
        } catch (RestClientException exception) {
            return AiSkillResult.fail(sanitizedFailure(exception.getMessage()));
        }
    }

    private String execute(Map<String, Object> arguments) {
        HttpMethod httpMethod = HttpMethod.valueOf(config.method().toUpperCase());
        if (HttpMethod.GET.equals(httpMethod) || HttpMethod.DELETE.equals(httpMethod)) {
            return requestWithoutBody(httpMethod, arguments);
        }
        return requestWithBody(httpMethod, arguments);
    }

    private String requestWithoutBody(HttpMethod httpMethod, Map<String, Object> arguments) {
        String uri = uriWithQuery(arguments);
        return restClient.method(httpMethod)
                .uri(uri)
                .headers(headers -> config.headers().forEach(headers::add))
                .retrieve()
                .body(String.class);
    }

    private String requestWithBody(HttpMethod httpMethod, Map<String, Object> arguments) {
        return restClient.method(httpMethod)
                .uri(config.path())
                .headers(headers -> config.headers().forEach(headers::add))
                .contentType(MediaType.APPLICATION_JSON)
                .body(arguments)
                .retrieve()
                .body(String.class);
    }

    private String uriWithQuery(Map<String, Object> arguments) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(config.path());
        arguments.forEach((key, value) -> {
            if (value != null) {
                builder.queryParam(key, value);
            }
        });
        return builder.build().toUriString();
    }

    private String sanitizedFailure(String message) {
        String safeMessage = message == null ? "" : message;
        for (String value : config.headers().values()) {
            if (StrUtil.isNotBlank(value)) {
                safeMessage = safeMessage.replace(value, "[redacted]");
                safeMessage = safeMessage.replace(bearerToken(value), "[redacted]");
            }
        }
        return "API skill call failed: " + safeMessage;
    }

    private String bearerToken(String value) {
        String prefix = "Bearer ";
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }
}
