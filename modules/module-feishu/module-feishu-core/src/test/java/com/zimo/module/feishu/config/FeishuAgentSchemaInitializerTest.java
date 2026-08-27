package com.zimo.module.feishu.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAgentSchemaInitializerTest {

    @Test
    void userMappingSchemaContainsRequiredColumns() {
        String sql = FeishuAgentSchemaInitializer.createUserMappingTableSql().toLowerCase();

        assertThat(sql).contains("create table if not exists ps_feishu_user_mapping");
        assertThat(sql).contains("feishu_user_id");
        assertThat(sql).contains("internal_user_id");
        assertThat(sql).contains("permissions");
        assertThat(sql).contains("comment='飞书用户映射表'");
    }

    @Test
    void messageLogSchemaContainsRequiredColumns() {
        String sql = FeishuAgentSchemaInitializer.createMessageLogTableSql().toLowerCase();

        assertThat(sql).contains("create table if not exists ps_feishu_message_log");
        assertThat(sql).contains("message_id");
        assertThat(sql).contains("command_text");
        assertThat(sql).contains("raw_payload");
        assertThat(sql).contains("reply_payload");
        assertThat(sql).contains("comment='飞书消息处理日志表'");
    }

    @Test
    void cliCallLogSchemaContainsRequiredColumns() {
        String sql = FeishuAgentSchemaInitializer.createCliCallLogTableSql().toLowerCase();

        assertThat(sql).contains("create table if not exists ps_feishu_cli_call_log");
        assertThat(sql).contains("business_type");
        assertThat(sql).contains("command_summary");
        assertThat(sql).contains("attempts");
        assertThat(sql).contains("stderr");
        assertThat(sql).contains("comment='飞书命令调用日志表'");
    }

    @Test
    void existingUserMappingTableColumnAdditionsAvoidNotNullConstraints() {
        String definitions = String.join("\n", FeishuAgentSchemaInitializer.userMappingColumnDefinitionsSql())
                .toLowerCase();

        assertThat(FeishuAgentSchemaInitializer.createUserMappingTableSql().toLowerCase())
                .contains("tenant_key varchar(128) not null");
        assertThat(definitions).contains("tenant_key varchar(128)");
        assertThat(definitions).doesNotContain("not null");
    }
}
