package com.zimo.module.ai.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.ai.controller.AiSkillAdminController;
import com.zimo.module.ai.management.AiAgentManagementService;
import com.zimo.module.ai.management.AiManagedSkill;
import com.zimo.module.ai.skillimport.AiSkillZipImportService;
import jakarta.servlet.MultipartConfigElement;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.HttpEncodingAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

@SpringBootTest(
        classes = AiSkillMultipartWebIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiSkillMultipartWebIntegrationTest {

    private static final int ONE_AND_HALF_MEGABYTES = 1536 * 1024;
    private static final int OVER_TWO_MEGABYTES = 2 * 1024 * 1024 + 1;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AiSkillZipImportService importService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MultipartConfigElement multipartConfig;

    @BeforeEach
    void prepareImportResult() {
        reset(importService);
        when(importService.importSkill(any(MultipartFile.class)))
                .thenReturn(new AiManagedSkill(
                        "imported-skill",
                        "导入测试技能",
                        false,
                        0,
                        null,
                        null,
                        "zip",
                        true,
                        null,
                        java.time.LocalDateTime.now()));
    }

    @Test
    void oneAndHalfMegabyteFileReachesSkillImportFlow() throws Exception {
        ResponseEntity<String> response = upload(ONE_AND_HALF_MEGABYTES);

        assertThat(multipartConfig.getMaxFileSize()).isEqualTo(2L * 1024 * 1024);
        assertThat(multipartConfig.getMaxRequestSize()).isEqualTo(3L * 1024 * 1024);
        assertThat(multipartConfig.getFileSizeThreshold()).isEqualTo(2 * 1024 * 1024);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        JsonNode body = responseBody(response);
        assertThat(body.path("code").asInt()).isEqualTo(200);
        assertThat(body.path("data").path("name").asText())
                .isEqualTo("imported-skill");
        verify(importService).importSkill(any(MultipartFile.class));
    }

    @Test
    void fileLargerThanTwoMegabytesReturnsBusinessStatus413() throws Exception {
        ResponseEntity<String> response = upload(OVER_TWO_MEGABYTES);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        JsonNode body = responseBody(response);
        assertThat(body.path("code").asInt()).isEqualTo(413);
        assertThat(body.path("msg").asText()).contains("2MB");
        verifyNoInteractions(importService);
    }

    @Test
    void malformedMultipartReturnsUnifiedBusinessStatus400() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("multipart/form-data;boundary=broken"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/biz/ai/skills/import",
                new HttpEntity<>("--broken\r\ninvalid", headers),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        JsonNode body = responseBody(response);
        assertThat(body.path("code").asInt()).isEqualTo(400);
    }

    private ResponseEntity<String> upload(int size) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(bytes(size), "skill.zip"));
        return restTemplate.postForEntity(
                "/api/biz/ai/skills/import",
                new HttpEntity<>(body, headers),
                String.class);
    }

    private JsonNode responseBody(ResponseEntity<String> response) throws Exception {
        assertThat(response.getBody()).isNotBlank();
        return objectMapper.readTree(response.getBody());
    }

    private byte[] bytes(int size) {
        byte[] content = new byte[size];
        Arrays.fill(content, (byte) 'x');
        return content;
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
        ServletWebServerFactoryAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class,
        WebMvcAutoConfiguration.class,
        HttpEncodingAutoConfiguration.class,
        JacksonAutoConfiguration.class,
        MultipartAutoConfiguration.class,
        AiModuleAutoConfiguration.class,
        AiSkillMultipartWebAutoConfiguration.class
    })
    @Import(TestBeans.class)
    static class TestApplication {
    }

    @Configuration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        AiAgentManagementService aiAgentManagementService() {
            return mock(AiAgentManagementService.class);
        }

        @Bean
        AiSkillZipImportService aiSkillZipImportService() {
            return mock(AiSkillZipImportService.class);
        }

        @Bean
        AiSkillAdminController aiSkillAdminController(
                AiAgentManagementService managementService,
                AiSkillZipImportService importService) {
            return new AiSkillAdminController(managementService, importService);
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
