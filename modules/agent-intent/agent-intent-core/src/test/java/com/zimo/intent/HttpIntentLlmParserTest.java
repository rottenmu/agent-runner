package com.zimo.intent;

import com.zimo.intent.model.IntentLlmResult;
import com.zimo.intent.model.IntentParseResult;
import com.zimo.intent.parser.HttpIntentLlmParser;
import com.zimo.intent.service.IntentRecognitionService;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** HTTP LLM 解析器测试：本地 mock OpenAI 兼容端点。 */
class HttpIntentLlmParserTest {

    private HttpServer server;
    private IntentProperties props;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        int port = server.getAddress().getPort();
        props = new IntentProperties();
        props.setLlmBaseUrl("http://localhost:" + port);
        props.setLlmTimeoutMs(5000);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void parsesValidLlmResponse() throws Exception {
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"{\\\"intentCode\\\":\\\"DATA_QUERY\\\",\\\"confidence\\\":0.9,\\\"entities\\\":{\\\"dataType\\\":\\\"\\u8ba2\\u5355\\\"},\\\"reason\\\":\\\"\\u6d4b\\u8bd5\\\"}\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        HttpIntentLlmParser parser = new HttpIntentLlmParser(new ObjectMapper(), props);
        IntentLlmResult result = parser.parse("\\u67e5\\u8be2\\u8ba2\\u5355", List.of(), null);
        assertThat(result).isNotNull();
        assertThat(result.intentCode()).isEqualTo("DATA_QUERY");
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.entities()).containsEntry("dataType", "订单");
    }

    @Test
    void fallsBackToNullOnServerError() throws Exception {
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        HttpIntentLlmParser parser = new HttpIntentLlmParser(new ObjectMapper(), props);
        assertThat(parser.parse("hello", List.of(), null)).isNull();
    }

    @Test
    void fallsBackToNullWithoutBaseUrl() {
        props.setLlmBaseUrl("");
        HttpIntentLlmParser parser = new HttpIntentLlmParser(new ObjectMapper(), props);
        assertThat(parser.parse("hello", List.of(), null)).isNull();
    }

    @Test
    void parsesFullContractResponse() throws Exception {
        String content = "{\\\"intentCode\\\":\\\"DATA_QUERY\\\",\\\"intentName\\\":\\\"业务数据查询\\\","
                + "\\\"confidence\\\":0.88,\\\"entities\\\":{\\\"dataType\\\":\\\"订单\\\"},"
                + "\\\"requiredSlotMissing\\\":[\\\"timeRange\\\"],\\\"needTool\\\":true,"
                + "\\\"toolList\\\":[\\\"report_query\\\"],\\\"needClarify\\\":false,\\\"clarifyPrompt\\\":\\\"\\\","
                + "\\\"isReject\\\":false,\\\"rejectReason\\\":\\\"\\\",\\\"routeStrategy\\\":\\\"工具调用\\\","
                + "\\\"reason\\\":\\\"完整契约\\\"}";
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = ("{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        HttpIntentLlmParser parser = new HttpIntentLlmParser(new ObjectMapper(), props);
        IntentLlmResult result = parser.parse("查询订单", List.of(), null, "[{\"intentCode\":\"DATA_QUERY\"}]");
        assertThat(result).isNotNull();
        assertThat(result.intentCode()).isEqualTo("DATA_QUERY");
        assertThat(result.intentName()).isEqualTo("业务数据查询");
        assertThat(result.confidence()).isEqualTo(0.88);
        assertThat(result.requiredSlotMissing()).containsExactly("timeRange");
        assertThat(result.needTool()).isTrue();
        assertThat(result.toolList()).containsExactly("report_query");
        assertThat(result.isReject()).isFalse();
        assertThat(result.routeStrategy()).isEqualTo("工具调用");
    }

    @Test
    void parsesRiskRejectFromLlm() throws Exception {
        String content = "{\\\"intentCode\\\":\\\"RISK_REJECT\\\",\\\"confidence\\\":0.95,\\\"isReject\\\":true,"
                + "\\\"rejectReason\\\":\\\"越权查询他人数据\\\",\\\"routeStrategy\\\":\\\"拒绝拦截\\\"}";
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = ("{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        HttpIntentLlmParser parser = new HttpIntentLlmParser(new ObjectMapper(), props);
        IntentLlmResult result = parser.parse("查一下别人的工资", List.of(), null);
        assertThat(result).isNotNull();
        assertThat(result.isReject()).isTrue();
        assertThat(result.rejectReason()).isEqualTo("越权查询他人数据");
    }

    @Test
    void generateRulesCallsLlmWithScenario() throws Exception {
        String content = "[{\\\"intentCode\\\":\\\"OUTPUT_QUERY\\\",\\\"intentName\\\":\\\"产量查询\\\","
                + "\\\"triggerKeywords\\\":[\\\"产量\\\",\\\"良率\\\"],\\\"confidenceThreshold\\\":0.8,"
                + "\\\"routeStrategy\\\":\\\"TOOL_CALL\\\"}]";
        java.util.concurrent.atomic.AtomicReference<String> capturedUser = new java.util.concurrent.atomic.AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] request = exchange.getRequestBody().readAllBytes();
            capturedUser.set(new String(request, StandardCharsets.UTF_8));
            byte[] body = ("{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        HttpIntentLlmParser parser = new HttpIntentLlmParser(new ObjectMapper(), props);
        String result = parser.generateRules("查询各车间产量和良率", "[]");
        assertThat(result).contains("OUTPUT_QUERY");
        assertThat(capturedUser.get()).contains("业务场景描述");
    }

    @Test
    void reviewRulesReturnsLlmJson() throws Exception {
        String content = "{\\\"issues\\\":[],\\\"suggestions\\\":[\\\"建议补充导出格式槽位\\\"],"
                + "\\\"optimizedRules\\\":[]}";
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = ("{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        HttpIntentLlmParser parser = new HttpIntentLlmParser(new ObjectMapper(), props);
        String result = parser.reviewRules("[{\"intentCode\":\"DATA_EXPORT\"}]");
        assertThat(result).contains("建议补充导出格式槽位");
    }

    @Test
    void generateRulesReturnsNullWithoutBaseUrl() {
        props.setLlmBaseUrl("");
        HttpIntentLlmParser parser = new HttpIntentLlmParser(new ObjectMapper(), props);
        assertThat(parser.generateRules("任意场景", "[]")).isNull();
        assertThat(parser.reviewRules("[]")).isNull();
    }

    @Test
    void rejectsUnknownIntentCodeInEngine() {
        // LLM 返回未知意图编码时引擎不采纳（走规则结果）
        IntentProperties engineProps = new IntentProperties();
        engineProps.setLlmEnabled(true);
        engineProps.setLlmBaseUrl("");
        IntentRecognitionService engine = new IntentRecognitionService(new ObjectMapper(), engineProps);
        assertThat(engine.listRules()).hasSize(8);
        IntentParseResult result = engine.parse("查询本周订单数据", Map.of());
        assertThat(result.intentCode()).isEqualTo("DATA_QUERY"); // 规则结果保留
    }
}
