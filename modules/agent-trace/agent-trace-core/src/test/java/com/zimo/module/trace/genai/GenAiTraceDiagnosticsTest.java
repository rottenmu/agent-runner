package com.zimo.module.trace.genai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * GenAiTraceDiagnostics 单测：观测通道自检。
 *
 * <p>锁定的行为：通道"静默不产出"时必须能在 {@code health()} 里被看见。</p>
 */
class GenAiTraceDiagnosticsTest {

    @BeforeEach
    void reset() {
        GenAiTraceDiagnostics.reset();
    }

    @Test
    void reportsHealthyWhenNothingHappened() {
        Map<String, Object> h = GenAiTraceDiagnostics.health();
        assertThat(h).containsEntry("healthy", true);
        assertThat((List<?>) h.get("warnings")).isEmpty();
    }

    @Test
    void flagsWhenBegunButNothingExported() {
        // 复现历史 bug 现场：链路开始了、步骤也记了，但导出为 0
        GenAiTraceDiagnostics.recordBegin("t1");
        GenAiTraceDiagnostics.recordStep();

        Map<String, Object> h = GenAiTraceDiagnostics.health();
        assertThat(h).containsEntry("beginCount", 1L).containsEntry("exportCount", 0L);
        assertThat(h).containsEntry("healthy", false);
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) h.get("warnings");
        assertThat(warnings).anyMatch(w -> w.contains("导出 0 次"));
    }

    @Test
    void flagsUnbalancedBeginEnd() {
        // 嵌套链路未用 endFor 精确收尾 → 差值为泄漏的活跃链路
        GenAiTraceDiagnostics.recordBegin("outer");
        GenAiTraceDiagnostics.recordBegin("inner");
        GenAiTraceDiagnostics.recordEnd("inner");

        Map<String, Object> h = GenAiTraceDiagnostics.health();
        assertThat(h).containsEntry("beginCount", 2L).containsEntry("endCount", 1L);
        assertThat(h).containsEntry("activeTraceCount", 1);
        assertThat(GenAiTraceDiagnostics.activeTraceIds()).containsExactly("outer");
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) h.get("warnings");
        assertThat(warnings).anyMatch(w -> w.contains("不配平"));
    }

    @Test
    void flagsDroppedSteps() {
        GenAiTraceDiagnostics.recordDroppedStep();
        Map<String, Object> h = GenAiTraceDiagnostics.health();
        assertThat(h).containsEntry("droppedStepCount", 1L);
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) h.get("warnings");
        assertThat(warnings).anyMatch(w -> w.contains("丢弃"));
    }

    @Test
    void healthyAfterBalancedExport() {
        GenAiTraceDiagnostics.recordBegin("t1");
        GenAiTraceDiagnostics.recordStep();
        GenAiTraceDiagnostics.recordEnd("t1");
        GenAiTraceDiagnostics.recordExport(2);

        Map<String, Object> h = GenAiTraceDiagnostics.health();
        assertThat(h).containsEntry("healthy", true)
                .containsEntry("exportCount", 1L)
                .containsEntry("exportedSpanCount", 2L)
                .containsEntry("activeTraceCount", 0);
        assertThat(h.get("lastExportAt")).isNotNull();
    }

    @Test
    void counterAccumulates() {
        List<GenAiSpan> spans = new ArrayList<>();
        GenAiTraceDiagnostics.recordExport(3);
        GenAiTraceDiagnostics.recordExport(4);
        assertThat(GenAiTraceDiagnostics.health()).containsEntry("exportedSpanCount", 7L);
        assertThat(spans).isEmpty();
    }
}
