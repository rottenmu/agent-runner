package com.zimo.framework.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiApiSkillRegistryTest {
    @Test
    void registeredApiSkillPostsArgumentsAndReturnsRemoteBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiSkillRegistry registry = new AiSkillRegistry(java.util.List.of(), builder);

        registry.registerApiSkill(new AiApiSkillConfig(
                "remote_quote",
                "query remote quote",
                true,
                true,
                "https://skill.example",
                "/quote",
                "POST",
                Map.of("X-App-Id", "demo-app"),
                3000));

        server.expect(requestTo("https://skill.example/quote"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-App-Id", "demo-app"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.materialCode").value("M-001"))
                .andRespond(withSuccess("{\"price\":12.5}", MediaType.APPLICATION_JSON));

        AiSkillResult result = registry.call("remote_quote", Map.of("materialCode", "M-001"));

        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("{\"price\":12.5}");
        assertThat(registry.list()).extracting(AiSkillDescriptor::name).contains("remote_quote");
        server.verify();
    }

    @Test
    void apiSkillReturnsFailureWhenRemoteCallFailsWithoutLeakingSensitiveHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiSkillRegistry registry = new AiSkillRegistry(java.util.List.of(), builder);

        registry.registerApiSkill(new AiApiSkillConfig(
                "remote_error",
                "remote error",
                false,
                true,
                "https://skill.example",
                "/fail",
                "POST",
                Map.of(HttpHeaders.AUTHORIZATION, "Bearer secret-token"),
                3000));

        server.expect(requestTo("https://skill.example/fail"))
                .andRespond(withServerError()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("server echoed secret-token"));

        AiSkillResult result = registry.call("remote_error", Map.of("text", "hello"));

        assertThat(result.success()).isFalse();
        assertThat(result.content()).contains("API skill call failed");
        assertThat(result.content()).doesNotContain("secret-token");
        server.verify();
    }
}
