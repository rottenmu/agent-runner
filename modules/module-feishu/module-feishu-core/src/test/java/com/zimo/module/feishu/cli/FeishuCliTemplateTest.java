package com.zimo.module.feishu.cli;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuCliTemplateTest {

    @Test
    void retriesOnceAndRecordsFinalResult() {
        FlakyExecutor executor = new FlakyExecutor();
        CapturingLogService logService = new CapturingLogService();
        FeishuCliTemplate template = new FeishuCliTemplate(executor, logService, FeishuCliPolicy.allowAll(), 1);

        FeishuCliCommandResult result = template.execute(
                FeishuCliCommandRequest.api("task", "POST", "/open-apis/task/v2/tasks")
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAttempts()).isEqualTo(2);
        assertThat(executor.calls).isEqualTo(2);
        assertThat(logService.records).hasSize(1);
        assertThat(logService.records.get(0).getAttempts()).isEqualTo(2);
    }

    @Test
    void rejectsBusinessTypeOutsideJavaAllowListBeforeExecutorRuns() {
        FlakyExecutor executor = new FlakyExecutor();
        CapturingLogService logService = new CapturingLogService();
        FeishuCliTemplate template = new FeishuCliTemplate(
                executor,
                logService,
                FeishuCliPolicy.allowOnly(Set.of("document")),
                1
        );

        FeishuCliCommandResult result = template.execute(
                FeishuCliCommandRequest.api("bitable", "POST", "/open-apis/bitable/v1/apps/app/tables/table/records")
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("business type is not allowed");
        assertThat(executor.calls).isZero();
        assertThat(logService.records).hasSize(1);
    }

    private static class FlakyExecutor implements FeishuCliExecutor {
        private int calls;

        @Override
        public FeishuCliCommandResult execute(FeishuCliCommandRequest request) {
            calls++;
            if (calls == 1) {
                return FeishuCliCommandResult.failure(1, "", "failed", "failed", 10L, 1);
            }
            return FeishuCliCommandResult.success("{\"code\":0}", null, 20L, 2);
        }
    }

    private static class CapturingLogService extends FeishuCliCallLogService {
        private final List<FeishuCliCommandResult> records = new ArrayList<>();

        private CapturingLogService() {
            super(null, true);
        }

        @Override
        public void record(FeishuCliCommandRequest request, FeishuCliCommandResult result) {
            records.add(result);
        }
    }
}
