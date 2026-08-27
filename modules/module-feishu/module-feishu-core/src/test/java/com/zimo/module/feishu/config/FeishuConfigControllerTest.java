package com.zimo.module.feishu.config;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeishuConfigControllerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RecordingConfigService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = new RecordingConfigService();
        mockMvc = MockMvcBuilders.standaloneSetup(new FeishuConfigController(service)).build();
    }

    @Test
    void pagesConfigsWithQueryConditions() throws Exception {
        mockMvc.perform(get("/api/biz/feishu/config/page")
                        .param("current", "2")
                        .param("size", "20")
                        .param("configName", "prod")
                        .param("appId", "cli_prod")
                        .param("enabled", "1")
                        .param("bound", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].appSecret").value("******"));

        assertThat(service.lastBound).isTrue();
    }

    @Test
    void createsUpdatesDeletesAndEnablesConfig() throws Exception {
        FeishuConfigRequest request = new FeishuConfigRequest();
        request.setConfigName("prod");
        request.setAppId("cli_prod");
        request.setAppSecret("secret");
        request.setVerificationToken("token");
        request.setEncryptKey("encrypt");
        request.setEnabled(1);

        mockMvc.perform(post("/api/biz/feishu/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configName").value("prod"));

        mockMvc.perform(put("/api/biz/feishu/config/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10));

        mockMvc.perform(put("/api/biz/feishu/config/10/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(1));

        mockMvc.perform(delete("/api/biz/feishu/config/10"))
                .andExpect(status().isOk());
    }

    @Test
    void bindsAgentToFeishuConfig() throws Exception {
        mockMvc.perform(put("/api/biz/feishu/config/10/agent-binding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agentId":"a-new"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.agentId").value("a-new"));
    }

    @Test
    void unbindsAgentFromFeishuConfig() throws Exception {
        mockMvc.perform(put("/api/biz/feishu/config/10/agent-unbinding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.agentId").doesNotExist());

        assertThat(service.lastBoundAgentId).isNull();
        assertThat(service.bindAgentCalls).isEqualTo(1);
    }

    private static class RecordingConfigService implements FeishuConfigService {
        private final List<Long> deletedIds = new ArrayList<>();
        private Boolean lastBound;
        private String lastBoundAgentId;
        private int bindAgentCalls;

        @Override
        public Page<FeishuConfigResponse> page(
                long current,
                long size,
                String configName,
                String appId,
                Integer enabled,
                Boolean bound) {
            lastBound = bound;
            FeishuConfigResponse response = response(null);
            Page<FeishuConfigResponse> page = new Page<>(current, size, 1);
            page.setRecords(List.of(response));
            return page;
        }

        @Override
        public FeishuConfigResponse get(Long id) {
            return response(id);
        }

        @Override
        public FeishuConfigResponse create(FeishuConfigRequest request) {
            FeishuConfigResponse response = response(1L);
            response.setConfigName(request.getConfigName());
            response.setAppId(request.getAppId());
            return response;
        }

        @Override
        public FeishuConfigResponse update(Long id, FeishuConfigRequest request) {
            return response(id);
        }

        @Override
        public void delete(Long id) {
            deletedIds.add(id);
        }

        @Override
        public FeishuConfigResponse enable(Long id) {
            FeishuConfigResponse response = response(id);
            response.setEnabled(1);
            return response;
        }

        @Override
        public FeishuConfigResponse bindAgent(Long id, String agentId) {
            bindAgentCalls++;
            lastBoundAgentId = agentId;
            FeishuConfigResponse response = response(id);
            response.setAgentId(agentId);
            return response;
        }

        @Override
        public FeishuConfigResponse getActiveConfigSummary() {
            return response(10L);
        }

        @Override
        public FeishuConfigResponse disableActive() {
            FeishuConfigResponse response = response(10L);
            response.setEnabled(0);
            return response;
        }

        @Override
        public FeishuRuntimeConfig getActiveConfig() {
            return null;
        }

        @Override
        public FeishuConfigEntity getRaw(Long id) {
            FeishuConfigEntity entity = new FeishuConfigEntity();
            entity.setId(id);
            entity.setAppId("cli_prod");
            entity.setAppSecret("secret");
            entity.setEnabled(1);
            return entity;
        }

        @Override
        public void updateCredentialStatus(Long id, String status, LocalDateTime validateTime) {
        }

        private static FeishuConfigResponse response(Long id) {
            FeishuConfigResponse response = new FeishuConfigResponse();
            response.setId(id);
            response.setConfigName("prod");
            response.setAppId("cli_prod");
            response.setAppSecret("******");
            response.setVerificationToken("******");
            response.setEncryptKey("******");
            response.setEnabled(1);
            return response;
        }
    }
}
