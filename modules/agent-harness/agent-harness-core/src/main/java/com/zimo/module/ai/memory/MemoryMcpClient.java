package com.zimo.module.ai.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * agent-memory MCP 端点的最小 HTTP 客户端（JSON-RPC 2.0）。
 *
 * <h2>为什么只实现 tools/call</h2>
 * <p>握手（{@code initialize}）与 {@code tools/list} 是给**外部** MCP 客户端发现能力用的；
 * 本类是服务端内部固定调用一个已知工具，跳过握手既少两次往返，也避免把「服务端自调用」
 * 伪装成一次完整会话。真正需要的是与外部调用**同一条写入口**，而不是同一次握手。</p>
 *
 * <h2>三个必须处理的坑（都会表现为「静默写不进去」）</h2>
 * <ol>
 *   <li><b>工具级失败也是 HTTP 200</b>：{@code tools/call} 内部的参数/未知工具错误由
 *       {@code MemoryMcpToolkit} 以 {@code {"error": "..."}} 作为**正常结果**返回，
 *       外层 HTTP 状态码仍是 200。只看状态码会把失败当成功。</li>
 *   <li><b>结果被包了两层</b>：JSON-RPC 的 {@code result} 是 MCP 的
 *       {@code {content:[{type:"text",text:"<JSON 字符串>"}]}}，业务结果藏在
 *       {@code content[0].text} 这个**字符串**里，需要再解析一次。</li>
 *   <li><b>401 的错误体不是统一响应信封</b>：准入失败时返回的是 JSON-RPC error 对象
 *       （HTTP 401），按 {@code {code,msg,data}} 解析会拿到 null 而丢掉真实原因
 *       （「未配置令牌仅允许回环」/「令牌不匹配」）。</li>
 * </ol>
 *
 * @author WorkBuddy
 * @since 2026-09-19
 */
public class MemoryMcpClient {

    private final ChatMemoryRecorderProperties properties;
    private final String token;
    /** 端点地址的兜底推导器（读实际监听端口）；配置显式给了 url 时不使用 */
    private final Supplier<String> urlFallback;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private final AtomicLong idSeq = new AtomicLong();

    /** 推导出的地址缓存：端口在进程生命周期内不变，但**必须惰性求值** —— 构造期 Web 容器可能尚未就绪 */
    private volatile String cachedUrl;

    public MemoryMcpClient(ChatMemoryRecorderProperties properties, String token,
                           Supplier<String> urlFallback) {
        this.properties = properties;
        this.token = token == null ? "" : token.trim();
        this.urlFallback = urlFallback;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, properties.getConnectTimeoutMs())))
                .build();
    }

    /**
     * 调用一个 MCP 工具。
     *
     * @param name      工具名（本类只用于 {@code memory_write}）
     * @param arguments 工具入参
     * @return 工具的业务结果（已从 {@code content[0].text} 解析回 Map）
     * @throws IllegalStateException 网络不可达 / 非 200 / JSON-RPC error / 负载无法解析
     */
    public Map<String, Object> callTool(String name, Map<String, Object> arguments) {
        String body = buildRequestBody(name, arguments);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint()))
                .timeout(Duration.ofMillis(Math.max(1, properties.getReadTimeoutMs())))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (!token.isEmpty()) {
            // 准入校验兼容 Bearer 与裸 token 两种写法；这里用规范写法
            builder.header("Authorization", "Bearer " + token);
        }

        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MCP 调用被中断: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("MCP 端点不可达（" + endpoint() + "）: " + e.getMessage(), e);
        }

        JsonNode root = readTree(response.body());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("MCP 返回 HTTP " + response.statusCode()
                    + "：" + jsonRpcError(root, response.body()));
        }
        if (root != null && root.hasNonNull("error")) {
            throw new IllegalStateException("MCP 返回错误：" + jsonRpcError(root, response.body()));
        }
        Map<String, Object> result = unwrapToolResult(root);
        // 坑 1：工具级失败同样是 200，必须检查业务结果里的 error
        if (result.containsKey("error")) {
            throw new IllegalStateException("MCP 工具 " + name + " 执行失败：" + result.get("error"));
        }
        return result;
    }

    /** 拼 JSON-RPC 请求体（{@code params.arguments} 为 MCP 规范位置）。 */
    private String buildRequestBody(String name, Map<String, Object> arguments) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("arguments", arguments == null ? Map.of() : arguments);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", "chat-recorder-" + idSeq.incrementAndGet());
        envelope.put("method", "tools/call");
        envelope.put("params", params);
        try {
            return mapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("MCP 请求序列化失败: " + e.getMessage(), e);
        }
    }

    /** 从 MCP 的 {@code {result:{content:[{text}]}}} 里取出并二次解析业务结果。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapToolResult(JsonNode root) {
        JsonNode content = root == null ? null : root.path("result").path("content");
        if (content == null || !content.isArray() || content.isEmpty()) {
            throw new IllegalStateException("MCP 响应缺少 result.content：" + root);
        }
        String text = content.get(0).path("text").asText("");
        if (text.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(text, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("MCP 结果不是 JSON 对象: " + text, e);
        }
    }

    private JsonNode readTree(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    /** 尽量取出 JSON-RPC 的 error.message，取不到就把原文截断返回。 */
    private String jsonRpcError(JsonNode root, String raw) {
        if (root != null && root.hasNonNull("error")) {
            JsonNode error = root.get("error");
            String message = error.path("message").asText("");
            if (!message.isBlank()) {
                return message;
            }
            return error.toString();
        }
        String text = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }

    /**
     * 端点地址：配置优先，其次按实际监听端口推导（结果缓存）。
     *
     * <p>推导放在**首次调用**而非构造期：Web 容器端口在 Bean 构造时可能尚未绑定，
     * 过早取值会得到一个 0 或 -1，然后把整个进程生命周期的写入都指向无效端口。</p>
     */
    String endpoint() {
        if (properties.getUrl() != null && !properties.getUrl().isBlank()) {
            return properties.getUrl().trim();
        }
        String cached = cachedUrl;
        if (cached != null) {
            return cached;
        }
        String derived = urlFallback == null ? null : urlFallback.get();
        String resolved = derived == null || derived.isBlank()
                ? "http://127.0.0.1:9900/api/agent-memory/mcp" : derived;
        cachedUrl = resolved;
        return resolved;
    }
}
