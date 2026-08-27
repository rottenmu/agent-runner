package com.zimo.starter.ai.a2a;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zimo.starter.ai.autoconfig.AiAgentAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

import com.zimo.module.agentmemory.memory.AiMemoryService;
import org.springframework.boot.test.mock.mockito.MockBean;

@WebMvcTest(A2aController.class)
@ImportAutoConfiguration({RestClientAutoConfiguration.class, AiAgentAutoConfiguration.class})
@Import(A2aController.class)
@TestPropertySource(properties = "ai.agent.api-key=")
class A2aControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiMemoryService aiMemoryService;

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Test
    void returnsAgentCard() throws Exception {
        mockMvc.perform(get("/api/ai/a2a/agent-card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ai-agent"))
                .andExpect(jsonPath("$.capabilities").value(hasItem("mcp-tools")));
    }

    @Test
    void returnsNotConfiguredMessageWhenApiKeyIsMissing() throws Exception {
        mockMvc.perform(post("/api/ai/a2a/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"hello"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agent").value("ai-agent"))
                .andExpect(jsonPath("$.content").value(containsString("AI 服务未配置")));
    }
}
