package com.zimo.starter.ai.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP 远程沙箱后端：将命令转发到远程沙箱服务执行（对应 dsh E2B 远程执行）。
 *
 * <p>协议：{@code POST {baseUrl}/api/sandbox/execute}，请求体为
 * {@link SandboxCommand} JSON，响应体为 {@link SandboxResult} JSON。
 * 远程不可达/协议错误统一以 {@link SandboxResult.failed} 返回，不抛异常。</p>
 *
 * <p>远程沙箱服务端参考 {@code scripts/sandbox-server.py}（Python 标准库实现，
 * 仅执行白名单命令并返回统一 JSON）。</p>
 */
public class HttpRemoteSandboxBackend implements SandboxBackend {

    private static final Logger log = LoggerFactory.getLogger(HttpRemoteSandboxBackend.class);

    /** 执行端点（相对于 baseUrl）。 */
    public static final String EXECUTE_PATH = "/api/sandbox/execute";

    private final String baseUrl;
    private final String apiToken;
    private final int timeoutSeconds;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpRemoteSandboxBackend(String baseUrl, String apiToken, int timeoutSeconds) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("remote sandbox baseUrl 不能为空");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiToken = apiToken;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String name() {
        return "remote";
    }

    @Override
    public List<String> wrapCommand(List<String> argv) {
        return argv;
    }

    @Override
    public boolean isAllowed(String executable) {
        return executable != null && !executable.isBlank();
    }

    @Override
    public SandboxResult execute(SandboxCommand command) {
        List<String> argv = command.argv();
        if (argv == null || argv.isEmpty()) {
            return SandboxResult.failed("命令为空");
        }
        if (!isAllowed(argv.get(0))) {
            return SandboxResult.failed("命令被沙箱拒绝: " + argv.get(0));
        }
        try {
            String bodyJson = objectMapper.writeValueAsString(command);
            log.debug("沙箱(远程 {}) 转发: {}", baseUrl, argv);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + EXECUTE_PATH))
                    .timeout(Duration.ofSeconds(timeoutSeconds + 5))
                    .header("Content-Type", "application/json");
            if (apiToken != null && !apiToken.isBlank()) {
                builder.header("Authorization", "Bearer " + apiToken);
            }
            HttpResponse<String> response = httpClient.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(bodyJson)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("沙箱(远程) 非 200: status={}, body={}", response.statusCode(), truncate(response.body(), 300));
                return SandboxResult.failed("远程沙箱返回 HTTP " + response.statusCode());
            }
            SandboxResult result = objectMapper.readValue(response.body(), SandboxResult.class);
            return result == null ? SandboxResult.failed("远程沙箱返回空结果") : result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SandboxResult.failed("远程沙箱执行被中断");
        } catch (Exception e) {
            log.warn("沙箱(远程) 调用失败: url={}, err={}", baseUrl, safeMessage(e));
            return SandboxResult.failed("远程沙箱不可达: " + safeMessage(e));
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