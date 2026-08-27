package com.zimo.starter.ai.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * HTTP 远程沙箱后端单测：协议转发 / 鉴权 / 超时 / 不可达。
 */
class HttpRemoteSandboxBackendTest {

    private static HttpServer server;
    private static String baseUrl;
    private static final String TOKEN = "test-token";

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/sandbox/execute", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (!("Bearer " + TOKEN).equals(auth)) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            String json = """
                    {"exitCode":0,"stdout":"remote-ok\\n","stderr":"","timedOut":false,"error":null}
                    """;
            exchange.sendResponseHeaders(200, json.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void forwardsCommandAndParsesResult() {
        HttpRemoteSandboxBackend backend = new HttpRemoteSandboxBackend(baseUrl, TOKEN, 10);
        SandboxResult result = backend.execute(new SandboxCommand(List.of("python3", "-c", "print(1)")));
        assertThat(result.error()).isNull();
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("remote-ok");
    }

    @Test
    void rejectsWithoutToken() {
        HttpRemoteSandboxBackend backend = new HttpRemoteSandboxBackend(baseUrl, "", 10);
        SandboxResult result = backend.execute(new SandboxCommand(List.of("python3", "-c", "print(1)")));
        assertThat(result.error()).contains("401");
    }

    @Test
    void returnsFailureWhenUnreachable() {
        HttpRemoteSandboxBackend backend = new HttpRemoteSandboxBackend(
                "http://127.0.0.1:1", TOKEN, 3);
        SandboxResult result = backend.execute(new SandboxCommand(List.of("python3")));
        assertThat(result.error()).isNotBlank();
        assertThat(result.succeeded()).isFalse();
    }
}