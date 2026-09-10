package com.zimo.framework.ai.sandbox;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地执行沙箱（默认后端）：在本机启动子进程执行，不做远程隔离。
 *
 * <p>作为沙箱 SPI 的基线实现：{@link #execute(SandboxCommand)} 直接
 * {@link ProcessBuilder} 起进程（支持 stdin/env/workdir/超时强杀），
 * 保证现有命令工具行为零变化；需要进程隔离/远程执行时注册自定义后端替换。</p>
 */
public class LocalSandboxBackend implements SandboxBackend {

    private static final Logger log = LoggerFactory.getLogger(LocalSandboxBackend.class);

    /** 默认超时（秒），命令未指定时使用。 */
    private final int defaultTimeoutSeconds;

    public LocalSandboxBackend() {
        this(30);
    }

    public LocalSandboxBackend(int defaultTimeoutSeconds) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds > 0 ? defaultTimeoutSeconds : 30;
    }

    @Override
    public String name() {
        return "local";
    }

    @Override
    public List<String> wrapCommand(List<String> argv) {
        if (argv == null || argv.isEmpty()) {
            return List.of();
        }
        log.debug("沙箱(本地) 包装命令: {}", argv);
        return argv;
    }

    @Override
    public boolean isAllowed(String executable) {
        return executable != null && !executable.isBlank();
    }

    @Override
    public SandboxResult execute(SandboxCommand command) {
        List<String> argv = command.argv();
        if (argv == null || argv.isEmpty()) {
            return SandboxResult.failed("命令为空");
        }
        String executable = argv.get(0);
        if (!isAllowed(executable)) {
            return SandboxResult.failed("命令被沙箱拒绝: " + executable);
        }
        List<String> resolvedArgs = wrapCommand(argv);
        if (resolvedArgs == null || resolvedArgs.isEmpty()) {
            return SandboxResult.failed("命令包装后为空");
        }
        log.debug("沙箱(本地) 执行: {}", resolvedArgs);
        ProcessBuilder builder = new ProcessBuilder(resolvedArgs);
        Map<String, String> env = command.env();
        if (env != null) {
            builder.environment().putAll(env);
        }
        if (hasText(command.workdir())) {
            Path dir = Path.of(command.workdir());
            if (Files.isDirectory(dir)) {
                builder.directory(dir.toFile());
            }
        }
        builder.redirectErrorStream(false);
        return run(resolvedArgs, builder, command.stdin(),
                command.timeoutSeconds() > 0 ? command.timeoutSeconds() : defaultTimeoutSeconds);
    }

    private SandboxResult run(List<String> argv, ProcessBuilder builder, String stdin, int timeoutSeconds) {
        Process process = null;
        try {
            process = builder.start();
            if (stdin != null && !stdin.isEmpty()) {
                process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
            }
            process.getOutputStream().close();
            // 先判超时再读流：进程未结束时 readAllBytes 会阻塞至自然退出，超时检测失效。
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(3, TimeUnit.SECONDS);
                log.warn("沙箱(本地) 超时强制终止: {}", argv);
                return SandboxResult.timeout(truncate(readFully(process)), truncate(readErr(process)));
            }
            return SandboxResult.ok(process.exitValue(), truncate(readFully(process)), truncate(readErr(process)));
        } catch (IOException e) {
            log.warn("沙箱(本地) 启动失败: {}", argv, e);
            return SandboxResult.failed("命令启动失败: " + safeMessage(e));
        } catch (InterruptedException e) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            return SandboxResult.failed("执行被中断");
        }
    }

    private String readFully(Process process) throws IOException {
        try (var in = process.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String readErr(Process process) throws IOException {
        try (var err = process.getErrorStream()) {
            return new String(err.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String truncate(String text) {
        return text != null && text.length() > 100_000
                ? text.substring(0, 100_000) + "\n...(已截断)"
                : text;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return message == null || message.isBlank()
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}