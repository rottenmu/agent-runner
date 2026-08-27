package com.zimo.module.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zimo.framework.common.BizException;
import com.zimo.module.ai.management.AiAgentManagementService;
import com.zimo.module.ai.management.AiManagedSkill;
import com.zimo.module.ai.management.AiManagedSkillRequest;
import com.zimo.module.ai.management.AiSkillApiConfigRequest;
import com.zimo.module.ai.management.AiSkillApiConfigResponse;
import com.zimo.module.ai.skillimport.AiSkillImportException;
import com.zimo.module.ai.skillimport.AiSkillZipImportService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

class AiSkillAdminControllerTest {

    @Test
    void 查询技能列表时返回平台统一响应体() throws Exception {
        ControllerFixture fixture = fixture();
        when(fixture.managementService().listSkills()).thenReturn(List.of(skill("remote_quality_check")));

        fixture.mockMvc().perform(get("/api/biz/ai/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data[0].name").value("remote_quality_check"))
                .andExpect(jsonPath("$.data[0].source").value("api"));

        verify(fixture.managementService()).listSkills();
    }

    @Test
    void 创建Api技能时转调Starter管理服务() throws Exception {
        ControllerFixture fixture = fixture();
        when(fixture.managementService().createApiSkill(any(AiManagedSkillRequest.class)))
                .thenReturn(skill("remote_quality_check"));

        fixture.mockMvc().perform(post("/api/biz/ai/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(skillPayload("remote_quality_check")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data.name").value("remote_quality_check"));

        verify(fixture.managementService()).createApiSkill(any(AiManagedSkillRequest.class));
    }

    @Test
    void 编辑Api技能时转调Starter管理服务() throws Exception {
        ControllerFixture fixture = fixture();
        when(fixture.managementService().updateApiSkill(
                eq("remote_quality_check"), any(AiManagedSkillRequest.class)))
                .thenReturn(skill("remote_quality_check"));

        fixture.mockMvc().perform(put("/api/biz/ai/skills/remote_quality_check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(skillPayload("remote_quality_check")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data.name").value("remote_quality_check"));

        verify(fixture.managementService()).updateApiSkill(
                eq("remote_quality_check"), any(AiManagedSkillRequest.class));
    }

