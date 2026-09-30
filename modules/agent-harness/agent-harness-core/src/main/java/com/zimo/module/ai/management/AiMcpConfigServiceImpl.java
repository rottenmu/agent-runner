package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.ai.mapper.AiMcpConfigMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * MCP 配置服务实现：CRUD 之上提供轻量连通性测试。
 *
 * <p>后端没有 MCP 客户端 SDK，测试按传输类型做最贴近真实握手的探测：</p>
 * <ul>
 *   <li>http/sse —— 对 endpoint 发 JSON-RPC <code>initialize</code> 请求（带
 *       MCP-Protocol-Version 头，透传 transportConfig.headers），按 HTTP 状态码 +
 *       JSON-RPC 回执判断连通。401/403 等鉴权失败会精确反馈进 FAIL 文案。</li>
 *   <li>stdio —— 由 endpoint 拆出「命令 参数...」启动子进程，写一条
 *       initialize 并读回执，8 秒超时；命令不存在等启动失败原样反馈。</li>
 * </ul>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@Service
public class AiMcpConfigServiceImpl extends ServiceImpl<AiMcpConfigMapper, AiMcpConfig>
        implements AiMcpConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiMcpConfigServiceImpl.class);

    /** MCP 协议版本：仅做握手协商，探测不判断版本高低，服务端拒识别也算链路通。 */
    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";
    private static final String UTF_8 = StandardCharsets.UTF_8.name();

    private final ObjectMapper objectMapper;

    public AiMcpConfigServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public String test(Long id) {
        AiMcpConfig config = getById(id);
        if (config == null) {
            return "FAIL: MCP 配置不存在: id=" + id;
        }
        String type = config.getMcpType() == null ? "" : config.getMcpType().trim().toLowerCase();
        try {
            // stdio 的 endpoint 语义是「命令 参数...」；http/sse 则是 URL
            return switch (type) {
                case "http", "sse" -> testHttp(config, type);
                case "stdio" -> testStdio(config);
                default -> "FAIL: 未知 MCP 类型: " + config.getMcpType();
            };
        } catch (Exception e) {
            log.warn("MCP 测试异常 id={} type={} endpoint={}", id, type, config.getEndpoint(), e);
            return "FAIL: " + safeMessage(e);
        }
    }

    /* ---------------- http / sse ---------------- */

    private String testHttp(AiMcpConfig config, String type) throws Exception {
        String endpoint = config.getEndpoint();
        if (!StringUtils.hasText(endpoint)) {
            return "FAIL: " + type + " 类型缺少端点地址";
        }
        Map<String, Object> transport = parseTransport(config.getTransportConfig());
        HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json, text/event-stream");
            conn.setRequestProperty("MCP-Protocol-Version", MCP_PROTOCOL_VERSION);
            JsonRpcRequest req = new JsonRpcRequest("initialize", Map.of(
                    "protocolVersion", MCP_PROTOCOL_VERSION,
                    "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "agent-harness", "version", "1.0")));
            // 透传 transportConfig.headers（用户配置的鉴权头），失败时诊断文案里给到提示
            applyHeaders(conn, transport);
            byte[] body = objectMapper.writeValueAsBytes(req);
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
            int code = conn.getResponseCode();
            String reply = readQuietly(code >= 400 ? conn.getErrorStream() : conn.getInputStream());

            // 鉴权失败也要返回内容（WWW-Authenticate 等），IO 异常视为不可达
            if (code == 401 || code == 403) {
                String hint = requireAuthHint(transport);
                return "FAIL: HTTP " + code + (reply.contains("Unauthorized") ? " Unauthorized" : "")
                        + " —— 服务要求鉴权" + hint;
            }
            if (code < 200 || code >= 300) {
                return "FAIL: HTTP " + code + " —— 端点已应答但不是成功状态"
                        + (StringUtils.hasText(reply) ? "，响应：" + clip(reply, 120) : "");
            }
            String okDetail = parseInitializeReply(reply, code);
            return "OK: " + type + " 握手成功（HTTP " + code + "）" + okDetail;
        } finally {
            conn.disconnect();
        }
    }

    /** transportConfig.headers 里是否有 bearer 之类的东西（诊断时提示检查）。 */
    private String requireAuthHint(Map<String, Object> transport) {
        Object headers = transport.get("headers");
        if (headers instanceof Map<?, ?> m && !m.isEmpty()) {
            return "（transportConfig.headers 已配置，请核对值）";
        }
        return "（请在该配置的「传输配置 JSON」里补充 headers，如 {\"headers\":{\"Authorization\":\"Bearer …\"}}）";
    }

    /* ---------------- stdio ---------------- */

    private String testStdio(AiMcpConfig config) {
        String endpoint = config.getEndpoint();
        if (!StringUtils.hasText(endpoint)) {
            return "FAIL: stdio 类型缺少启动命令（endpoint 填「命令 参数...」）";
        }
        String[] cmd = endpoint.trim().split("\\s+");
        if (cmd.length == 0 || !StringUtils.hasText(cmd[0])) {
            return "FAIL: stdio 启动命令为空";
        }
        List<String> command = new ArrayList<>();
        command.add(cmd[0]);
        Arrays.stream(cmd).skip(1).forEach(command::add);
        // 只 heredoc 化 JSON-RPC，命令本身不再经 shell —— 防注入也防引号噩梦
        String payload = writeInitialize();
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            // transportConfig.env（mcp.json 标准字段）透传为子进程环境变量，
            // 与 http 分支透传 headers 对齐 —— stdio server 常靠 env 注入 API_KEY
            Map<String, Object> transport = parseTransport(config.getTransportConfig());
            Object envObj = transport.get("env");
            if (envObj instanceof Map<?, ?> em && !em.isEmpty()) {
                em.forEach((k, v) -> {
                    if (k != null && v != null) {
                        pb.environment().put(String.valueOf(k), String.valueOf(v));
                    }
                });
            }
            pb.redirectErrorStream(true);
            process = pb.start();
            try (OutputStream os = process.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            // 读取放进守护线程：readLine() 是阻塞读，主线程用 CountDownLatch 守 8s 闸门，
            // 否则子进程不退出也不输出时，测试会被卡到进程退出为止
            StringBuilder window = new StringBuilder();
            java.util.concurrent.atomic.AtomicBoolean sawEventOrResult = new java.util.concurrent.atomic.AtomicBoolean(false);
            java.util.concurrent.CountDownLatch readDone = new java.util.concurrent.CountDownLatch(1);
            Thread readerThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (window.length() < 2000) {
                            window.append(line).append('\n');
                        }
                        if (line.contains("\"method\"") || line.contains("\"result\"")) {
                            sawEventOrResult.set(true);
                            break;
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    readDone.countDown();
                }
            }, "mcp-stdio-reader");
            readerThread.setDaemon(true);
            readerThread.start();

            boolean responded = readDone.await(8, TimeUnit.SECONDS);
            if (sawEventOrResult.get()) {
                return "OK: stdio 进程已响应（" + cmd[0] + "），首个消息："
                        + clip(window.toString(), 140).replace('\n', ' ');
            }
            if (!process.isAlive()) {
                int exit = process.exitValue();
                return "FAIL: stdio 进程提前退出（exit=" + exit + "）"
                        + (StringUtils.hasText(window) ? "，输出：" + clip(window.toString(), 120) : "");
            }
            return "FAIL: stdio 进程 8s 内未返回任何 JSON-RPC 消息，疑似握手挂起（输出："
                    + clip(window.toString(), 80) + "）";
        } catch (Exception e) {
            return "FAIL: stdio 启动/通信失败 —— " + safeMessage(e);
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    /* ---------------- 工具 ---------------- */

    private Map<String, Object> parseTransport(String transportConfig) {
        if (!StringUtils.hasText(transportConfig)) {
            return Map.of();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(transportConfig, Map.class);
            return map == null ? Map.of() : map;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void applyHeaders(HttpURLConnection conn, Map<String, Object> transport) {
        Object headers = transport.get("headers");
        if (!(headers instanceof Map<?, ?> h) || h.isEmpty()) {
            return;
        }
        h.forEach((k, v) -> {
            if (k != null && v != null) {
                try {
                    conn.setRequestProperty(String.valueOf(k), String.valueOf(v));
                } catch (IllegalStateException ignored) {
                    // 连接已开启时不允许改头，忽略
                }
            }
        });
    }

    private String writeInitialize() {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
                + "\"protocolVersion\":\"" + MCP_PROTOCOL_VERSION + "\","
                + "\"capabilities\":{},\"clientInfo\":{\"name\":\"agent-harness\",\"version\":\"1.0\"}}}\n";
    }

    /**
     * 解析 initialize 回执：JSON-RPC result 里应有 protocolVersion 与服务端工具列表
     * （tools/list 未必在 initialize 里返回，有 capability 就算握手完整）。
     */
    private String parseInitializeReply(String reply, int code) {
        if (!StringUtils.hasText(reply)) {
            return "";
        }
        try {
            Map<String, Object> json = objectMapper.readValue(reply, Map.class);
            Object err = json.get("error");
            if (err instanceof Map<?, ?> em && !em.isEmpty()) {
                return " —— 服务端返回 JSON-RPC error: " + clip(String.valueOf(em.get("message")), 80);
            }
            Object result = json.get("result");
            if (result instanceof Map<?, ?> rm) {
                String version = String.valueOf(rm.get("protocolVersion") == null ? "" : rm.get("protocolVersion"));
                if (StringUtils.hasText(version)) {
                    return "（服务端协议 " + version + "）";
                }
            }
            return "（HTTP " + code + "，响应非标准 JSON-RPC —— 链路通但回执未识别）";
        } catch (Exception e) {
            // sse 首包可能是 event 头，透传前 80 字符帮诊断
            return "（HTTP " + code + "，响应非 JSON：" + clip(reply, 80) + "）";
        }
    }

    private String readQuietly(java.io.InputStream in) {
        if (in == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
                if (sb.length() > 8192) {
                    break;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private String safeMessage(Exception e) {
        String msg = e.getMessage();
        if (StringUtils.hasText(msg)) {
            return msg;
        }
        if (e instanceof java.net.UnknownHostException uhe) {
            return "无法解析主机: " + uhe.getMessage();
        }
        return e.getClass().getSimpleName();
    }

    /** 极简 JSON-RPC 请求体（预设 id=1）。 */
    static class JsonRpcRequest {
        public final String jsonrpc = "2.0";
        public final int id = 1;
        public final String method;
        public final Map<String, Object> params;

        JsonRpcRequest(String method, Map<String, Object> params) {
            this.method = method;
            this.params = params;
        }
    }
}