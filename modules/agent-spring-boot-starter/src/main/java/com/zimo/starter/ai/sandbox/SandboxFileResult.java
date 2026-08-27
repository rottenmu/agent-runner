package com.zimo.starter.ai.sandbox;

import java.util.List;

/**
 * 沙箱文件操作结果（不可变）。
 *
 * @param success 是否成功
 * @param content 读取/写入的内容（read/write 用，可空）
 * @param entries 目录条目名（list 用，可空）
 * @param exists  是否存在（exists 用）
 * @param error   错误信息（失败时非空）
 */
public record SandboxFileResult(
        boolean success,
        String content,
        List<String> entries,
        boolean exists,
        String error) {

    public static SandboxFileResult ok(String content) {
        return new SandboxFileResult(true, content, null, true, null);
    }

    public static SandboxFileResult okList(List<String> entries) {
        return new SandboxFileResult(true, null,
                entries == null ? List.of() : List.copyOf(entries), true, null);
    }

    public static SandboxFileResult okExists(boolean exists) {
        return new SandboxFileResult(true, null, null, exists, null);
    }

    public static SandboxFileResult okWrite() {
        return new SandboxFileResult(true, null, null, true, null);
    }

    public static SandboxFileResult okDelete() {
        return new SandboxFileResult(true, null, null, false, null);
    }

    public static SandboxFileResult failed(String error) {
        return new SandboxFileResult(false, null, null, false, error);
    }
}