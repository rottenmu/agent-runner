package com.zimo.module.feishu.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.feishu.agent.dto.FeishuCredentialRefreshRequest;
import com.zimo.module.feishu.agent.dto.FeishuCredentialValidateResponse;
import com.zimo.module.feishu.agent.dto.FeishuTenantCredentialResponse;
import com.zimo.module.feishu.agent.dto.FeishuTenantScanInitRequest;
import com.zimo.module.feishu.agent.dto.FeishuTenantScanInitResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeishuAgentCredentialControllerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RecordingCredentialService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = new RecordingCredentialService();
        mockMvc = MockMvcBuilders.standaloneSetup(new FeishuAgentCredentialController(service)).build();
    }

    @Test
    void exposesTenantScanAndCredentialEndpoints() throws Exception {
        FeishuTenantScanInitRequest request = new FeishuTenantScanInitRequest();
        request.setTenantName("生产租户");
        request.setAppName("智能体运行平台 Agent");

        mockMvc.perform(post("/api/biz/feishu/agent/tenant-scan/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OBJECT_MAPPER.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.scanTicket").value("scan_ticket_1"));

        mockMvc.perform(get("/api/biz/feishu/agent/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].appSecret").value("******"));

        mockMvc.perform(post("/api/biz/feishu/agent/credentials/1/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));

        mockMvc.perform(delete("/api/biz/feishu/agent/credentials/1"))
                .andExpect(status().isOk());
    }

    private static class RecordingCredentialService implements FeishuAgentCredentialService {
        private final List<Long> deletedIds = new ArrayList<>();

        @Override
        public FeishuTenantScanInitResponse initTenantScan(FeishuTenantScanInitRequest request) {
            return new FeishuTenantScanInitResponse(
                    "https://open.feishu.cn/app/create?q=scan",
                    "scan_ticket_1",
                    600,
                    List.of("im:message"),
                    List.of("im.message.receive_v1"));
        }

        @Override
        public List<FeishuTenantCredentialResponse> listCredentials() {
            return List.of(credential(1L));
        }

        @Override
        public FeishuTenantCredentialResponse getCredential(Long id) {
            return credential(id);
        }

        @Override
        public void deleteCredential(Long id) {
            deletedIds.add(id);
        }

        @Override
        public FeishuCredentialValidateResponse validateCredential(Long id) {
            return new FeishuCredentialValidateResponse(true, "飞书凭据有效", LocalDateTime.now());
        }

        @Override
        public FeishuTenantCredentialResponse refreshSecret(Long id, FeishuCredentialRefreshRequest request) {
            return credential(id);
        }

        private static FeishuTenantCredentialResponse credential(Long id) {
            FeishuTenantCredentialResponse response = new FeishuTenantCredentialResponse();
            response.setId(id);
            response.setTenantName("生产租户");
            response.setAppId("cli_prod");
            response.setAppSecret("******");
            response.setCredentialStatus("VALID");
            response.setEnabled(1);
            return response;
        }
    }
}
