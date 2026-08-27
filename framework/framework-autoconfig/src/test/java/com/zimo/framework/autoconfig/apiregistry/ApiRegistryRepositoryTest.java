package com.zimo.framework.autoconfig.apiregistry;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcOperations;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiRegistryRepositoryTest {

    @Test
    void initializeSkipsSchemaScriptWhenTableAlreadyExists() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("api_registry"))).thenReturn(1);
        ApiRegistrySchemaInitializer initializer = new ApiRegistrySchemaInitializer(
                jdbc, schemaResource("CREATE TABLE api_registry (id BIGINT PRIMARY KEY)"));

        boolean initialized = initializer.initializeIfNecessary();

        assertThat(initialized).isFalse();
        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void initializeExecutesSchemaScriptWhenTableIsMissing() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("api_registry"))).thenReturn(0);
        ApiRegistrySchemaInitializer initializer = new ApiRegistrySchemaInitializer(
                jdbc, schemaResource("CREATE TABLE api_registry (id BIGINT PRIMARY KEY)"));

        boolean initialized = initializer.initializeIfNecessary();

        assertThat(initialized).isTrue();
        verify(jdbc).execute("CREATE TABLE api_registry (id BIGINT PRIMARY KEY)");
    }

    @Test
    void schemaDefinesUniqueHashIndexForAtomicDeduplication() throws Exception {
        String ddl = new ClassPathResource("db/framework/api_registry.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(ddl).contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_api_registry_hash ON api_registry (hash)");
        assertThat(ddl).doesNotContain("controller_name", "controller_path");
    }

    @Test
    void saveAllUsesAtomicSqliteUpsertAndPreservesManualFields() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 2);
        ApiRegistryRepository repository = new ApiRegistryRepository(jdbc);

        ApiRegistrySaveResult result = repository.saveAll(List.of(
                endpoint("first-hash", "/api/material/categories"),
                endpoint("second-hash", "/api/material/items")));

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object[]> parameters = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, org.mockito.Mockito.times(2)).update(sql.capture(), parameters.capture());
        assertThat(sql.getAllValues()).allSatisfy(value -> {
            assertThat(value).contains("controller_desc, method, path");
            assertThat(insertColumns(value)).containsExactly(
                    "module_code", "module_name", "module_base_path", "module_desc",
                    "controller_desc", "method", "path", "api_name", "summary", "description",
                    "tags", "request_params", "response_example", "auth_required", "deprecated",
                    "hash", "version");
            assertThat(value.chars().filter(character -> character == '?').count()).isEqualTo(17);
            assertThat(value).doesNotContain("controller_name", "controller_path", "status", "sort");
            assertThat(value).contains("ON CONFLICT(hash) DO UPDATE SET");
        });
        assertThat(parameters.getAllValues().get(0)).containsExactly(
                "material", "物料管理", "/api/material", "", "", "GET",
                "/api/material/categories", "list", "list", "", "[\"material\"]", "[]",
                null, 1, 0, "first-hash", "1.0.0");
        assertThat(parameters.getAllValues().get(1)).containsExactly(
                "material", "物料管理", "/api/material", "", "", "GET",
                "/api/material/items", "list", "list", "", "[\"material\"]", "[]",
                null, 1, 0, "second-hash", "1.0.0");
    }

    private List<String> insertColumns(String sql) {
        Matcher matcher = Pattern.compile(
                "INSERT\\s+INTO\\s+api_registry\\s*\\((.*?)\\)\\s*VALUES",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(sql);
        assertThat(matcher.find()).as("UPSERT SQL should contain an INSERT column list").isTrue();
        return Arrays.stream(matcher.group(1).split(","))
                .map(String::trim)
                .filter(column -> !column.isEmpty())
                .toList();
    }

    private ByteArrayResource schemaResource(String sql) {
        return new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8));
    }

    private ApiEndpointMetadata endpoint(String hash, String path) {
        return new ApiEndpointMetadata(
                "material", "物料管理", "/api/material", "", "", "GET", path,
                "list", "list", "", "[\"material\"]", "[]", null,
                true, false, hash, "1.0.0");
    }
}
