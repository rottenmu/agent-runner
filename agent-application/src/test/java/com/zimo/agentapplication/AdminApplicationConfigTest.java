package com.zimo.agentapplication;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.net.URL;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.core.io.ClassPathResource;

class AdminApplicationConfigTest {

    @Test
    void mainDatasourceUsesSqlite() throws IOException {
        String applicationYaml = new ClassPathResource("application.yml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(applicationYaml).contains("jdbc:sqlite:");
        assertThat(applicationYaml).doesNotContain("jdbc:mysql://");
    }

    @Test
    void mainApplicationAllowsFileUploadsLargerThanSpringDefault() throws IOException {
        String applicationYaml = new ClassPathResource("application.yml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(applicationYaml).contains("servlet:");
        assertThat(applicationYaml).contains("multipart:");
        assertThat(applicationYaml).contains("max-file-size: 200MB");
        assertThat(applicationYaml).contains("max-request-size: 200MB");
    }

    @Test
    void mainApplicationDoesNotGloballyScanModuleMappers() {
        MapperScan mapperScan = AgentApplication.class.getAnnotation(MapperScan.class);

        assertThat(mapperScan).isNull();
    }

    @Test
    void feishuProjectAgentArchiveTableCanBeConfiguredByEnvironment() throws IOException {
        String applicationYaml = new ClassPathResource("application.yml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(applicationYaml).contains("archive-app-token: ${FEISHU_ARCHIVE_APP_TOKEN:}");
        assertThat(applicationYaml).contains("archive-table-id: ${FEISHU_ARCHIVE_TABLE_ID:}");
    }

    @Test
    void adminClasspathDoesNotImportLegacyAgentScopeAutoConfiguration() throws IOException {
        List<String> imports = Collections.list(Thread.currentThread()
                        .getContextClassLoader()
                        .getResources("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"))
                .stream()
                .flatMap(resource -> readImportLines(resource).stream())
                .toList();

        assertThat(imports).doesNotContain("com.zimo.framework.autoconfig.AgentScopeAutoConfiguration");
    }

    private static List<String> readImportLines(URL resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
