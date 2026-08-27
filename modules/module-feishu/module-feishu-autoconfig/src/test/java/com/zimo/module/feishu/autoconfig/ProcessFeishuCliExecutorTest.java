package com.zimo.module.feishu.autoconfig;

import com.zimo.module.feishu.cli.FeishuCliCommandRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessFeishuCliExecutorTest {

    @Test
    void buildsNpxApiCommandWithParamsAndData() {
        FeishuCliProperties properties = new FeishuCliProperties();
        CapturingProcessRunner runner = new CapturingProcessRunner(0, "{\"code\":0}", "");
        ProcessFeishuCliExecutor executor = new ProcessFeishuCliExecutor(properties, runner);

        executor.execute(FeishuCliCommandRequest
                .api("bitable", "GET", "/open-apis/bitable/v1/apps/app/tables/table/records")
                .withParam("page_size", 20)
                .withData("fields", Map.of("名称", "样件A")));

        assertThat(runner.command).containsExactly(
                "npx", "@larksuite/cli@latest", "api", "GET",
                "/open-apis/bitable/v1/apps/app/tables/table/records",
                "--format", "json",
                "--params", "{\"page_size\":20}",
                "--data", "{\"fields\":{\"名称\":\"样件A\"}}"
        );
    }

    @Test
    void buildsWrapperCommandWhenWrapperModeIsConfigured() {
        FeishuCliProperties properties = new FeishuCliProperties();
        properties.setMode(FeishuCliProperties.Mode.WRAPPER);
        properties.setWrapperPath("D:/tools/my-lark-cli.exe");
        CapturingProcessRunner runner = new CapturingProcessRunner(0, "{\"code\":0}", "");
        ProcessFeishuCliExecutor executor = new ProcessFeishuCliExecutor(properties, runner);

        executor.execute(FeishuCliCommandRequest.api("document", "POST", "/open-apis/docx/v1/documents")
                .withData("title", "项目纪要"));

        assertThat(runner.command).containsExactly(
                "D:/tools/my-lark-cli.exe", "api", "POST",
                "/open-apis/docx/v1/documents",
                "--format", "json",
                "--data", "{\"title\":\"项目纪要\"}"
        );
    }

    private static class CapturingProcessRunner implements ProcessFeishuCliExecutor.ProcessRunner {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private List<String> command;

        private CapturingProcessRunner(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        @Override
        public ProcessFeishuCliExecutor.ProcessOutput run(
                List<String> command,
                String workingDirectory,
                Duration timeout) {
            this.command = command;
            return new ProcessFeishuCliExecutor.ProcessOutput(exitCode, stdout, stderr, false);
        }
    }
}
