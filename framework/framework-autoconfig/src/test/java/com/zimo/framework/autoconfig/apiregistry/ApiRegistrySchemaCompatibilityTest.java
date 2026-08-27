package com.zimo.framework.autoconfig.apiregistry;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcOperations;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiRegistrySchemaCompatibilityTest {

    @Test
    void existingTableDoesNotInspectOrAlterRetiredControllerColumns() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("api_registry"))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
                eq("api_registry"), eq("uk_api_registry_hash"))).thenReturn(1);
        ApiRegistrySchemaInitializer initializer = new ApiRegistrySchemaInitializer(
                jdbc, new ByteArrayResource("CREATE TABLE ignored".getBytes(StandardCharsets.UTF_8)));

        assertThat(initializer.initializeIfNecessary()).isFalse();

        verify(jdbc).queryForObject(anyString(), eq(Integer.class),
                eq("api_registry"), eq("uk_api_registry_hash"));
        verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class),
                eq("api_registry"), eq("controller_name"));
        verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class),
                eq("api_registry"), eq("controller_path"));
        verify(jdbc, never()).execute(org.mockito.ArgumentMatchers.contains("controller_"));
    }
    @Test
    void existingTableReceivesMissingUniqueHashIndexWithoutRecreatingTable() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("api_registry"))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
                eq("api_registry"), eq("uk_api_registry_hash"))).thenReturn(0);
        ApiRegistrySchemaInitializer initializer = new ApiRegistrySchemaInitializer(
                jdbc, new ByteArrayResource("CREATE TABLE ignored".getBytes(StandardCharsets.UTF_8)));

        boolean tableCreated = initializer.initializeIfNecessary();

        assertThat(tableCreated).isFalse();
        verify(jdbc).execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_api_registry_hash ON api_registry (hash)");
    }
}
