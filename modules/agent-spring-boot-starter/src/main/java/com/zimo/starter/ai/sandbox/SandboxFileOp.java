package com.zimo.starter.ai.sandbox;

import java.util.List;
import java.util.Map;

/**
 * 沙箱文件操作请求（不可变）。
 *
 * <p>描述一次沙箱内文件系统操作：{@code op} 为操作类型
 * （read/write/list/delete/exists），{@code path} 为相对工作区根的路径
 * （禁止绝对路径与 {@code ..} 越界），{@code content} 供 write 使用。</p>
 *
 * @param op      操作类型：read / write / list / delete / exists
 * @param path    相对工作区根的路径
 * @param content 写入内容（write 用，可空）
 */
public record SandboxFileOp(
        String op,
        String path,
        String content) {

    public static final String OP_READ = "read";
    public static final String OP_WRITE = "write";
    public static final String OP_LIST = "list";
    public static final String OP_DELETE = "delete";
    public static final String OP_EXISTS = "exists";

    private static final Map<String, String> OP_ALIASES = Map.of(
            "ls", OP_LIST,
            "mkdir", OP_WRITE,
            "cat", OP_READ);

    public SandboxFileOp {
        op = normalize(op);
    }

    public SandboxFileOp(String op, String path) {
        this(op, path, null);
    }

    public boolean isRead() {
        return OP_READ.equals(op);
    }

    public boolean isWrite() {
        return OP_WRITE.equals(op);
    }

    public boolean isList() {
        return OP_LIST.equals(op);
    }

    public boolean isDelete() {
        return OP_DELETE.equals(op);
    }

    public boolean isExists() {
        return OP_EXISTS.equals(op);
    }

    /** 规范化操作名（兼容别名，未知返回原值）。 */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String lower = value.trim().toLowerCase();
        return OP_ALIASES.getOrDefault(lower, lower);
    }
}