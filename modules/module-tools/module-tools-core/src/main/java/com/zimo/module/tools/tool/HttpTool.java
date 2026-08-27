package com.zimo.module.tools.tool;

import com.zimo.starter.ai.skill.AiSkill;
import cn.hutool.core.util.StrUtil;
import com.zimo.starter.ai.skill.AiSkillResult;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP 请求工具：GET / POST / PUT / DELETE / PATCH，支持自定义请求头与 JSON 体。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class HttpTool implements AiSkill {

    private static final int MAX_BODY = 8000;

    private final RestTemplate restTemplate;

    public HttpTool() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String name() {
        return "http_request";
    }

    @Override
    public String description() {
        return "HTTP 请求：method(GET/POST/PUT/DELETE/PATCH，默认 GET)、url(必填)、"
                + "headers(JSON 对象，可选)、body(JSON 字符串，可选)。返回状态码与响应内容（截断 8000 字符）。";
    }

    @Override
    public boolean readOnly() {
        return false;
    }

    @Override
    public AiSkillResult call(Map<String, Object> arguments) {
        if (arguments == null) {
            return AiSkillResult.fail("缺少参数");
        }
        String url = str(arguments.get("url"));
        if (StrUtil.isBlank(url)) {
            return AiSkillResult.fail("请提供 url");
        }
        String method = str(arguments.get("method"));
        if (method.isBlank()) {
            method = "GET";
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (arguments.get("headers") instanceof Map<?, ?> headerMap) {
                headerMap.forEach((k, v) -> headers.set(String.valueOf(k), String.valueOf(v)));
            }
            String body = str(arguments.get("body"));
            HttpEntity<String> entity = new HttpEntity<>(body.isBlank() ? null : body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.valueOf(method.toUpperCase()), entity, String.class);
            String content = response.getBody() == null ? "" : response.getBody();
            if (content.length() > MAX_BODY) {
                content = content.substring(0, MAX_BODY) + "\n...(已截断)";
            }
            return AiSkillResult.ok("HTTP " + method.toUpperCase() + " " + url
                    + "\n状态码: " + response.getStatusCode().value()
                    + "\n响应:\n" + content);
        } catch (Exception e) {
            return AiSkillResult.fail("HTTP 请求失败: " + safeMessage(e));
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
