package com.zimo.framework.autoconfig.apiregistry;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcOperations;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiRegistryCompositeIndexValidationTest {

    @Test
    void indexValidationQueriesSqliteMasterForHashIndexExistence() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("api_registry"))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
                eq("api_registry"), eq("uk_api_registry_hash"))).thenReturn(1);
        ApiRegistrySchemaInitializer initializer = new ApiRegistrySchemaInitializer(
                jdbc, new ByteArrayResource("CREATE TABLE ignored".getBytes(StandardCharsets.UTF_8)));

        initializer.initializeIfNecessary();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> parameters = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Integer.class),
                parameters.capture(), parameters.capture());
        assertThat(sql.getValue()).contains("FROM sqlite_master");
        assertThat(sql.getValue()).contains("type = 'index'");
        assertThat(parameters.getAllValues()).containsExactly("api_registry", "uk_api_registry_hash");
    }
}
