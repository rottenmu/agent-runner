package com.zimo.module.ai.schema;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 建表方言支持：方言判定 + 幂等建索引。
 *
 * <p>所属模块：agent-harness 启动期建表。工程本地默认数据源是 SQLite，生产规范要求 MySQL，
 * 两种方言在 DDL 上存在**不兼容**关键字，不能写一份"两边都勉强能跑"的 SQL：</p>
 *
 * <table border="1">
 *   <caption>方言差异</caption>
 *   <tr><th>用途</th><th>SQLite</th><th>MySQL</th></tr>
 *   <tr><td>自增主键</td><td>{@code INTEGER PRIMARY KEY AUTOINCREMENT}</td>
 *       <td>{@code BIGINT AUTO_INCREMENT PRIMARY KEY}</td></tr>
 *   <tr><td>大文本</td><td>{@code TEXT} / {@code CLOB}</td><td>{@code TEXT} / {@code LONGTEXT}</td></tr>
 *   <tr><td>幂等建索引</td><td>{@code CREATE INDEX IF NOT EXISTS}</td>
 *       <td><b>不支持 IF NOT EXISTS</b>（语法错误 1064），只能先查元数据再建</td></tr>
 * </table>
 *
 * <p>索引那一行是本类存在的直接原因：把 {@code CREATE INDEX IF NOT EXISTS} 发到 MySQL 不是
 * "被忽略"，而是**直接抛语法错**，建表初始化在 {@code @PostConstruct} 里，于是整个应用启动失败。
 * 这类错误在 SQLite 环境下永远看不到。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public final class SchemaDdl {

    private static final Logger log = LoggerFactory.getLogger(SchemaDdl.class);

    /** MySQL 查询索引是否已存在的元数据语句（{@code DATABASE()} 取当前库，免写死库名）。 */
    private static final String MYSQL_INDEX_EXISTS_SQL = """
            SELECT COUNT(*) FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?""";

    /** 是否为 MySQL 方言。 */
    private final boolean mysql;

    /**
     * 直接指定方言（供单测构造，生产走 {@link #detect(JdbcTemplate)}）。
     *
     * @param mysql true 表示 MySQL 方言
     */
    public SchemaDdl(boolean mysql) {
        this.mysql = mysql;
    }

    /**
     * 按数据源连接元数据探测方言。
     *
     * @param jdbcTemplate JDBC 模板，不可为空
     * @return 方言支持对象；探测失败按 SQLite 处理（本地默认数据源）
     */
    public static SchemaDdl detect(JdbcTemplate jdbcTemplate) {
        try {
            DataSource dataSource = jdbcTemplate.getDataSource();
            if (dataSource == null) {
                return new SchemaDdl(false);
            }
            try (Connection connection = dataSource.getConnection()) {
                return new SchemaDdl(isMysqlProduct(connection.getMetaData().getDatabaseProductName()));
            }
        } catch (SQLException | RuntimeException e) {
            log.warn("[schema] 数据库方言探测失败，按 sqlite 处理: {}", e.getMessage());
            return new SchemaDdl(false);
        }
    }

    /**
     * 判断产品名是否 MySQL 系（兼容 MariaDB 等自称）。
     *
     * @param productName {@code DatabaseMetaData.getDatabaseProductName()} 返回值
     * @return 属于 MySQL 系返回 true
     */
    static boolean isMysqlProduct(String productName) {
        if (productName == null) {
            return false;
        }
        String lower = productName.toLowerCase();
        return lower.contains("mysql") || lower.contains("mariadb");
    }

    /** 是否为 MySQL 方言。 */
    public boolean mysql() {
        return mysql;
    }

    /**
     * 幂等建索引（两种方言语义一致：索引已存在时静默跳过）。
     *
     * @param jdbcTemplate JDBC 模板，不可为空
     * @param table 表名，不可为空
     * @param index 索引名，不可为空
     * @param columns 索引列（逗号分隔，不含括号），不可为空
     */
    public void ensureIndex(JdbcTemplate jdbcTemplate, String table, String index, String columns) {
        if (mysql) {
            if (indexExists(jdbcTemplate, table, index)) {
                log.debug("[schema] 索引已存在，跳过: {}", index);
                return;
            }
            jdbcTemplate.execute(createIndexSql(table, index, columns, true));
            return;
        }
        jdbcTemplate.execute(createIndexSql(table, index, columns, false));
    }

    /**
     * 拼装建索引语句（含方言差异），抽成静态纯函数便于单测锁定"MySQL 不得带 IF NOT EXISTS"。
     *
     * @param table 表名
     * @param index 索引名
     * @param columns 索引列
     * @param mysql 是否为 MySQL 方言
     * @return 建索引语句
     */
    static String createIndexSql(String table, String index, String columns, boolean mysql) {
        String prefix = mysql ? "CREATE INDEX " : "CREATE INDEX IF NOT EXISTS ";
        return prefix + index + " ON " + table + "(" + columns + ")";
    }

    /** 查 MySQL 元数据判断索引是否存在。 */
    private boolean indexExists(JdbcTemplate jdbcTemplate, String table, String index) {
        Integer count = jdbcTemplate.queryForObject(
                MYSQL_INDEX_EXISTS_SQL, Integer.class, table, index);
        return count != null && count > 0;
    }
}
