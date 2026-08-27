package com.zimo.module.feishu.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

@Order(Ordered.HIGHEST_PRECEDENCE + 61)
public class FeishuAgentSchemaInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(FeishuAgentSchemaInitializer.class);
    private static final String[] TABLE_TYPES = {"TABLE"};

    private final DataSource dataSource;

    public FeishuAgentSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()) {
            ensureTable(connection, statement, "ps_feishu_user_mapping", createUserMappingTableSql(), userMappingColumns());
            ensureTable(connection, statement, "ps_feishu_message_log", createMessageLogTableSql(), messageLogColumns());
            ensureTable(connection, statement, "ps_feishu_cli_call_log", createCliCallLogTableSql(), cliCallLogColumns());
        }
        log.info("Feishu agent schema checked");
    }

    public static String createUserMappingTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS ps_feishu_user_mapping (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    feishu_user_id VARCHAR(128),
                    feishu_open_id VARCHAR(128),
                    feishu_union_id VARCHAR(128),
                    tenant_key VARCHAR(128) NOT NULL,
                    internal_user_id BIGINT NOT NULL,
                    internal_account VARCHAR(128) NOT NULL,
                    internal_user_name VARCHAR(128),
                    organization_id VARCHAR(128),
                    organization_name VARCHAR(128),
                    data_scope VARCHAR(32),
                    permissions TEXT,
                    enabled TINYINT DEFAULT 1,
                    remark VARCHAR(512),
                    deleted TINYINT DEFAULT 0,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_ps_feishu_user_mapping_user_id (tenant_key, feishu_user_id),
                    INDEX idx_ps_feishu_user_mapping_open_id (tenant_key, feishu_open_id),
                    INDEX idx_ps_feishu_user_mapping_union_id (tenant_key, feishu_union_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='飞书用户映射表'
                """;
    }

    public static String createCliCallLogTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS ps_feishu_cli_call_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    business_type VARCHAR(64),
                    method VARCHAR(16),
                    api_path VARCHAR(512),
                    command_summary VARCHAR(1024),
                    params_json LONGTEXT,
                    data_json LONGTEXT,
                    success TINYINT DEFAULT 1,
                    exit_code INT,
                    attempts INT,
                    cost_millis BIGINT,
                    stdout LONGTEXT,
                    stderr LONGTEXT,
                    error_message VARCHAR(1024),
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_ps_feishu_cli_call_log_business (business_type),
                    INDEX idx_ps_feishu_cli_call_log_api_path (api_path(191)),
                    INDEX idx_ps_feishu_cli_call_log_create_time (create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='飞书命令调用日志表'
                """;
    }

    public static String createMessageLogTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS ps_feishu_message_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    message_id VARCHAR(128),
                    chat_id VARCHAR(128),
                    tenant_key VARCHAR(128),
                    sender_user_id VARCHAR(128),
                    sender_open_id VARCHAR(128),
                    internal_user_id BIGINT,
                    internal_account VARCHAR(128),
                    command_text TEXT,
                    reply_type VARCHAR(32),
                    stage VARCHAR(32),
                    success TINYINT DEFAULT 1,
                    error_code INT,
                    error_message VARCHAR(1024),
                    cost_millis BIGINT,
                    raw_payload LONGTEXT,
                    reply_payload LONGTEXT,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_ps_feishu_message_log_message_id (message_id),
                    INDEX idx_ps_feishu_message_log_chat_id (chat_id),
                    INDEX idx_ps_feishu_message_log_sender (tenant_key, sender_user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='飞书消息处理日志表'
                """;
    }

    static List<String> userMappingColumnDefinitionsSql() {
        return userMappingColumns().stream()
                .map(ColumnDefinition::definition)
                .toList();
    }

    private void ensureTable(
            Connection connection,
            Statement statement,
            String tableName,
            String createSql,
            List<ColumnDefinition> columns) throws Exception {
        if (!tableExists(connection, tableName)) {
            statement.execute(createSql);
            log.info("Feishu agent schema table initialized: {}", tableName);
            return;
        }
        for (ColumnDefinition column : columns) {
            if (!columnExists(connection, tableName, column.name())) {
                statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + column.definition());
                log.info("Feishu agent schema column added: {}.{}", tableName, column.name());
            }
        }
    }

    private static List<ColumnDefinition> userMappingColumns() {
        return List.of(
                new ColumnDefinition("feishu_user_id", "feishu_user_id VARCHAR(128)"),
                new ColumnDefinition("feishu_open_id", "feishu_open_id VARCHAR(128)"),
                new ColumnDefinition("feishu_union_id", "feishu_union_id VARCHAR(128)"),
                new ColumnDefinition("tenant_key", "tenant_key VARCHAR(128)"),
                new ColumnDefinition("internal_user_id", "internal_user_id BIGINT"),
                new ColumnDefinition("internal_account", "internal_account VARCHAR(128)"),
                new ColumnDefinition("internal_user_name", "internal_user_name VARCHAR(128)"),
                new ColumnDefinition("organization_id", "organization_id VARCHAR(128)"),
                new ColumnDefinition("organization_name", "organization_name VARCHAR(128)"),
                new ColumnDefinition("data_scope", "data_scope VARCHAR(32)"),
                new ColumnDefinition("permissions", "permissions TEXT"),
                new ColumnDefinition("enabled", "enabled TINYINT DEFAULT 1"),
                new ColumnDefinition("remark", "remark VARCHAR(512)"),
                new ColumnDefinition("deleted", "deleted TINYINT DEFAULT 0"),
                new ColumnDefinition("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP"),
                new ColumnDefinition("update_time", "update_time DATETIME DEFAULT CURRENT_TIMESTAMP")
        );
    }

    private static List<ColumnDefinition> messageLogColumns() {
        return List.of(
                new ColumnDefinition("message_id", "message_id VARCHAR(128)"),
                new ColumnDefinition("chat_id", "chat_id VARCHAR(128)"),
                new ColumnDefinition("tenant_key", "tenant_key VARCHAR(128)"),
                new ColumnDefinition("sender_user_id", "sender_user_id VARCHAR(128)"),
                new ColumnDefinition("sender_open_id", "sender_open_id VARCHAR(128)"),
                new ColumnDefinition("internal_user_id", "internal_user_id BIGINT"),
                new ColumnDefinition("internal_account", "internal_account VARCHAR(128)"),
                new ColumnDefinition("command_text", "command_text TEXT"),
                new ColumnDefinition("reply_type", "reply_type VARCHAR(32)"),
                new ColumnDefinition("stage", "stage VARCHAR(32)"),
                new ColumnDefinition("success", "success TINYINT DEFAULT 1"),
                new ColumnDefinition("error_code", "error_code INT"),
                new ColumnDefinition("error_message", "error_message VARCHAR(1024)"),
                new ColumnDefinition("cost_millis", "cost_millis BIGINT"),
                new ColumnDefinition("raw_payload", "raw_payload LONGTEXT"),
                new ColumnDefinition("reply_payload", "reply_payload LONGTEXT"),
                new ColumnDefinition("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP")
        );
    }

    private static List<ColumnDefinition> cliCallLogColumns() {
        return List.of(
                new ColumnDefinition("business_type", "business_type VARCHAR(64)"),
                new ColumnDefinition("method", "method VARCHAR(16)"),
                new ColumnDefinition("api_path", "api_path VARCHAR(512)"),
                new ColumnDefinition("command_summary", "command_summary VARCHAR(1024)"),
                new ColumnDefinition("params_json", "params_json LONGTEXT"),
                new ColumnDefinition("data_json", "data_json LONGTEXT"),
                new ColumnDefinition("success", "success TINYINT DEFAULT 1"),
                new ColumnDefinition("exit_code", "exit_code INT"),
                new ColumnDefinition("attempts", "attempts INT"),
                new ColumnDefinition("cost_millis", "cost_millis BIGINT"),
                new ColumnDefinition("stdout", "stdout LONGTEXT"),
                new ColumnDefinition("stderr", "stderr LONGTEXT"),
                new ColumnDefinition("error_message", "error_message VARCHAR(1024)"),
                new ColumnDefinition("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP")
        );
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet tables = connection.getMetaData()
                .getTables(connection.getCatalog(), null, tableName, TABLE_TYPES)) {
            return tables.next();
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws Exception {
        try (ResultSet columns = connection.getMetaData()
                .getColumns(connection.getCatalog(), null, tableName, columnName)) {
            return columns.next();
        }
    }

    private record ColumnDefinition(String name, String definition) {
    }
}
