package com.zimo.framework.ai.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 端到端冒烟：本地直通 / HTTP 远程转发 / 远程携带 stdin+env 的完整协议验证。
 */
class SandboxSmokeTest {

    private static HttpServer server;
    private static String baseUrl;

    /** 收到请求的原始 body 校验（证明协议忠实转发）。 */
    private static volatile String receivedBody;

    @BeforeAll
    static void startRemote() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/sandbox/execute", exchange -> {
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            // 真实执行：在远程"沙箱"内跑 python
            var body = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(receivedBody);
            var argvRaw = body.get("argv");
            List<String> argv = new java.util.ArrayList<>();
            for (var item : argvRaw) {
                argv.add(item.asText());
            }
            String stdin = body.hasNonNull("stdin") ? body.get("stdin").asText() : null;
            int timeout = body.hasNonNull("timeoutSeconds") ? body.get("timeoutSeconds").asInt() : 10;
            // 忠实透传 env（验证协议完整转发）——用 final 容器规避 lambda effectively-final 约束
            final Map<String, String> envMap = new java.util.HashMap<>();
            if (body.hasNonNull("env") && body.get("env").isObject()) {
                body.get("env").fields().forEachRemaining(e -> envMap.put(e.getKey(), e.getValue().asText()));
            }
            com.zimo.framework.ai.sandbox.SandboxResult inner =
                    new LocalSandboxBackend(30).execute(new SandboxCommand(argv, envMap.isEmpty() ? null : envMap, stdin, timeout, "."));;
            String json = """
                    {"exitCode":%d,"stdout":%s,"stderr":%s,"timedOut":%s,"error":%s}
                    """.formatted(
                    inner.exitCode(),
                    json(inner.stdout()),
                    json(inner.stderr()),
                    inner.timedOut(),
                    inner.error() == null ? "null" : json(inner.error()));
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String json(String s) {
        StringBuilder sb = new StringBuilder("\"");
        if (s != null) {
            for (char c : s.toCharArray()) {
                if (c == '"') {
                    sb.append("\\\"");
                } else if (c == '\\') {
                    sb.append("\\\\");
                } else if (c == '\n') {
                    sb.append("\\n");
                } else if (c < 32) {
                    sb.append(String.format("\\u%04x", (int) c));
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.append("\"").toString();
    }

    @AfterAll
    static void stopRemote() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void localPassthroughRunsPython() {
        LocalSandboxBackend local = new LocalSandboxBackend(10);
        SandboxResult result = local.execute(new SandboxCommand(
                List.of("python3", "-c", "print(6 * 7)")));
        assertThat(result.succeeded()).isTrue();
        assertThat(result.stdout()).contains("42");
    }

    @Test
    void remoteBackendRunsSameCodeWithStdinAndEnv() {
        HttpRemoteSandboxBackend remote = new HttpRemoteSandboxBackend(baseUrl, "", 15);
        SandboxResult result = remote.execute(new SandboxCommand(
                List.of("python3", "-c",
                        "import sys, os; print(sys.stdin.read().strip() + ':' + os.environ.get('SB_ENV', '?') + ':' + str(2**10))"),
                Map.of("SB_ENV", "remote-env"),
                "from-stdin", 15, "."));
        assertThat(result.error()).isNull();
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("from-stdin:remote-env:1024");
        assertThat(receivedBody).contains("remote-env").contains("from-stdin");
    }

    @Test
    void remoteBackendCapturesFailure() {
        HttpRemoteSandboxBackend remote = new HttpRemoteSandboxBackend(baseUrl, "", 10);
        SandboxResult result = remote.execute(new SandboxCommand(
                List.of("python3", "-c", "import sys; sys.exit(7)")));
        assertThat(result.error()).isNull();
        assertThat(result.exitCode()).isEqualTo(7);
    }
}