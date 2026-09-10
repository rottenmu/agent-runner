package com.zimo.module.feishu.autoconfig;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.feishu.config.FeishuConfigProvider;
import com.zimo.module.feishu.config.FeishuRuntimeConfig;
import com.zimo.module.feishu.file.FeishuFileClient;
import com.zimo.module.feishu.file.FeishuFileDownloadResult;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.util.StringUtils;

public class OfficialFeishuFileClient implements FeishuFileClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String OPEN_API_BASE = "https://open.feishu.cn/open-apis";

    private final FeishuConfigProvider configProvider;
    private final HttpClient httpClient;

    public OfficialFeishuFileClient(FeishuConfigProvider configProvider) {
        this(configProvider, HttpClient.newHttpClient());
    }

    OfficialFeishuFileClient(FeishuConfigProvider configProvider, HttpClient httpClient) {
        this.configProvider = configProvider;
        this.httpClient = httpClient;
    }

    @Override
    public FeishuFileDownloadResult downloadMessageFile(String messageId, String fileKey) {
        if (!StringUtils.hasText(messageId) || !StringUtils.hasText(fileKey)) {
            return FeishuFileDownloadResult.failure("messageId 和 fileKey 不能为空");
        }
        try {
            String token = tenantAccessToken();
            HttpRequest request = HttpRequest.newBuilder(downloadUri(messageId, fileKey))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return FeishuFileDownloadResult.success("", contentType(response), response.body());
            }
            return FeishuFileDownloadResult.failure("飞书文件下载失败，HTTP状态码：" + response.statusCode());
        } catch (Exception e) {
            String message = e.getMessage();
            return FeishuFileDownloadResult.failure(StringUtils.hasText(message) ? message : e.getClass().getSimpleName());
        }
    }

    private String tenantAccessToken() throws Exception {
        FeishuRuntimeConfig config = configProvider == null ? null : configProvider.getActiveConfig();
        if (config == null || !StringUtils.hasText(config.getAppId()) || !StringUtils.hasText(config.getAppSecret())) {
            throw new IllegalStateException("active feishu app config is missing");
        }
        String body = OBJECT_MAPPER.writeValueAsString(Map.of(
                "app_id", config.getAppId(),
                "app_secret", config.getAppSecret()));
        HttpRequest request = HttpRequest.newBuilder(URI.create(OPEN_API_BASE + "/auth/v3/tenant_access_token/internal"))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Map<String, Object> payload = OBJECT_MAPPER.readValue(response.body(), new TypeReference<>() {
        });
        Object token = payload.get("tenant_access_token");
        if (response.statusCode() >= 200 && response.statusCode() < 300 && token != null && StringUtils.hasText(String.valueOf(token))) {
            return String.valueOf(token);
        }
        throw new IllegalStateException("飞书 tenant_access_token 获取失败：" + payload.getOrDefault("msg", response.statusCode()));
    }

    private URI downloadUri(String messageId, String fileKey) {
        return URI.create(OPEN_API_BASE
                + "/im/v1/messages/" + encode(messageId)
                + "/resources/" + encode(fileKey)
                + "?type=file");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String contentType(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type").orElse("");
    }
}
