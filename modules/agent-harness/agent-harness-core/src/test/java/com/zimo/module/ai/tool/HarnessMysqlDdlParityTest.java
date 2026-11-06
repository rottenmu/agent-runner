package com.zimo.module.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimo.module.ai.schema.DdlColumns;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * MySQL 建表一致性测试：Java 运行期自举的 DDL 与版本化迁移脚本**逐列**比对。
 *
 * <p>为什么需要它：同一份表结构有意存在两个副本——{@code ToolSchemaInitializer} 里的是
 * 每次启动都跑的幂等自举（本地 SQLite 也依赖它），{@code db/module-agent-harness} 下的
 * 版本脚本是给生产 DBA 审计执行的。两份都"能跑"，但一旦只改一边，症状是
 * 「本地跑得好好的，生产表缺列」，而且报错点离改动点很远（插入时报 unknown column）。</p>
 *
 * <p>被锁定的三件事：列名、列类型、列的增减。不锁注释与默认值——那属于风格，不值得让测试变脆。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
class HarnessMysqlDdlParityTest {

    /** 版本脚本在 classpath 中的位置。 */
    private static final String SCRIPT_PATH = "/db/module-agent-harness/V1.0.0__graph_runtime_tool_tables.sql";

    /** 工具目录表：自举 DDL 与脚本列定义必须一致。 */
    @Test
    void toolMetaColumnsMatchScript() throws IOException {
        assertSameColumns("ai_tool_meta", ToolSchemaInitializer.MYSQL_TOOL_META);
    }

    /** 编排审计表：自举 DDL 与脚本列定义必须一致。 */
    @Test
    void invokeLogColumnsMatchScript() throws IOException {
        assertSameColumns("ai_graph_tool_invoke_log", ToolSchemaInitializer.MYSQL_INVOKE_LOG);
    }

    /** 自举 DDL 不得残留 SQLite 专用关键字（AUTOINCREMENT / CLOB 在 MySQL 上都是硬错误）。 */
    @Test
    void bootstrapDdlContainsNoSqliteOnlyKeywords() {
        for (String ddl : List.of(ToolSchemaInitializer.MYSQL_TOOL_META,
                ToolSchemaInitializer.MYSQL_INVOKE_LOG)) {
            assertFalse(ddl.contains("AUTOINCREMENT"),
                    "MySQL DDL 出现 AUTOINCREMENT（应为 AUTO_INCREMENT）: " + ddl);
            assertFalse(ddl.contains("CLOB"), "MySQL DDL 出现 CLOB（应为 TEXT）: " + ddl);
            assertTrue(ddl.contains("AUTO_INCREMENT"), "MySQL 自增主键必须用 AUTO_INCREMENT: " + ddl);
        }
    }

    /** 逐列比对，失败时把两侧完整列清单打出来（只看 diff 往往不够定位）。 */
    private void assertSameColumns(String table, String bootstrapDdl) throws IOException {
        String script = DdlColumns.readResource(SCRIPT_PATH);
        String block = DdlColumns.tableBlock(script, table);
        assertFalse(block.isBlank(), "版本脚本里找不到表定义: " + table);

        List<String> fromScript = DdlColumns.of(block);
        List<String> fromJava = DdlColumns.of(bootstrapDdl);
        assertFalse(fromScript.isEmpty(), "脚本表定义解析出 0 列: " + table);
        assertEquals(fromScript, fromJava,
                "表 " + table + " 的自举 DDL 与版本脚本不一致\n脚本: " + fromScript + "\nJava: " + fromJava);
    }
}
