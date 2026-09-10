package com.zimo.framework.ai.mcp;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zimo.framework.ai.autoconfig.AiAgentAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(McpController.class)
@ImportAutoConfiguration({RestClientAutoConfiguration.class, AiAgentAutoConfiguration.class})
@Import(McpController.class)
class McpControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Test
    void listsToolsWithJsonRpcEnvelope() throws Exception {
        mockMvc.perform(post("/api/ai/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":"1","method":"tools/list"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.result.tools[*].name").value(hasItem("echo")));
    }

    @Test
    void callsToolWithJsonRpcEnvelope() throws Exception {
        mockMvc.perform(post("/api/ai/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":"2","method":"tools/call","params":{"name":"echo","arguments":{"text":"hello"}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("2"))
                .andExpect(jsonPath("$.result.success").value(true))
                .andExpect(jsonPath("$.result.content").value("{text=hello}"));
    }
}
