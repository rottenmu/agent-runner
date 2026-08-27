package com.zimo.module.trace.genai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * GenAiTraceObserver 单测：span 树构建 / kind 映射 / 属性 / 导出。
 */
class GenAiTraceObserverTest {

    /** 收集型导出器（测试桩）。 */
    private static final class CollectingExporter implements GenAiSpanExporter {
        final List<GenAiSpan> spans = new ArrayList<>();

        @Override
        public void export(List<GenAiSpan> spans) {
            this.spans.addAll(spans);
        }
    }

    @Test
    void buildsSpanTreeWithRootAndSteps() {
        CollectingExporter exporter = new CollectingExporter();
        GenAiTraceObserver observer = new GenAiTraceObserver(exporter);

        observer.onBegin("trace-1", "session-a", "agent-1", "测试助手", "conversation", "console");
        observer.onStep("trace-1", 1, "tool", "query_datasource",
                "{\"ds\":\"hr\"}", "{\"rows\":3}", 12L, "ok");
        observer.onStep("trace-1", 2, "agent", "意图路由",
                "{\"agentType\":\"tool\"}", "{\"routed\":true}", 1L, "ok");
        observer.onEnd("trace-1", "success", "你好", "已处理", 42, 130L);

        assertThat(exporter.spans).hasSize(3);
        GenAiSpan root = exporter.spans.get(0);
        assertThat(root.kind()).isEqualTo(GenAiSpanKind.AGENT);
        assertThat(root.name()).isEqualTo("invoke_agent 测试助手");
        assertThat(root.operationName()).isEqualTo(GenAiOperationName.INVOKE_AGENT);
        assertThat(root.attributes())
                .containsEntry(GenAiAttributeNames.SESSION_ID, "session-a")
                .containsEntry(GenAiAttributeNames.AGENT_NAME, "测试助手")
                .containsEntry(GenAiAttributeNames.USAGE_OUTPUT_TOKENS, 42);
        assertThat(root.status()).isEqualTo("success");

        GenAiSpan tool = exporter.spans.get(1);
        assertThat(tool.kind()).isEqualTo(GenAiSpanKind.TOOL);
        assertThat(tool.name()).isEqualTo("execute_tool query_datasource");
        assertThat(tool.operationName()).isEqualTo(GenAiOperationName.EXECUTE_TOOL);

        GenAiSpan step = exporter.spans.get(2);
        assertThat(step.kind()).isEqualTo(GenAiSpanKind.STEP);
    }

    @Test
    void mapsStepTypesToKinds() {
        assertThat(GenAiSpanKind.fromStepType("tool")).isEqualTo(GenAiSpanKind.TOOL);
        assertThat(GenAiSpanKind.fromStepType("agent")).isEqualTo(GenAiSpanKind.STEP);
        assertThat(GenAiSpanKind.fromStepType("plan")).isEqualTo(GenAiSpanKind.STEP);
        assertThat(GenAiSpanKind.fromStepType("rag")).isEqualTo(GenAiSpanKind.RETRIEVER);
        assertThat(GenAiSpanKind.fromStepType("other")).isEqualTo(GenAiSpanKind.TASK);
        assertThat(GenAiSpanKind.fromStepType(null)).isEqualTo(GenAiSpanKind.TASK);
    }

    @Test
    void ignoresStepsBeforeBeginAndEndsWithoutTrace() {
        CollectingExporter exporter = new CollectingExporter();
        GenAiTraceObserver observer = new GenAiTraceObserver(exporter);

        observer.onStep("no-trace", 1, "tool", "x", "{}", "{}", 1L, "ok");
        observer.onEnd("no-trace", "success", "p", "r", 0, 1L);

        assertThat(exporter.spans).isEmpty();
    }

    @Test
    void truncatesLongContent() {
        CollectingExporter exporter = new CollectingExporter();
        GenAiTraceObserver observer = new GenAiTraceObserver(exporter);
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 1200; i++) {
            longText.append('a');
        }
        observer.onBegin("t", "s", "a", "agent", "c", "console");
        observer.onEnd("t", "success", longText.toString(), "r", 0, 1L);
        GenAiSpan root = exporter.spans.get(0);
        String prompt = (String) root.attributes().get(GenAiAttributeNames.PROMPT);
        assertThat(prompt).hasSize(1001); // 1000 + 省略号
        assertThat(prompt).endsWith("…");
    }
}
