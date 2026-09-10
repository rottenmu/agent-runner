package com.zimo.framework.ai.sandbox;

import java.util.Set;

/**
 * 沙箱文件系统抽象（对应 dsh A7：文件系统与子进程共享执行世界）。
 *
 * <p>文件操作与命令执行共享同一沙箱边界：所有路径均相对
 * {@link #workdir()} 工作区根解析，越界（绝对路径 / {@code ..} 逃逸）拒绝。
 * 本地实现直接操作文件系统；远程实现转发到远程沙箱服务的文件端点。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-25
 */
public interface SandboxFileSystem {

    /** 工作区根目录（所有路径相对此解析）。 */
    String workdir();

    /** 可读写文件扩展名白名单（小写）；空=不限制。 */
    Set<String> allowedExtensions();

    /** 执行一次文件操作。 */
    SandboxFileResult execute(SandboxFileOp op);

    /** 校验路径是否安全（非绝对、无 .. 逃逸）。 */
    static String normalizeSafe(String workdir, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("禁止绝对路径: " + path);
        }
        java.util.StringTokenizer tokenizer = new java.util.StringTokenizer(normalized, "/");
        StringBuilder safe = new StringBuilder();
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if ("..".equals(token)) {
                throw new IllegalArgumentException("禁止路径越界: " + path);
            }
            if (".".equals(token)) {
                continue;
            }
            if (safe.length() > 0) {
                safe.append('/');
            }
            safe.append(token);
        }
        if (safe.length() == 0) {
            throw new IllegalArgumentException("路径不能只包含分隔符: " + path);
        }
        return safe.toString();
    }
}