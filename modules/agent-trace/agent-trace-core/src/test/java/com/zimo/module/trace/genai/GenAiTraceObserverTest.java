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

    /**
     * 回归守卫：onBegin 未到达的链路<b>不再整条丢弃</b>。
     *
     * <p>历史 bug：onStep 开头有 {@code if (!traceSpans.containsKey(traceId)) return;}，
     * 只要 onBegin 没到（中间件未装配 / 事件来自其它路径），后续所有 step 全被静默吞掉，
     * 且 onEnd 又因 root 为 null 提前 return，最终一条 span 都导不出——表现就是
     * "observer 注册成功但产出为 0"。现改为 {@code ensureRoot} 兜底补建根 span。</p>
     */
    @Test
    void synthesizesRootWhenBeginNeverArrived() {
        CollectingExporter exporter = new CollectingExporter();
        GenAiTraceObserver observer = new GenAiTraceObserver(exporter);

        observer.onStep("no-trace", 1, "tool_call", "query_datasource",
                "{}", "{}", 5L, "ok");
        observer.onEnd("no-trace", "success", "p", "r", 0, 5L);

        // 根 span（兜底补建）+ 那一条 step
        assertThat(exporter.spans).hasSize(2);
        GenAiSpan root = exporter.spans.get(0);
        assertThat(root.kind()).isEqualTo(GenAiSpanKind.AGENT);
        assertThat(root.operationName()).isEqualTo(GenAiOperationName.INVOKE_AGENT);

        GenAiSpan step = exporter.spans.get(1);
        assertThat(step.kind()).isEqualTo(GenAiSpanKind.TOOL);
        assertThat(step.name()).isEqualTo("execute_tool query_datasource");
    }

    /**
     * 回归守卫：{@code model_call} / {@code reasoning} 不得退化成 TASK。
     *
     * <p>这三个词是 HarnessTraceMiddleware 实际写入的词汇，早期 fromStepType 完全不认识，
     * 导致真实链路的 LLM span 全部塌成 TASK。</p>
     */
    @Test
    void middlewareVocabularyYieldsRealKinds() {
        CollectingExporter exporter = new CollectingExporter();
        GenAiTraceObserver observer = new GenAiTraceObserver(exporter);

        observer.onBegin("trace-2", "s", "a", "助手", "conversation", "console");
        observer.onStep("trace-2", 1, "intent", "意图识别", "{}", "{}", 1L, "ok");
        observer.onStep("trace-2", 2, "model_call", "模型调用 qwen-max", "{}", "{}", 1L, "ok");
        observer.onStep("trace-2", 3, "reasoning", "推理", "{}", "{}", 1L, "ok");
        observer.onStep("trace-2", 4, "tool_call", "search_docs", "{}", "{}", 1L, "ok");
        observer.onEnd("trace-2", "success", "p", "r", 0, 5L);

        assertThat(exporter.spans).hasSize(5);
        assertThat(exporter.spans.get(2).kind()).isEqualTo(GenAiSpanKind.LLM);
        assertThat(exporter.spans.get(4).kind()).isEqualTo(GenAiSpanKind.TOOL);
        // LLM span 必须带上请求模型名
        assertThat(exporter.spans.get(2).attributes())
                .containsEntry(GenAiAttributeNames.REQUEST_MODEL, "qwen-max");
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
