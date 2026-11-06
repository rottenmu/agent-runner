package com.zimo.module.ai.observ;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.zimo.module.ai.schema.DdlColumns;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 可观测建表一致性测试：{@code observ_session_event} 的 MySQL 自举 DDL 与版本脚本对齐。
 *
 * <p>这张表的自举语句曾经是纯 SQLite 写法（{@code INTEGER PRIMARY KEY AUTOINCREMENT} +
 * {@code CLOB}），在 MySQL 上会抛语法错，而建表在 {@code @PostConstruct} 里，
 * 所以症状是「切库后应用起不来」。本测试把三方（Java MySQL DDL、版本脚本、禁用关键字）都锁住。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
class ObservMysqlDdlTest {

    /** 版本脚本位置。 */
    private static final String SCRIPT_PATH = "/db/module-agent-harness/V1.0.1__observ_session_event.sql";

    /** MySQL 自举 DDL 不能含 SQLite 专用关键字。 */
    @Test
    void mysqlDdlHasNoSqliteKeywords() {
        assertFalse(SessionEventLogServiceImpl.MYSQL_DDL.contains("AUTOINCREMENT"),
                "MySQL DDL 出现 AUTOINCREMENT: " + SessionEventLogServiceImpl.MYSQL_DDL);
        assertFalse(SessionEventLogServiceImpl.MYSQL_DDL.contains("CLOB"),
                "MySQL DDL 出现 CLOB: " + SessionEventLogServiceImpl.MYSQL_DDL);
    }

    /** 自举 DDL 与版本脚本逐列一致。 */
    @Test
    void columnsMatchScript() throws IOException {
        String script = DdlColumns.readResource(SCRIPT_PATH);
        String block = DdlColumns.tableBlock(script, "observ_session_event");
        assertFalse(block.isBlank(), "版本脚本里找不到 observ_session_event");

        List<String> fromScript = DdlColumns.of(block);
        List<String> fromJava = DdlColumns.of(SessionEventLogServiceImpl.MYSQL_DDL);
        assertFalse(fromScript.isEmpty(), "脚本解析出 0 列");
        assertEquals(fromScript, fromJava,
                "observ_session_event 自举 DDL 与版本脚本不一致\n脚本: " + fromScript
                        + "\nJava: " + fromJava);
    }
}
