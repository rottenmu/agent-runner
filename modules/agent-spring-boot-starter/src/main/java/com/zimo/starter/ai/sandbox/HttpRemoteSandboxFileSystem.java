package com.zimo.starter.ai.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP 远程沙箱文件系统：把文件操作转发到远程沙箱服务（dsh A7 远程共享执行世界）。
 *
 * <p>协议：{@code POST {baseUrl}/api/sandbox/file}，请求体为 {@link SandboxFileOp} JSON，
 * 响应体为 {@link SandboxFileResult} JSON。工作区根与白名单由远程服务端维护，
 * 客户端仅声明相对路径。远程不可达/协议错误统一以 failed 返回，不抛异常。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-25
 */
public class HttpRemoteSandboxFileSystem implements SandboxFileSystem {

    private static final Logger log = LoggerFactory.getLogger(HttpRemoteSandboxFileSystem.class);

    /** 文件操作端点（相对于 baseUrl）。 */
    public static final String FILE_PATH = "/api/sandbox/file";

    private final String baseUrl;
    private final String apiToken;
    private final int timeoutSeconds;
    private final String workdir;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpRemoteSandboxFileSystem(String baseUrl, String apiToken, int timeoutSeconds) {
        this(baseUrl, apiToken, timeoutSeconds, "/");
    }

    public HttpRemoteSandboxFileSystem(String baseUrl, String apiToken, int timeoutSeconds, String workdir) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("remote sandbox baseUrl 不能为空");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiToken = apiToken;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        this.workdir = workdir == null || workdir.isBlank() ? "/" : workdir;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String workdir() {
        return workdir;
    }

    @Override
    public Set<String> allowedExtensions() {
        return Set.of();
    }

    @Override
    public SandboxFileResult execute(SandboxFileOp op) {
        if (op == null || op.op() == null) {
            return SandboxFileResult.failed("文件操作类型不能为空");
        }
        try {
            String bodyJson = objectMapper.writeValueAsString(op);
            log.debug("沙箱(远程) 文件操作转发: {} {}", baseUrl, op.op());
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + FILE_PATH))
                    .timeout(Duration.ofSeconds(timeoutSeconds + 5))
                    .header("Content-Type", "application/json");
            if (apiToken != null && !apiToken.isBlank()) {
                builder.header("Authorization", "Bearer " + apiToken);
            }
            HttpResponse<String> response = httpClient.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(bodyJson)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("沙箱(远程) 文件操作非 200: status={}, body={}",
                        response.statusCode(), truncate(response.body(), 300));
                return SandboxFileResult.failed("远程沙箱返回 HTTP " + response.statusCode());
            }
            SandboxFileResult result = objectMapper.readValue(response.body(), SandboxFileResult.class);
            return result == null ? SandboxFileResult.failed("远程沙箱返回空结果") : result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SandboxFileResult.failed("远程沙箱文件操作被中断");
        } catch (Exception e) {
            log.warn("沙箱(远程) 文件操作调用失败: url={}, err={}", baseUrl, safeMessage(e));
            return SandboxFileResult.failed("远程沙箱不可达: " + safeMessage(e));
        }
    }

    private String truncate(String text, int max) {
        return text != null && text.length() > max ? text.substring(0, max) + "..." : text;
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return message == null || message.isBlank()
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}