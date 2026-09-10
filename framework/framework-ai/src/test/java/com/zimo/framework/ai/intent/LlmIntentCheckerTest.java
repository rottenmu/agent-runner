package com.zimo.framework.ai.intent;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.zimo.framework.ai.intent.demo.OrderQuerySkill;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** LLM 意图校验器测试：本地 mock OpenAI 兼容端点。 */
class LlmIntentCheckerTest {

    private HttpServer server;
    private LlmIntentChecker.LlmConfig config;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        int port = server.getAddress().getPort();
        config = new LlmIntentChecker.LlmConfig("http://localhost:" + port, "test-key", "qwen-plus", 5000);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void parsesMatchFromLlmResponse() throws Exception {
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = ("{\"choices\":[{\"message\":{\"content\":\"{\\\"isMatch\\\":true,\\\"confidence\\\":0.93,"
                    + "\\\"needClarify\\\":false,\\\"intentName\\\":\\\"ORDER_QUERY\\\",\\\"reason\\\":\\\"\\u7528\\u6237\\u660e\\u786e\\u67e5\\u8be2\\u8ba2\\u5355\\\"}\"}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        LlmIntentChecker checker = new LlmIntentChecker(config);
        IntentCheckResult result = checker.check("查一下我的订单状态", ConversationContext.of("u1", "u1", "s1"),
                new OrderQuerySkill());
        assertThat(result.isMatch()).isTrue();
        assertThat(result.confidence()).isEqualTo(0.93);
        assertThat(result.intentName()).isEqualTo("ORDER_QUERY");
        assertThat(result.needClarify()).isFalse();
    }

    @Test
    void parsesClarifyFromLlmResponse() throws Exception {
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = ("{\"choices\":[{\"message\":{\"content\":\"{\\\"isMatch\\\":true,\\\"confidence\\\":0.55,"
                    + "\\\"needClarify\\\":true,\\\"intentName\\\":\\\"ORDER_QUERY\\\",\\\"reason\\\":\\\"\\u8ba2\\u5355\\u7f16\\u53f7\\u672a\\u6307\\u660e\\\"}\"}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        LlmIntentChecker checker = new LlmIntentChecker(config);
        IntentCheckResult result = checker.check("帮我看看那个单子", ConversationContext.of("u1", "u1", "s1"),
                new OrderQuerySkill());
        assertThat(result.isMatch()).isTrue();
        assertThat(result.needClarify()).isTrue(); // 置信 0.55 < 阈值 0.8
    }

    @Test
    void unknownWhenLlmUnconfigured() {
        LlmIntentChecker checker = new LlmIntentChecker(LlmIntentChecker.LlmConfig.disabled());
        IntentCheckResult result = checker.check("查订单", ConversationContext.of("u1", "u1", "s1"),
                new OrderQuerySkill());
        assertThat(result.isMatch()).isFalse();
        assertThat(result.reason()).contains("LLM 未配置");
    }

    @Test
    void systemPromptContainsJsonSchema() {
        assertThat(IntentCheckPrompts.SYSTEM_PROMPT).contains("\"required\"")
                .contains("isMatch")
                .contains("needClarify")
                .contains("confidence");
    }
}
