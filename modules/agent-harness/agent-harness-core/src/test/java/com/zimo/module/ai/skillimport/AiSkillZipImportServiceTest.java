package com.zimo.module.ai.skillimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.ai.management.AiAgentManagementService;
import com.zimo.module.ai.management.AiManagedSkill;
import com.zimo.module.ai.management.AiManagedSkillRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class AiSkillZipImportServiceTest {

    @Test
    void 有效Zip转换后只调用一次现有创建方法() {
        AiAgentManagementService managementService = mock(AiAgentManagementService.class);
        AiSkillZipImportService service = service(managementService);
        AiManagedSkill created = skill("quality_query");
        when(managementService.createApiSkill(any())).thenReturn(created);

        AiManagedSkill result = service.importSkill(zipFile("quality-query.zip", validArchive()));

        ArgumentCaptor<AiManagedSkillRequest> captor = ArgumentCaptor.forClass(AiManagedSkillRequest.class);
        verify(managementService).createApiSkill(captor.capture());
        AiManagedSkillRequest request = captor.getValue();
        assertThat(result).isSameAs(created);
        assertThat(request.getName()).isEqualTo("quality_query");
        assertThat(request.getDescription()).isEqualTo("质量查询");
        assertThat(request.isReadOnly()).isFalse();
        assertThat(request.getAgentId()).isNull();
        assertThat(request.getPromptTemplateId()).isNull();
        assertThat(request.getApiConfig().getApiRegistryId()).isNull();
        assertThat(request.getApiConfig().isEnabled()).isFalse();
        assertThat(request.getApiConfig().getBaseUrl()).isEqualTo("https://api.example.com");
        assertThat(request.getApiConfig().getPath()).isEqualTo("/quality");
        assertThat(request.getApiConfig().getMethod()).isEqualTo("PATCH");
        assertThat(request.getApiConfig().getHeaders()).containsExactlyEntriesOf(Map.of("X-Tenant", "factory"));
        assertThat(request.getApiConfig().getTimeoutMillis()).isEqualTo(5000);
    }

    @Test
    void 拒绝空MultipartFile() {
        AiAgentManagementService managementService = mock(AiAgentManagementService.class);
        AiSkillZipImportService service = service(managementService);
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.zip", "application/zip", new byte[0]);

        assertThatThrownBy(() -> service.importSkill(file))
                .isInstanceOf(AiSkillImportException.class)
                .hasMessage("请选择 ZIP 技能包")
                .extracting("code")
                .isEqualTo(400);
        verify(managementService, never()).createApiSkill(any());
    }

    @Test
    void 拒绝非zip扩展名() {
        AiAgentManagementService managementService = mock(AiAgentManagementService.class);
        AiSkillZipImportService service = service(managementService);

        assertThatThrownBy(() -> service.importSkill(zipFile("quality-query.json", validArchive())))
                .isInstanceOf(AiSkillImportException.class)
                .hasMessage("仅支持 .zip 技能包")
                .extracting("code")
                .isEqualTo(400);
        verify(managementService, never()).createApiSkill(any());
    }

    @Test
    void 拒绝超过2MB压缩文件并返回413() {
        AiAgentManagementService managementService = mock(AiAgentManagementService.class);
        AiSkillZipImportService service = service(managementService);
        byte[] oversized = new byte[2 * 1024 * 1024 + 1];

        assertThatThrownBy(() -> service.importSkill(zipFile("oversized.zip", oversized)))
                .isInstanceOf(AiSkillImportException.class)
                .hasMessage("ZIP 技能包不能超过 2 MB")
                .extracting("code")
                .isEqualTo(413);
        verify(managementService, never()).createApiSkill(any());
    }

    @Test
    void Parser失败时不调用创建方法() {
        AiAgentManagementService managementService = mock(AiAgentManagementService.class);
        AiSkillZipImportService service = service(managementService);
        MockMultipartFile file = zipFile(
                "invalid.zip", "not-a-zip".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.importSkill(file))
                .isInstanceOf(AiSkillImportException.class)
                .extracting("code")
                .isEqualTo(400);
        verify(managementService, never()).createApiSkill(any());
    }

    @Test
    void 技能重名异常保持为IllegalArgumentException供Controller转换400() {
        AiAgentManagementService managementService = mock(AiAgentManagementService.class);
        AiSkillZipImportService service = service(managementService);
        IllegalArgumentException duplicate = new IllegalArgumentException("API技能已存在");
        when(managementService.createApiSkill(any())).thenThrow(duplicate);

        assertThatThrownBy(() -> service.importSkill(zipFile("duplicate.zip", validArchive())))
                .isSameAs(duplicate);
    }

    @Test
    void 持久化异常不包装为400或413() {
        AiAgentManagementService managementService = mock(AiAgentManagementService.class);
        AiSkillZipImportService service = service(managementService);
        RuntimeException persistenceFailure = new RuntimeException("persistence failure");
        when(managementService.createApiSkill(any())).thenThrow(persistenceFailure);

        assertThatThrownBy(() -> service.importSkill(zipFile("persistence.zip", validArchive())))
                .isSameAs(persistenceFailure);
    }

    @Test
    void 注册表异常不包装为400或413() {
        AiAgentManagementService managementService = mock(AiAgentManagementService.class);
        AiSkillZipImportService service = service(managementService);
        RuntimeException registryFailure = new RuntimeException("registry failure");
        when(managementService.createApiSkill(any())).thenThrow(registryFailure);

        assertThatThrownBy(() -> service.importSkill(zipFile("registry.zip", validArchive())))
                .isSameAs(registryFailure);
    }

    private static AiSkillZipImportService service(AiAgentManagementService managementService) {
        return new AiSkillZipImportService(new AiSkillZipParser(new ObjectMapper()), managementService);
    }

    private static AiManagedSkill skill(String name) {
        return new AiManagedSkill(name, "质量查询", false, 0, null, null, "api", true, null, LocalDateTime.now());
    }

    private static MockMultipartFile zipFile(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, "application/zip", content);
    }

    private static byte[] validArchive() {
        return zip("""
                {
                  "schemaVersion": 1,
                  "name": " quality_query ",
                  "description": " 质量查询 ",
                  "readOnly": false,
                  "apiConfig": {
                    "enabled": false,
                    "baseUrl": " https://api.example.com ",
                    "path": " /quality ",
                    "method": " PATCH ",
                    "headers": {
                      "X-Tenant": "factory"
                    },
                    "timeoutMillis": 5000
                  }
                }
                """);
    }

    private static byte[] zip(String manifest) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry("skill.json"));
                zip.write(manifest.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
