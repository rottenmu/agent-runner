package com.zimo.module.tools.govern;

import java.util.Map;
import cn.hutool.core.util.StrUtil;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StringUtils;

/**
 * 通用 HTTP 业务接口执行器：插件工具通过本执行器调用配置的 HTTP 接口。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class GenericHttpExecutor implements ToolExecutor {

    private static final int MAX_BODY = 4000;

    private final String name;
    private final String description;
    private final String method;
    private final String baseUrl;
    private final String path;
    private final boolean readOnly;
    private final RestTemplate restTemplate = new RestTemplate();

    public GenericHttpExecutor(String name, String description, String method,
                               String baseUrl, String path, boolean readOnly) {
        this.name = name;
        this.description = description;
        this.method = StrUtil.isBlank(method) ? "GET" : method.toUpperCase();
        this.baseUrl = baseUrl;
        this.path = path;
        this.readOnly = readOnly;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean readOnly() {
        return readOnly;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("插件接口未配置 baseUrl（请设置 ai.tools.plugin-base-url 或在插件配置中指定）");
        }
        String resolvedPath = path == null ? "" : path;
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            resolvedPath = resolvedPath.replace("{" + entry.getKey() + "}",
                    String.valueOf(entry.getValue()));
        }
        String url = baseUrl.endsWith("/") ? baseUrl + resolvedPath.replaceFirst("^/", "")
                : baseUrl + (resolvedPath.startsWith("/") ? resolvedPath : "/" + resolvedPath);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String bodyJson = null;
        if (arguments.get("_body") != null) {
            bodyJson = String.valueOf(arguments.get("_body"));
        }
        HttpEntity<String> entity = new HttpEntity<>(bodyJson, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.valueOf(method), entity, String.class);
        String content = response.getBody() == null ? "" : response.getBody();
        if (content.length() > MAX_BODY) {
            content = content.substring(0, MAX_BODY) + "\n...(已截断)";
        }
        return "HTTP " + method + " " + url + " -> " + response.getStatusCode().value() + "\n" + content;
    }
}
