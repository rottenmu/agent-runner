package com.zimo.starter.ai.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * HTTP 远程沙箱文件系统单测：协议转发 / 鉴权 / 不可达。
 */
class HttpRemoteSandboxFileSystemTest {

    private static HttpServer server;
    private static String baseUrl;
    private static final String TOKEN = "file-token";

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/sandbox/file", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (!("Bearer " + TOKEN).equals(auth)) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            String requestJson = new String(body, StandardCharsets.UTF_8);
            String op = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(requestJson).path("op").asText("");
            String json;
            if ("read".equals(op)) {
                json = "{\"success\":true,\"content\":\"remote-content\",\"entries\":null,\"exists\":true,\"error\":null}";
            } else if ("list".equals(op)) {
                json = "{\"success\":true,\"content\":null,\"entries\":[\"a.txt\",\"sub/\"],\"exists\":true,\"error\":null}";
            } else {
                json = "{\"success\":true,\"content\":null,\"entries\":null,\"exists\":true,\"error\":null}";
            }
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
    void forwardsReadAndParsesResult() {
        HttpRemoteSandboxFileSystem fs = new HttpRemoteSandboxFileSystem(baseUrl, TOKEN, 10);
        SandboxFileResult result = fs.execute(new SandboxFileOp("read", "notes/a.txt"));
        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("remote-content");
    }

    @Test
    void forwardsListAndParsesEntries() {
        HttpRemoteSandboxFileSystem fs = new HttpRemoteSandboxFileSystem(baseUrl, TOKEN, 10);
        SandboxFileResult result = fs.execute(new SandboxFileOp("list", "notes"));
        assertThat(result.success()).isTrue();
        assertThat(result.entries()).contains("a.txt", "sub/");
    }

    @Test
    void unauthorizedReturnsFailure() {
        HttpRemoteSandboxFileSystem fs = new HttpRemoteSandboxFileSystem(baseUrl, "wrong-token", 10);
        SandboxFileResult result = fs.execute(new SandboxFileOp("read", "a.txt"));
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("HTTP 401");
    }

    @Test
    void unreachableReturnsFailure() {
        HttpRemoteSandboxFileSystem fs = new HttpRemoteSandboxFileSystem(
                "http://127.0.0.1:1", TOKEN, 5);
        SandboxFileResult result = fs.execute(new SandboxFileOp("read", "a.txt"));
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("不可达");
    }

    @Test
    void rejectsBlankBaseUrl() {
        assertThatThrownBy(() -> new HttpRemoteSandboxFileSystem("", TOKEN, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}