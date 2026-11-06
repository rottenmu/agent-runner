package com.zimo.module.ai.tool;

import com.zimo.module.ai.schema.SchemaDdl;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 工具市场建表初始化（幂等）。
 *
 * <p>所属模块：agent-harness 工具治理层。工程本地默认数据源是 SQLite，生产按规范切 MySQL，
 * 两种方言的 DDL 关键字不通用（{@code AUTOINCREMENT} vs {@code AUTO_INCREMENT}），
 * 因此这里按连接元数据的产品名分支执行，而不是写一份"两边都勉强能跑"的 SQL。</p>
 *
 * <p>索引语句同样走方言分支：MySQL <b>不支持</b> {@code CREATE INDEX IF NOT EXISTS}（报语法错 1064），
 * 必须先在 {@code information_schema.statistics} 查一次再建，详见 {@link SchemaDdl}。</p>
 *
 * <p>刻意<b>不建 (tenant_id, tool_id) 唯一索引</b>：工具采用逻辑删除，删掉之后同标识再注册
 * 会与残留行冲突，唯一性由注册时的查重保证。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class ToolSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(ToolSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造建表初始化器。
     *
     * @param jdbcTemplate JDBC 模板，不可为空
     */
    public ToolSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 启动时建表 + 建索引（均幂等，可重复执行）。 */
    @PostConstruct
    public void init() {
        SchemaDdl ddl = SchemaDdl.detect(jdbcTemplate);
        boolean mysql = ddl.mysql();
        jdbcTemplate.execute(mysql ? MYSQL_TOOL_META : SQLITE_TOOL_META);
        jdbcTemplate.execute(mysql ? MYSQL_INVOKE_LOG : SQLITE_INVOKE_LOG);
        ddl.ensureIndex(jdbcTemplate, "ai_tool_meta", "idx_tool_meta_tenant", "tenant_id, tool_id");
        ddl.ensureIndex(jdbcTemplate, "ai_graph_tool_invoke_log", "idx_graph_tool_log",
                "tool_id, invoke_ts");
        ddl.ensureIndex(jdbcTemplate, "ai_graph_tool_invoke_log", "idx_graph_tool_log_run",
                "run_id, node_id");
        log.info("[tool] 工具市场表初始化完成（dialect={}）", mysql ? "mysql" : "sqlite");
    }

    /** SQLite 方言：工具目录表。 */
    private static final String SQLITE_TOOL_META = """
            CREATE TABLE IF NOT EXISTS ai_tool_meta (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              tool_id TEXT NOT NULL,
              tenant_id TEXT NOT NULL DEFAULT 'u001',
              name TEXT,
              description TEXT,
              provider_type TEXT,
              provider_ref TEXT,
              version TEXT,
              schema_json TEXT,
              required_permission TEXT,
              enabled INTEGER DEFAULT 1,
              deleted INTEGER DEFAULT 0,
              create_time TEXT,
              update_time TEXT
            )""";

    /** SQLite 方言：调用审计表。 */
    private static final String SQLITE_INVOKE_LOG = """
            CREATE TABLE IF NOT EXISTS ai_graph_tool_invoke_log (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              tool_id TEXT NOT NULL,
              tenant_id TEXT,
              user_id TEXT,
              run_id INTEGER,
              node_id TEXT,
              args_json TEXT,
              result_json TEXT,
              status TEXT,
              error_msg TEXT,
              latency_ms INTEGER DEFAULT 0,
              invoke_ts INTEGER NOT NULL
            )""";

    /**
     * MySQL 方言：工具目录表。
     *
     * <p>字段必须与版本脚本
     * {@code db/module-agent-harness/V1.0.0__graph_runtime_tool_tables.sql} 一致，
     * 由 {@code HarnessMysqlDdlParityTest} 逐列比对锁定（两处都可改，但不许只改一处）。</p>
     */
    static final String MYSQL_TOOL_META = """
            CREATE TABLE IF NOT EXISTS ai_tool_meta (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              tool_id VARCHAR(128) NOT NULL,
              tenant_id VARCHAR(64) NOT NULL DEFAULT 'u001',
              name VARCHAR(128),
              description VARCHAR(1024),
              provider_type VARCHAR(32),
              provider_ref VARCHAR(255),
              version VARCHAR(32),
              schema_json TEXT,
              required_permission VARCHAR(128),
              enabled TINYINT DEFAULT 1,
              deleted TINYINT DEFAULT 0,
              create_time DATETIME,
              update_time DATETIME
            )""";

    /**
     * MySQL 方言：调用审计表。
     *
     * <p>字段必须与版本脚本
     * {@code db/module-agent-harness/V1.0.0__graph_runtime_tool_tables.sql} 一致，
     * 由 {@code HarnessMysqlDdlParityTest} 逐列比对锁定（两处都可改，但不许只改一处）。</p>
     */
    static final String MYSQL_INVOKE_LOG = """
            CREATE TABLE IF NOT EXISTS ai_graph_tool_invoke_log (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              tool_id VARCHAR(128) NOT NULL,
              tenant_id VARCHAR(64),
              user_id VARCHAR(64),
              run_id BIGINT,
              node_id VARCHAR(128),
              args_json TEXT,
              result_json TEXT,
              status VARCHAR(32),
              error_msg VARCHAR(1024),
              latency_ms BIGINT DEFAULT 0,
              invoke_ts BIGINT NOT NULL
            )""";
}
