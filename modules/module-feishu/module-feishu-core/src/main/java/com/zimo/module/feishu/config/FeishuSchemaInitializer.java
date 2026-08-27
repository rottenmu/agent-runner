package com.zimo.module.feishu.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Order(Ordered.HIGHEST_PRECEDENCE + 60)
public class FeishuSchemaInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(FeishuSchemaInitializer.class);
    private static final String TABLE_NAME = "ps_feishu_config";
    private static final String[] TABLE_TYPES = {"TABLE"};

    private final DataSource dataSource;

    public FeishuSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            if (!tableExists(connection)) {
                statement.execute(createTableSql());
                log.info("Feishu schema table initialized: {}", TABLE_NAME);
                return;
            }
            for (ColumnDefinition column : agentColumns()) {
                if (!columnExists(connection, column.name())) {
                    statement.execute("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + column.definition());
                    log.info("Feishu schema column added: {}.{}", TABLE_NAME, column.name());
                }
            }
        }
        log.info("Feishu schema checked");
    }

    public static String createTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS ps_feishu_config (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
                    config_name VARCHAR(128) NOT NULL COMMENT '配置名称',
                    app_id VARCHAR(128) NOT NULL COMMENT '飞书应用编号',
                    app_secret VARCHAR(256) COMMENT '飞书应用密钥',
                    verification_token VARCHAR(256) COMMENT '事件校验令牌',
                    encrypt_key VARCHAR(256) COMMENT '事件加密密钥',
                    enabled TINYINT DEFAULT 0 COMMENT '启用状态',
                    tenant_key VARCHAR(128) COMMENT '租户标识',
                    tenant_name VARCHAR(128) COMMENT '租户名称',
                    credential_status VARCHAR(32) COMMENT '凭证状态',
                    last_validate_time DATETIME COMMENT '最后验证时间',
                    last_refresh_time DATETIME COMMENT '最后刷新时间',
                    scan_state VARCHAR(32) COMMENT '扫码状态',
                    scan_ticket VARCHAR(128) COMMENT '扫码票据',
                    permission_scopes TEXT COMMENT '权限范围',
                    event_subscriptions TEXT COMMENT '事件订阅',
                    remark VARCHAR(512) COMMENT '备注',
                    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    INDEX idx_ps_feishu_config_app_id (app_id),
                    INDEX idx_ps_feishu_config_enabled (enabled),
                    INDEX idx_ps_feishu_config_tenant_key (tenant_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='飞书应用配置表'
                """;
    }

    private static List<ColumnDefinition> agentColumns() {
        return List.of(
                new ColumnDefinition("tenant_key", "tenant_key VARCHAR(128) COMMENT '租户标识'"),
                new ColumnDefinition("tenant_name", "tenant_name VARCHAR(128) COMMENT '租户名称'"),
                new ColumnDefinition("credential_status", "credential_status VARCHAR(32) COMMENT '凭证状态'"),
                new ColumnDefinition("last_validate_time", "last_validate_time DATETIME COMMENT '最后验证时间'"),
                new ColumnDefinition("last_refresh_time", "last_refresh_time DATETIME COMMENT '最后刷新时间'"),
                new ColumnDefinition("scan_state", "scan_state VARCHAR(32) COMMENT '扫码状态'"),
                new ColumnDefinition("scan_ticket", "scan_ticket VARCHAR(128) COMMENT '扫码票据'"),
                new ColumnDefinition("permission_scopes", "permission_scopes TEXT COMMENT '权限范围'"),
                new ColumnDefinition("event_subscriptions", "event_subscriptions TEXT COMMENT '事件订阅'")
        );
    }

    private boolean tableExists(Connection connection) throws Exception {
        try (ResultSet tables = connection.getMetaData()
                .getTables(connection.getCatalog(), null, TABLE_NAME, TABLE_TYPES)) {
            return tables.next();
        }
    }

    private boolean columnExists(Connection connection, String columnName) throws Exception {
        try (ResultSet columns = connection.getMetaData()
                .getColumns(connection.getCatalog(), null, TABLE_NAME, columnName)) {
            return columns.next();
        }
    }

    private record ColumnDefinition(String name, String definition) {
    }
}
