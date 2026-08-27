package com.zimo.starter.ai.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 本地沙箱后端单测：执行/超时/拒绝/stdin/exitCode。
 */
class LocalSandboxBackendTest {

    private final LocalSandboxBackend sandbox = new LocalSandboxBackend(5);

    @Test
    void executesCommandAndCapturesOutput() {
        SandboxResult result = sandbox.execute(new SandboxCommand(
                List.of("python3", "-c", "print('hello local sandbox')")));
        assertThat(result.error()).isNull();
        assertThat(result.timedOut()).isFalse();
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("hello local sandbox");
    }

    @Test
    void capturesNonZeroExitCode() {
        SandboxResult result = sandbox.execute(new SandboxCommand(
                List.of("python3", "-c", "import sys; sys.exit(3)")));
        assertThat(result.error()).isNull();
        assertThat(result.exitCode()).isEqualTo(3);
    }

    @Test
    void passesStdinToProcess() {
        SandboxResult result = sandbox.execute(new SandboxCommand(
                List.of("python3", "-c", "import sys; print(sys.stdin.read().strip().upper())"),
                null, "abc", 5, "."));
        assertThat(result.error()).isNull();
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("ABC");
    }

    @Test
    void rejectsEmptyCommand() {
        SandboxResult result = sandbox.execute(new SandboxCommand(List.of()));
        assertThat(result.error()).isNotBlank();
        assertThat(result.succeeded()).isFalse();
    }

    @Test
    void timeoutForcesTermination() {
        SandboxResult result = sandbox.execute(new SandboxCommand(
                List.of("python3", "-c", "import time; time.sleep(30)"), null, null, 1, "."));
        assertThat(result.timedOut()).isTrue();
    }

    @Test
    void supportsEnvInjection() {
        SandboxResult result = sandbox.execute(new SandboxCommand(
                List.of("python3", "-c", "import os; print(os.environ.get('SANDBOX_TEST', 'miss'))"),
                Map.of("SANDBOX_TEST", "hit"), null, 5, "."));
        assertThat(result.error()).isNull();
        assertThat(result.stdout()).contains("hit");
    }
}