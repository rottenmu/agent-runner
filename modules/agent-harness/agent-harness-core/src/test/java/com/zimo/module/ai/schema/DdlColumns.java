package com.zimo.module.ai.schema;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 测试辅助：把一段 DDL 解析成「列名 + 类型」二元组，用于比对 Java 自举常量与版本脚本。
 *
 * <p>只做一件事，故意写得很笨：逐行扫、跳过约束与索引行、取前两个 token。
 * 这样它不会"聪明到"把 {@code BIGINT AUTO_INCREMENT PRIMARY KEY} 和
 * {@code BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键'} 判成不同类型——两者都是 {@code BIGINT}。</p>
 *
 * <p>注意它<b>不校验</b>默认值、注释、索引；只拦「列名写错、类型写错、少列多列」这三类别扭。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public final class DdlColumns {

    /** 不以列定义开头的行（约束/索引）。 */
    private static final String[] NON_COLUMN_PREFIXES = {
        "PRIMARY KEY", "KEY ", "UNIQUE", "INDEX", "CONSTRAINT", ")", "CREATE TABLE", "--"
    };

    private DdlColumns() {
    }

    /**
     * 解析 DDL 中的列定义。
     *
     * @param ddl 单表建表语句（可含 {@code CREATE TABLE ... ( ... )} 之外的内容）
     * @return 按出现顺序排列的 {@code "列名 类型"} 列表
     */
    public static List<String> of(String ddl) {
        List<String> columns = new ArrayList<>();
        boolean inside = false;
        for (String raw : ddl.split("\n")) {
            String line = raw.trim();
            if (!inside) {
                inside = line.toUpperCase(Locale.ROOT).startsWith("CREATE TABLE");
                continue;
            }
            if (line.startsWith(")")) {
                break;
            }
            if (line.isEmpty() || isNonColumn(line)) {
                continue;
            }
            String[] tokens = line.split("\\s+");
            if (tokens.length < 2) {
                continue;
            }
            // 类型 token 可能带行尾逗号（"name VARCHAR(128),"），剥掉后再比对
            String type = tokens[1].toUpperCase(Locale.ROOT).replace(",", "");
            columns.add(tokens[0].toLowerCase(Locale.ROOT) + " " + type);
        }
        return columns;
    }

    /** 从多表脚本中截取单表块（找不到返回空串，让断言以"空 vs 非空"的形式暴露）。 */
    public static String tableBlock(String script, String table) {
        String marker = "CREATE TABLE IF NOT EXISTS " + table;
        int start = script.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int end = script.indexOf("\n)", start);
        return end < 0 ? script.substring(start) : script.substring(start, end + 2);
    }

    /** 读取 classpath 下的 SQL 资源。 */
    public static String readResource(String path) throws IOException {
        try (InputStream in = DdlColumns.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("classpath 资源不存在: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 是否约束/索引/注释行。 */
    private static boolean isNonColumn(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        for (String prefix : NON_COLUMN_PREFIXES) {
            if (upper.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