    @Test
    void 更新Api配置时转调Starter管理服务() throws Exception {
        ControllerFixture fixture = fixture();
        when(fixture.managementService().updateApiSkillConfig(
                eq("remote_quality_check"), any(AiSkillApiConfigRequest.class)))
                .thenReturn(skill("remote_quality_check"));

        fixture.mockMvc().perform(put("/api/biz/ai/skills/remote_quality_check/api-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "apiRegistryId": 1001,
                                  "enabled": true,
                                  "baseUrl": "https://api.example.com",
                                  "path": "/quality",
                                  "method": "POST",
                                  "headers": {},
                                  "timeoutMillis": 3000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data.apiConfig.path").value("/quality"));

        verify(fixture.managementService()).updateApiSkillConfig(
                eq("remote_quality_check"), any(AiSkillApiConfigRequest.class));
    }

    @Test
    void 绑定提示词模板时转调Starter管理服务() throws Exception {
        ControllerFixture fixture = fixture();
        when(fixture.managementService().bindSkillPromptTemplate("remote_quality_check", 88L))
                .thenReturn(skill("remote_quality_check"));

        fixture.mockMvc().perform(put("/api/biz/ai/skills/remote_quality_check/prompt-template")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptTemplateId\":88}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"));

        verify(fixture.managementService()).bindSkillPromptTemplate("remote_quality_check", 88L);
    }

    @Test
    void 清空提示词模板时允许空请求体() throws Exception {
        ControllerFixture fixture = fixture();
        when(fixture.managementService().bindSkillPromptTemplate("remote_quality_check", (Long) null))
                .thenReturn(skill("remote_quality_check"));

        fixture.mockMvc().perform(put("/api/biz/ai/skills/remote_quality_check/prompt-template")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"));

        verify(fixture.managementService()).bindSkillPromptTemplate("remote_quality_check", (Long) null);
    }

    @Test
    void 删除Api技能时转调Starter管理服务() throws Exception {
        ControllerFixture fixture = fixture();
        when(fixture.managementService().deleteApiSkill("remote_quality_check")).thenReturn(true);

        fixture.mockMvc().perform(delete("/api/biz/ai/skills/remote_quality_check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(fixture.managementService()).deleteApiSkill("remote_quality_check");
    }

    @Test
    void 删除不存在技能时转换为404业务异常() {
        ControllerFixture fixture = fixture();
        when(fixture.managementService().deleteApiSkill("missing_skill")).thenReturn(false);

        assertThatThrownBy(() -> fixture.controller().delete("missing_skill"))
                .as("删除不存在的 API 技能应返回业务 404")
                .isInstanceOf(BizException.class)
                .hasMessage("API技能不存在")
                .extracting("code")
                .isEqualTo(404);
    }

    @Test
    void Starter参数异常转换为400业务异常() {
        ControllerFixture fixture = fixture();
        when(fixture.managementService().updateApiSkill(eq("summarize"), any(AiManagedSkillRequest.class)))
                .thenThrow(new IllegalArgumentException("内置技能不可编辑"));

        assertThatThrownBy(() -> fixture.controller().update("summarize", new AiManagedSkillRequest()))
                .as("starter 参数异常应转换成平台业务异常")
                .isInstanceOf(BizException.class)
                .hasMessage("内置技能不可编辑")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void 上传Zip时返回创建后的技能() throws Exception {
        ControllerFixture fixture = fixture();
        when(fixture.importService().importSkill(any())).thenReturn(skill("quality_query"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "quality.zip", "application/zip", new byte[] {0x50, 0x4b, 0x03, 0x04});

        fixture.mockMvc().perform(multipart("/api/biz/ai/skills/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("quality_query"));

        verify(fixture.importService()).importSkill(any(MultipartFile.class));
    }

    @Test
    void 导入格式异常转换为400业务异常() {
        ControllerFixture fixture = fixture();
        when(fixture.importService().importSkill(any()))
                .thenThrow(AiSkillImportException.badRequest("缺少 skill.json"));

        assertThatThrownBy(() -> fixture.controller().importSkill(mock(MultipartFile.class)))
                .isInstanceOf(BizException.class)
                .hasMessage("缺少 skill.json")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void 导入超限异常转换为413业务异常() {
        ControllerFixture fixture = fixture();
        when(fixture.importService().importSkill(any()))
                .thenThrow(AiSkillImportException.tooLarge("ZIP 技能包不能超过 2 MB"));

        assertThatThrownBy(() -> fixture.controller().importSkill(mock(MultipartFile.class)))
                .isInstanceOf(BizException.class)
                .hasMessage("ZIP 技能包不能超过 2 MB")
                .extracting("code")
                .isEqualTo(413);
    }

    @Test
    void 缺少file字段时进入Service并转换为400业务异常() {
        ControllerFixture fixture = fixture();
        when(fixture.importService().importSkill(isNull()))
                .thenThrow(AiSkillImportException.badRequest("请选择 ZIP 技能包"));

        assertThatThrownBy(() -> fixture.controller().importSkill(null))
                .isInstanceOf(BizException.class)
                .hasMessage("请选择 ZIP 技能包")
                .extracting("code")
                .isEqualTo(400);
        verify(fixture.importService()).importSkill(null);
    }

    @Test
    void 技能重名转换为400业务异常() {
        ControllerFixture fixture = fixture();
        when(fixture.importService().importSkill(any()))
                .thenThrow(new IllegalArgumentException("技能名称已存在"));

        assertThatThrownBy(() -> fixture.controller().importSkill(mock(MultipartFile.class)))
                .isInstanceOf(BizException.class)
                .hasMessage("技能名称已存在")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void 持久化RuntimeException不被Controller捕获以便全局返回500() {
        ControllerFixture fixture = fixture();
        RuntimeException failure = new RuntimeException("持久化失败");
        when(fixture.importService().importSkill(any())).thenThrow(failure);

        assertThatThrownBy(() -> fixture.controller().importSkill(mock(MultipartFile.class)))
                .isSameAs(failure);
    }

    @Test
    void 导入方法声明multipart路由和file参数() throws NoSuchMethodException {
        var method = AiSkillAdminController.class.getMethod("importSkill", MultipartFile.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        RequestPart requestPart = method.getParameters()[0].getAnnotation(RequestPart.class);

        assertThat(mapping).as("技能导入方法必须声明 POST 路由").isNotNull();
        assertThat(mapping.value()).containsExactly("/import");
        assertThat(mapping.consumes()).containsExactly(MediaType.MULTIPART_FORM_DATA_VALUE);
        assertThat(requestPart).as("技能导入文件必须声明为 multipart file 字段").isNotNull();
        assertThat(requestPart.value()).isEqualTo("file");
        assertThat(requestPart.required()).as("缺少文件时应进入导入服务统一返回业务错误").isFalse();
    }

    @Test
    void 声明RestController平台路径契约() {
        assertThat(AiSkillAdminController.class.isAnnotationPresent(RestController.class))
                .as("技能管理适配层必须声明为 REST 控制器")
                .isTrue();
        RequestMapping mapping = AiSkillAdminController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping).as("技能管理适配层必须声明平台路径").isNotNull();
        assertThat(mapping.value()).containsExactly("/api/biz/ai/skills");
    }

    private static ControllerFixture fixture() {
        return new ControllerFixture(
                mock(AiAgentManagementService.class),
                mock(AiSkillZipImportService.class));
    }

    private static AiManagedSkill skill(String name) {
        AiSkillApiConfigResponse apiConfig = new AiSkillApiConfigResponse(
                true,
                "https://api.example.com",
                "/quality",
                "POST",
                Map.of(),
                3000,
                null);
        return new AiManagedSkill(name, "质检查询", true, 0, null, apiConfig, "api", true, null, LocalDateTime.now());
    }

    private static String skillPayload(String name) {
        return """
                {
                  "name": "%s",
                  "description": "质检查询",
                  "readOnly": true,
                  "apiConfig": {
                    "apiRegistryId": 1001,
                    "enabled": true,
                    "baseUrl": "https://api.example.com",
                    "path": "/quality",
                    "method": "POST",
                    "headers": {},
                    "timeoutMillis": 3000
                  }
                }
                """.formatted(name);
    }

    private record ControllerFixture(
            AiAgentManagementService managementService,
            AiSkillZipImportService importService) {

        AiSkillAdminController controller() {
            return new AiSkillAdminController(managementService, importService);
        }

        MockMvc mockMvc() {
            return MockMvcBuilders.standaloneSetup(controller()).build();
        }
    }
}
