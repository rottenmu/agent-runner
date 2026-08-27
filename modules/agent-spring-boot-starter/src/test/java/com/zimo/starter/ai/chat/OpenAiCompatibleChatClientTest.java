package com.zimo.starter.ai.chat;

import com.zimo.module.agentmemory.chat.AiChatMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.zimo.starter.ai.AiAgentProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleChatClientTest {
    @Test
    void postsOpenAiCompatibleChatRequestAndExtractsContent() {
        AiAgentProperties properties = properties("dummy-api-key");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(properties, builder);

        server.expect(requestTo("https://dashscope.example/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer dummy-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("qwen-plus"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value(properties.getSystemPrompt()))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("hello"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"model reply"}}]}
                        """, MediaType.APPLICATION_JSON));

        AiChatResponse response = client.chat(new AiChatRequest(
                "ai-agent", "hello", "s1", "qwen-plus", 0.7, 2000));

        assertThat(response.success()).isTrue();
        assertThat(response.content()).isEqualTo("model reply");
        server.verify();
    }

    @Test
    void includesConversationHistoryBetweenSystemAndCurrentUserMessage() {
        AiAgentProperties properties = properties("dummy-api-key");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(properties, builder);

        server.expect(requestTo("https://dashscope.example/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("first question"))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[2].content").value("first answer"))
                .andExpect(jsonPath("$.messages[3].role").value("user"))
                .andExpect(jsonPath("$.messages[3].content").value("follow up"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"follow answer"}}]}
                        """, MediaType.APPLICATION_JSON));

        AiChatResponse response = client.chat(new AiChatRequest(
                "ai-agent",
                "follow up",
                "s1",
                "qwen-plus",
                0.7,
                2000,
                properties.getSystemPrompt(),
                List.of(
                        new AiChatMessage("user", "first question"),
                        new AiChatMessage("assistant", "first answer"))));

        assertThat(response.success()).isTrue();
        assertThat(response.content()).isEqualTo("follow answer");
        server.verify();
    }

    @Test
    void returnsSanitizedFailureWhenHttpCallFails() {
        AiAgentProperties properties = properties("dummy-api-key");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(properties, builder);

        server.expect(requestTo("https://dashscope.example/v1/chat/completions"))
                .andRespond(withServerError());

        AiChatResponse response = client.chat(new AiChatRequest(
                "ai-agent", "hello", "s1", "qwen-plus", 0.7, 2000));

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).contains("大模型调用失败");
        assertThat(response.errorMessage()).doesNotContain("dummy-api-key");
        server.verify();
    }

    @Test
    void redactsApiKeyWhenProviderErrorMentionsIt() {
        AiAgentProperties properties = properties("dummy-api-key");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleChatClient client = new OpenAiCompatibleChatClient(properties, builder);

        server.expect(requestTo("https://dashscope.example/v1/chat/completions"))
                .andRespond(withServerError()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("provider echoed dummy-api-key"));

        AiChatResponse response = client.chat(new AiChatRequest(
                "ai-agent", "hello", "s1", "qwen-plus", 0.7, 2000));

        assertThat(response.success()).isFalse();
        assertThat(response.errorMessage()).doesNotContain("dummy-api-key");
        assertThat(response.errorMessage()).contains("[redacted]");
        server.verify();
    }

    private AiAgentProperties properties(String apiKey) {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setBaseUrl("https://dashscope.example/v1");
        properties.setApiKey(apiKey);
        properties.setModelName("qwen-plus");
        return properties;
    }
}
