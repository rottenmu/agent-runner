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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiRegistryEmptyHashMigrationTest {

    @Test
    void duplicateArchivalIncludesLegacyRowsWithEmptyHashes() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("api_registry"))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
                eq("api_registry"), eq("uk_api_registry_hash"))).thenReturn(0);
        ApiRegistrySchemaInitializer initializer = new ApiRegistrySchemaInitializer(
                jdbc, new ByteArrayResource("CREATE TABLE ignored".getBytes(StandardCharsets.UTF_8)));

        initializer.initializeIfNecessary();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).execute(sql.capture());
        assertThat(sql.getAllValues().get(0)).doesNotContain("WHERE hash <> ''");
    }
}
