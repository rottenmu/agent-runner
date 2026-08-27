package com.zimo.framework.autoconfig.apiregistry;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcOperations;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiRegistrySchemaMigrationSafetyTest {

    private static final String CREATE_HASH_INDEX_SQL =
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_api_registry_hash ON api_registry (hash)";

    @Test
    void duplicateRowsAreArchivedAndRehashedBeforeUniqueIndexCreation() {
        JdbcOperations jdbc = schemaWithoutIndex();
        ApiRegistrySchemaInitializer initializer = initializer(jdbc);

        initializer.initializeIfNecessary();

        InOrder order = inOrder(jdbc);
        order.verify(jdbc).execute(org.mockito.ArgumentMatchers.startsWith("UPDATE api_registry"));
        order.verify(jdbc).execute(CREATE_HASH_INDEX_SQL);
    }

    @Test
    void concurrentIndexCreationIsAcceptedAfterUniqueIndexAppears() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("api_registry"))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
                eq("api_registry"), eq("uk_api_registry_hash")))
                .thenReturn(0, 1);
        doThrow(new BadSqlGrammarException(
                        "create index", CREATE_HASH_INDEX_SQL, new SQLException("index already exists")))
                .when(jdbc).execute(CREATE_HASH_INDEX_SQL);
        ApiRegistrySchemaInitializer initializer = initializer(jdbc);

        assertThatCode(initializer::initializeIfNecessary).doesNotThrowAnyException();
    }

    private JdbcOperations schemaWithoutIndex() {
        JdbcOperations jdbc = mock(JdbcOperations.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("api_registry"))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
                eq("api_registry"), eq("uk_api_registry_hash"))).thenReturn(0);
        return jdbc;
    }

    private ApiRegistrySchemaInitializer initializer(JdbcOperations jdbc) {
        return new ApiRegistrySchemaInitializer(jdbc,
                new ByteArrayResource("CREATE TABLE ignored".getBytes(StandardCharsets.UTF_8)));
    }
}
