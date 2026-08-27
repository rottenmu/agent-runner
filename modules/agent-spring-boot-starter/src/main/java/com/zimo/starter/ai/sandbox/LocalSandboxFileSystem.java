package com.zimo.starter.ai.sandbox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地沙箱文件系统：在工作区根内执行文件操作（dsh A7 共享执行世界）。
 *
 * <p>所有路径经 {@link SandboxFileSystem#normalizeSafe} 校验后相对工作区根解析，
 * 双重防护：语义层禁止绝对路径与 {@code ..}，物理层 realpath 复核不越出根目录。
 * 写入白名单：仅允许 {@link #allowedExtensions()} 声明的扩展名（空=不限制）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-25
 */
public class LocalSandboxFileSystem implements SandboxFileSystem {

    private static final Logger log = LoggerFactory.getLogger(LocalSandboxFileSystem.class);

    private final Path root;
    private final Set<String> allowedExtensions;

    public LocalSandboxFileSystem(String workdir) {
        this(workdir, Set.of());
    }

    public LocalSandboxFileSystem(String workdir, Set<String> allowedExtensions) {
        Path resolved = workdir == null || workdir.isBlank()
                ? Path.of("data/sandbox-files").toAbsolutePath()
                : Path.of(workdir).toAbsolutePath();
        try {
            Files.createDirectories(resolved);
        } catch (IOException e) {
            log.warn("沙箱工作区创建失败: {} {}", resolved, e.getMessage());
        }
        this.root = resolved.normalize();
        this.allowedExtensions = allowedExtensions == null ? Set.of() : allowedExtensions;
    }

    @Override
    public String workdir() {
        return root.toString();
    }

    @Override
    public Set<String> allowedExtensions() {
        return allowedExtensions;
    }

    @Override
    public SandboxFileResult execute(SandboxFileOp op) {
        if (op == null || op.op() == null) {
            return SandboxFileResult.failed("文件操作类型不能为空");
        }
        try {
            // 空路径 = 工作区根目录
            if (op.path() == null || op.path().isBlank()) {
                if (op.isDelete()) {
                    return SandboxFileResult.failed("禁止删除工作区根目录");
                }
                if (op.isList() || op.isExists()) {
                    Path rootTarget = root;
                    return switch (op.op()) {
                        case SandboxFileOp.OP_LIST -> list(rootTarget);
                        case SandboxFileOp.OP_EXISTS -> SandboxFileResult.okExists(Files.exists(rootTarget));
                        default -> SandboxFileResult.failed("不支持的文件操作: " + op.op());
                    };
                }
                return SandboxFileResult.failed("文件路径不能为空");
            }
            Path target = resolve(op.path());
            return switch (op.op()) {
                case SandboxFileOp.OP_READ -> read(target);
                case SandboxFileOp.OP_WRITE -> write(target, op.content());
                case SandboxFileOp.OP_LIST -> list(target);
                case SandboxFileOp.OP_DELETE -> delete(target);
                case SandboxFileOp.OP_EXISTS -> SandboxFileResult.okExists(Files.exists(target));
                default -> SandboxFileResult.failed("不支持的文件操作: " + op.op());
            };
        } catch (IllegalArgumentException e) {
            return SandboxFileResult.failed(e.getMessage());
        } catch (Exception e) {
            log.warn("沙箱文件操作失败: op={} err={}", op.op(), e.getMessage());
            return SandboxFileResult.failed("文件操作失败: " + safeMessage(e));
        }
    }

    /* ---------------- 操作实现 ---------------- */

    private SandboxFileResult read(Path target) throws IOException {
        if (!Files.exists(target)) {
            return SandboxFileResult.failed("文件不存在: " + root.relativize(target));
        }
        if (!Files.isRegularFile(target)) {
            return SandboxFileResult.failed("不是文件: " + root.relativize(target));
        }
        long size = Files.size(target);
        if (size > 1024 * 1024) {
            return SandboxFileResult.failed("文件超过 1MB 上限: " + root.relativize(target));
        }
        String content = Files.readString(target, StandardCharsets.UTF_8);
        return SandboxFileResult.ok(content);
    }

    private SandboxFileResult write(Path target, String content) throws IOException {
        String extension = extensionOf(target);
        if (!allowedExtensions.isEmpty() && !allowedExtensions.contains(extension)) {
            return SandboxFileResult.failed("扩展名不在白名单: ." + extension);
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] bytes = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 1024 * 1024) {
            return SandboxFileResult.failed("写入内容超过 1MB 上限");
        }
        Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return SandboxFileResult.okWrite();
    }

    private SandboxFileResult list(Path target) throws IOException {
        if (!Files.exists(target)) {
            return SandboxFileResult.failed("目录不存在: " + root.relativize(target));
        }
        if (!Files.isDirectory(target)) {
            return SandboxFileResult.failed("不是目录: " + root.relativize(target));
        }
        List<String> entries = new ArrayList<>();
        try (var stream = Files.list(target)) {
            stream.sorted().forEach(path -> entries.add(
                    Files.isDirectory(path) ? path.getFileName().toString() + "/"
                            : path.getFileName().toString()));
        }
        return SandboxFileResult.okList(entries);
    }

    private SandboxFileResult delete(Path target) throws IOException {
        if (!Files.exists(target)) {
            return SandboxFileResult.okDelete();
        }
        if (target.equals(root)) {
            return SandboxFileResult.failed("禁止删除工作区根目录");
        }
        Files.deleteIfExists(target);
        return SandboxFileResult.okDelete();
    }

    /* ---------------- 工具 ---------------- */

    /** 解析相对路径 → 工作区根内绝对路径（语义校验 + realpath 复核）。 */
    private Path resolve(String path) {
        String safe = SandboxFileSystem.normalizeSafe(root.toString(), path);
        Path candidate = root.resolve(safe).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("路径越出工作区: " + path);
        }
        // 已存在目标复核 realpath（防符号链接逃逸）；不存在则按父目录校验
        Path base = Files.exists(candidate) ? candidate : candidate.getParent();
        if (base != null && Files.exists(base)) {
            try {
                Path real = base.toRealPath();
                if (!real.startsWith(root.toRealPath())) {
                    throw new IllegalArgumentException("符号链接越出工作区: " + path);
                }
            } catch (IOException e) {
                // 解析失败时退回语义校验结果
            }
        }
        return candidate;
    }

    private String extensionOf(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase();
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return message == null || message.isBlank()
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}