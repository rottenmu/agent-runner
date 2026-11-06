package com.zimo.module.ai.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 建表方言支持单测：锁死"MySQL 不得发 IF NOT EXISTS"这条硬约束。
 *
 * <p>为什么值得单独测：{@code CREATE INDEX IF NOT EXISTS} 在 SQLite 上是合法且幂等的，
 * 在 MySQL 上是**语法错**。而建表初始化跑在 {@code @PostConstruct} 里，一旦发错语句，
 * 应用会直接启动失败——本地用 SQLite 开发时永远看不到，只有切生产的第一次启动才炸。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
class SchemaDdlTest {

    /** 反向断言：MySQL 分支的建索引语句**不能**带 IF NOT EXISTS。 */
    @Test
    void mysqlIndexSqlMustNotUseIfNotExists() {
        String sql = SchemaDdl.createIndexSql("t", "idx_t", "a, b", true);
        assertEquals("CREATE INDEX idx_t ON t(a, b)", sql);
        assertFalse(sql.contains("IF NOT EXISTS"),
                "MySQL 不支持 CREATE INDEX IF NOT EXISTS（语法错 1064），实际语句: " + sql);
    }

    /** SQLite 分支必须带 IF NOT EXISTS，否则重复启动会报索引已存在。 */
    @Test
    void sqliteIndexSqlMustUseIfNotExists() {
        String sql = SchemaDdl.createIndexSql("t", "idx_t", "a, b", false);
        assertEquals("CREATE INDEX IF NOT EXISTS idx_t ON t(a, b)", sql);
        assertTrue(sql.contains("IF NOT EXISTS"), "SQLite 需要幂等索引语句，实际语句: " + sql);
    }

    /** MySQL：索引不存在时才建，且发出的语句不能带 IF NOT EXISTS。 */
    @Test
    void mysqlEnsureIndexCreatesWhenAbsent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
                .thenReturn(0);

        new SchemaDdl(true).ensureIndex(jdbc, "t", "idx_t", "a");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).execute(captor.capture());
        assertEquals("CREATE INDEX idx_t ON t(a)", captor.getValue());
    }

    /** MySQL：索引已存在时不发 CREATE INDEX（重复执行不报错）。 */
    @Test
    void mysqlEnsureIndexSkipsWhenPresent() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
                .thenReturn(1);

        new SchemaDdl(true).ensureIndex(jdbc, "t", "idx_t", "a");

        verify(jdbc, never()).execute(anyString());
    }

    /** MySQL：元数据查不到行（null 计数）时按"不存在"处理，不能静默跳过建索引。 */
    @Test
    void mysqlEnsureIndexCreatesWhenCountIsNull() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
                .thenReturn(null);

        new SchemaDdl(true).ensureIndex(jdbc, "t", "idx_t", "a");

        verify(jdbc, times(1)).execute("CREATE INDEX idx_t ON t(a)");
    }

    /** SQLite：不走元数据查询，直接发 IF NOT EXISTS 语句。 */
    @Test
    void sqliteEnsureIndexDoesNotQueryMetadata() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        new SchemaDdl(false).ensureIndex(jdbc, "t", "idx_t", "a");

        verify(jdbc).execute("CREATE INDEX IF NOT EXISTS idx_t ON t(a)");
        verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), anyString(), anyString());
    }

    /** 方言判定：MySQL / MariaDB 归一，其余（含 null）归 SQLite。 */
    @Test
    void productNameDetection() {
        assertTrue(SchemaDdl.isMysqlProduct("MySQL"));
        assertTrue(SchemaDdl.isMysqlProduct("MariaDB"));
        assertFalse(SchemaDdl.isMysqlProduct("SQLite"));
        assertFalse(SchemaDdl.isMysqlProduct("H2"));
        assertFalse(SchemaDdl.isMysqlProduct(null));
    }

    /** 探测不到数据源时回落 SQLite（本地默认），不能抛异常打断启动。 */
    @Test
    void detectFallsBackToSqliteWithoutDataSource() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.getDataSource()).thenReturn(null);
        assertFalse(SchemaDdl.detect(jdbc).mysql());
    }
}
