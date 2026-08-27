package com.zimo.module.feishu.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuSchemaInitializerTest {
    @Test
    void schemaCreatesFeishuConfigTableWithAgentCredentialColumns() {
        String schema = FeishuSchemaInitializer.createTableSql().toLowerCase();

        assertThat(schema).contains("create table if not exists ps_feishu_config");
        assertThat(schema).contains("tenant_key");
        assertThat(schema).contains("tenant_name");
        assertThat(schema).contains("credential_status");
        assertThat(schema).contains("scan_ticket");
        assertThat(schema).contains("permission_scopes");
        assertThat(schema).contains("event_subscriptions");
        assertThat(schema).contains("comment='飞书应用配置表'");
        assertColumnsHaveComments(FeishuSchemaInitializer.createTableSql());
    }

    private void assertColumnsHaveComments(String createTableSql) {
        for (String line : createTableSql.lines().toList()) {
            String normalized = line.trim().toLowerCase();
            if (normalized.isEmpty()
                    || normalized.startsWith("create table")
                    || normalized.startsWith(")")
                    || normalized.startsWith("index ")) {
                continue;
            }
            assertThat(normalized)
                    .as("column definition should have comment: %s", line.trim())
                    .contains(" comment '");
        }
    }
}
