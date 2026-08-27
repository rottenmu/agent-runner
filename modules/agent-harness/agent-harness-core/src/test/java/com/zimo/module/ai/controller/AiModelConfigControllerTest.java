package com.zimo.module.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zimo.module.ai.modelconfig.AiModelConfigImportRequest;
import com.zimo.module.ai.modelconfig.AiModelConfigImportResponse;
import com.zimo.module.ai.modelconfig.AiModelConfigQuery;
import com.zimo.module.ai.modelconfig.AiModelConfigRequest;
import com.zimo.module.ai.modelconfig.AiModelConfigResponse;
import com.zimo.module.ai.modelconfig.AiModelConfigService;
import com.zimo.module.ai.modelconfig.AiModelConfigTestResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class AiModelConfigControllerTest {

    @Test
    void 查询模型配置列表时绑定Status查询契约() throws Exception {
        AiModelConfigService service = mock(AiModelConfigService.class);
        when(service.list(any(AiModelConfigQuery.class))).thenReturn(List.of(response(1L)));

        mockMvc(service).perform(get("/api/biz/ai/model-configs")
                        .param("env", "prod")
                        .param("provider", "bailian")
                        .param("status", "enabled")
                        .param("keyword", "qwen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].apiKeyMasked").value("sk-****5678"));

        ArgumentCaptor<AiModelConfigQuery> captor = ArgumentCaptor.forClass(AiModelConfigQuery.class);
        verify(service).list(captor.capture());
        assertThat(captor.getValue())
                .extracting("env", "provider", "status", "keyword")
                .containsExactly("prod", "bailian", "enabled", "qwen");
    }

    @Test
    void 查询模型配置详情时返回脱敏响应体() throws Exception {
        AiModelConfigService service = mock(AiModelConfigService.class);
        when(service.get(7L)).thenReturn(response(7L));

        mockMvc(service).perform(get("/api/biz/ai/model-configs/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.apiKeyMasked").value("sk-****5678"));

        verify(service).get(7L);
    }

    @Test
    void 新增模型配置时转调业务服务() throws Exception {
        AiModelConfigService service = mock(AiModelConfigService.class);
        when(service.create(any(AiModelConfigRequest.class))).thenReturn(response(2L));

        mockMvc(service).perform(post("/api/biz/ai/model-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("生产百炼")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.configName").value("生产百炼"));

        verify(service).create(any(AiModelConfigRequest.class));
    }

    @Test
    void 编辑模型配置时按Id转调业务服务() throws Exception {
        AiModelConfigService service = mock(AiModelConfigService.class);
        when(service.update(eq(3L), any(AiModelConfigRequest.class))).thenReturn(response(3L));

        mockMvc(service).perform(put("/api/biz/ai/model-configs/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("生产百炼")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(3));

        verify(service).update(eq(3L), any(AiModelConfigRequest.class));
    }

    @Test
    void 删除模型配置时返回空成功响应体() throws Exception {
        AiModelConfigService service = mock(AiModelConfigService.class);

        mockMvc(service).perform(delete("/api/biz/ai/model-configs/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(service).delete(4L);
    }

    @Test
    void 复制模型配置时返回新配置() throws Exception {
        AiModelConfigService service = mock(AiModelConfigService.class);
        when(service.copy(5L)).thenReturn(response(6L));

        mockMvc(service).perform(post("/api/biz/ai/model-configs/5/copy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(6));

        verify(service).copy(5L);
    }

    @Test
    void 测试模型连接时返回测试结果() throws Exception {
        AiModelConfigService service = mock(AiModelConfigService.class);
        when(service.testConnection(8L)).thenReturn(testResponse());

        mockMvc(service).perform(post("/api/biz/ai/model-configs/8/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("success"))
                .andExpect(jsonPath("$.data.latency").value(35));

        verify(service).testConnection(8L);
    }

    @Test
    void 导入模型配置时返回成功和跳过数量() throws Exception {
        AiModelConfigService service = mock(AiModelConfigService.class);
        when(service.importConfigs(any(AiModelConfigImportRequest.class)))
                .thenReturn(new AiModelConfigImportResponse(1, 1));

        mockMvc(service).perform(post("/api/biz/ai/model-configs/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configs": [
                                    {
                                      "configName": "生产百炼",
                                      "provider": "bailian",
                                      "endpoint": "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
                                      "apiKey": "sk-12345678",
                                      "modelId": "qwen-plus",
                                      "env": "prod"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.skippedCount").value(1));

        verify(service).importConfigs(any(AiModelConfigImportRequest.class));
    }

    @Test
    void 导出模型配置时绑定Status查询契约() throws Exception {
        AiModelConfigService service = mock(AiModelConfigService.class);
        when(service.export(any(AiModelConfigQuery.class))).thenReturn(List.of(response(9L)));

        mockMvc(service).perform(get("/api/biz/ai/model-configs/export")
                        .param("status", "disabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(9));

        ArgumentCaptor<AiModelConfigQuery> captor = ArgumentCaptor.forClass(AiModelConfigQuery.class);
        verify(service).export(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("disabled");
    }

    @Test
    void 声明RestController真实平台路径契约() {
        assertThat(AiModelConfigController.class.isAnnotationPresent(RestController.class)).isTrue();
        RequestMapping mapping = AiModelConfigController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/api/biz/ai/model-configs");
    }

    private static MockMvc mockMvc(AiModelConfigService service) {
        return MockMvcBuilders.standaloneSetup(new AiModelConfigController(service)).build();
    }

    private static AiModelConfigResponse response(Long id) {
        return new AiModelConfigResponse(
                id,
                "生产百炼",
                "项目工作台默认模型",
                "bailian",
                "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
                "sk-****5678",
                "qwen-plus",
                "prod",
                true,
                new BigDecimal("0.70"),
                new BigDecimal("0.80"),
                4096,
                List.of("项目", "默认"),
                "untested",
                0,
                "尚未测试",
                null,
                LocalDateTime.of(2026, 7, 23, 10, 0),
                LocalDateTime.of(2026, 7, 23, 10, 5));
    }

    private static AiModelConfigTestResponse testResponse() {
        return new AiModelConfigTestResponse(
                "success",
                35,
                "连接校验通过",
                LocalDateTime.of(2026, 7, 23, 10, 10));
    }

    private static String requestJson(String configName) {
        return """
                {
                  "configName": "%s",
                  "description": "项目工作台默认模型",
                  "provider": "bailian",
                  "endpoint": "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
                  "apiKey": "sk-12345678",
                  "modelId": "qwen-plus",
                  "env": "prod",
                  "enabled": true,
                  "temperature": 0.7,
                  "topP": 0.8,
                  "maxTokens": 4096,
                  "tags": ["项目", "默认"]
                }
                """.formatted(configName);
    }
}
