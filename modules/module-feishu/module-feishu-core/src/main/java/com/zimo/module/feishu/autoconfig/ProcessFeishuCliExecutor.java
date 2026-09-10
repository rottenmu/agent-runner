package com.zimo.module.feishu.autoconfig;

import com.fasterxml.jackson.databind.JsonNode;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.feishu.cli.FeishuCliCommandRequest;
import com.zimo.module.feishu.cli.FeishuCliCommandResult;
import com.zimo.module.feishu.cli.FeishuCliExecutor;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ProcessFeishuCliExecutor implements FeishuCliExecutor {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FeishuCliProperties properties;
    private final ProcessRunner runner;

    public ProcessFeishuCliExecutor(FeishuCliProperties properties) {
        this(properties, new DefaultProcessRunner());
    }

    ProcessFeishuCliExecutor(FeishuCliProperties properties, ProcessRunner runner) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.runner = Objects.requireNonNull(runner, "runner must not be null");
    }

    @Override
    public FeishuCliCommandResult execute(FeishuCliCommandRequest request) {
        long started = System.nanoTime();
        List<String> command = buildCommand(request);
        if (command.isEmpty()) {
            return FeishuCliCommandResult.failure(
                    -1,
                    "",
                    "",
                    "feishu cli wrapper-path must not be blank when mode is WRAPPER",
                    elapsedMillis(started),
                    1
            );
        }

        ProcessOutput output;
        try {
            output = runner.run(command, properties.getWorkingDirectory(), resolveTimeout(request));
        } catch (RuntimeException e) {
            return FeishuCliCommandResult.failure(-1, "", "", e.getMessage(), elapsedMillis(started), 1);
        }

        long costMillis = elapsedMillis(started);
        if (output.timedOut()) {
            return FeishuCliCommandResult.failure(-1, output.stdout(), output.stderr(), "feishu cli command timeout", costMillis, 1);
        }
        if (output.exitCode() != 0) {
            return FeishuCliCommandResult.failure(output.exitCode(), output.stdout(), output.stderr(), output.stderr(), costMillis, 1);
        }
        return FeishuCliCommandResult.success(output.stdout(), parseJson(output.stdout()), costMillis, 1);
    }

    private List<String> buildCommand(FeishuCliCommandRequest request) {
        List<String> command = new ArrayList<>();
        if (properties.getMode() == FeishuCliProperties.Mode.WRAPPER) {
            if (!StringUtils.hasText(properties.getWrapperPath())) {
                return List.of();
            }
            command.add(properties.getWrapperPath());
        } else {
            command.add(StringUtils.hasText(properties.getCommand()) ? properties.getCommand() : "npx");
            command.add(StringUtils.hasText(properties.getPackageName()) ? properties.getPackageName() : "@larksuite/cli@latest");
        }
        command.add("api");
        command.add(request.getMethod());
        command.add(request.getApiPath());
        command.add("--format");
        command.add("json");
        appendJsonOption(command, "--params", request.getParams());
        appendJsonOption(command, "--data", request.getData());
        return command;
    }

    private static void appendJsonOption(List<String> command, String option, Map<String, Object> value) {
        if (cn.hutool.core.map.MapUtil.isEmpty(value)) {
            return;
        }
        try {
            command.add(option);
            command.add(OBJECT_MAPPER.writeValueAsString(value));
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to serialize feishu cli option " + option, e);
        }
    }

    private Duration resolveTimeout(FeishuCliCommandRequest request) {
        if (request.getTimeout() != null) {
            return request.getTimeout();
        }
        return Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
    }

    private static JsonNode parseJson(String stdout) {
        if (!StringUtils.hasText(stdout)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(stdout);
        } catch (IOException e) {
            return null;
        }
    }

    private static long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    interface ProcessRunner {
        ProcessOutput run(List<String> command, String workingDirectory, Duration timeout);
    }

    public record ProcessOutput(int exitCode, String stdout, String stderr, boolean timedOut) {
    }

    private static class DefaultProcessRunner implements ProcessRunner {
        @Override
        public ProcessOutput run(List<String> command, String workingDirectory, Duration timeout) {
            try {
                ProcessBuilder builder = new ProcessBuilder(command);
                if (StringUtils.hasText(workingDirectory)) {
                    builder.directory(new File(workingDirectory));
                }
                Process process = builder.start();
                boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    return new ProcessOutput(-1, read(process.getInputStream().readAllBytes()),
                            read(process.getErrorStream().readAllBytes()), true);
                }
                return new ProcessOutput(process.exitValue(), read(process.getInputStream().readAllBytes()),
                        read(process.getErrorStream().readAllBytes()), false);
            } catch (IOException e) {
                throw new IllegalStateException("failed to run feishu cli command", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("feishu cli command interrupted", e);
            }
        }

        private static String read(byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
