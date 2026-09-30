package com.zimo.module.trace.genai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * GenAiStepTypes 单测：三套历史词汇的归一化。
 *
 * <p>本测试锁定的正是"agent-trace 注册了但产出 0 条"的根因 ——
 * {@code HarnessTraceMiddleware} 实际写入的 {@code model_call} / {@code reasoning} /
 * {@code tool_call} 必须被正确映射，若只认早期的 {@code tool} / {@code plan}，
 * 绝大多数步骤会静默落进 TASK。</p>
 */
class GenAiStepTypesTest {

    @Test
    void normalizesMiddlewareVocabulary() {
        // HarnessTraceMiddleware 实际写入的词（最关键的一组）
        assertThat(GenAiStepTypes.normalize("intent")).isEqualTo(GenAiStepTypes.INTENT);
        assertThat(GenAiStepTypes.normalize("agent")).isEqualTo(GenAiStepTypes.AGENT);
        assertThat(GenAiStepTypes.normalize("model_call")).isEqualTo(GenAiStepTypes.MODEL_CALL);
        assertThat(GenAiStepTypes.normalize("reasoning")).isEqualTo(GenAiStepTypes.REASONING);
        assertThat(GenAiStepTypes.normalize("tool_call")).isEqualTo(GenAiStepTypes.TOOL_CALL);
    }

    @Test
    void normalizesLegacyKindVocabulary() {
        assertThat(GenAiStepTypes.normalize("tool")).isEqualTo(GenAiStepTypes.TOOL_CALL);
        assertThat(GenAiStepTypes.normalize("plan")).isEqualTo(GenAiStepTypes.REASONING);
        assertThat(GenAiStepTypes.normalize("rag")).isEqualTo(GenAiStepTypes.RETRIEVAL);
        assertThat(GenAiStepTypes.normalize("retriever")).isEqualTo(GenAiStepTypes.RETRIEVAL);
    }

    @Test
    void normalizesObserverJavadocVocabulary() {
        // TraceObserver.onStep 的 Javadoc 声称的那一套
        assertThat(GenAiStepTypes.normalize("knowledge_retrieval")).isEqualTo(GenAiStepTypes.RETRIEVAL);
        assertThat(GenAiStepTypes.normalize("prompt")).isEqualTo(GenAiStepTypes.MODEL_CALL);
        assertThat(GenAiStepTypes.normalize("generation")).isEqualTo(GenAiStepTypes.GENERATION);
    }

    @Test
    void handlesCaseAndSeparatorVariants() {
        assertThat(GenAiStepTypes.normalize("MODEL_CALL")).isEqualTo(GenAiStepTypes.MODEL_CALL);
        assertThat(GenAiStepTypes.normalize("model-call")).isEqualTo(GenAiStepTypes.MODEL_CALL);
        assertThat(GenAiStepTypes.normalize("  Tool_Call  ")).isEqualTo(GenAiStepTypes.TOOL_CALL);
        assertThat(GenAiStepTypes.normalize("Knowledge-Retrieval")).isEqualTo(GenAiStepTypes.RETRIEVAL);
    }

    @Test
    void unknownAndNullFallBackWithoutThrowing() {
        assertThat(GenAiStepTypes.normalize(null)).isEqualTo(GenAiStepTypes.UNKNOWN);
        assertThat(GenAiStepTypes.normalize("")).isEqualTo(GenAiStepTypes.UNKNOWN);
        assertThat(GenAiStepTypes.normalize("whatever")).isEqualTo(GenAiStepTypes.UNKNOWN);
        assertThat(GenAiSpanKind.fromStepType("whatever")).isEqualTo(GenAiSpanKind.TASK);
    }

    @Test
    void mapsToSpanKind() {
        assertThat(GenAiStepTypes.toSpanKind("model_call")).isEqualTo(GenAiSpanKind.LLM);
        assertThat(GenAiStepTypes.toSpanKind("reasoning")).isEqualTo(GenAiSpanKind.STEP);
        assertThat(GenAiStepTypes.toSpanKind("tool_call")).isEqualTo(GenAiSpanKind.TOOL);
        assertThat(GenAiStepTypes.toSpanKind("agent")).isEqualTo(GenAiSpanKind.STEP);
        assertThat(GenAiStepTypes.toSpanKind("intent")).isEqualTo(GenAiSpanKind.TASK);
        assertThat(GenAiStepTypes.toSpanKind("knowledge_retrieval")).isEqualTo(GenAiSpanKind.RETRIEVER);
    }

    @Test
    void detectsContainerAndEntrySteps() {
        // 容器型：由 doOnComplete 写入，seq 最大
        assertThat(GenAiStepTypes.isContainer("agent")).isTrue();
        assertThat(GenAiStepTypes.isContainer("generation")).isTrue();
        assertThat(GenAiStepTypes.isContainer("model_call")).isFalse();
        // 入口型：发生在智能体执行之前
        assertThat(GenAiStepTypes.isEntry("intent")).isTrue();
        assertThat(GenAiStepTypes.isEntry("agent")).isFalse();
    }

    @Test
    void buildsSemanticSpanNames() {
        assertThat(GenAiStepTypes.spanName("tool_call", "query_datasource"))
                .isEqualTo("execute_tool query_datasource");
        assertThat(GenAiStepTypes.spanName("model_call", "模型调用 DashScopeChatModel"))
                .isEqualTo("chat 模型调用 DashScopeChatModel");
        assertThat(GenAiStepTypes.spanName("knowledge_retrieval", "向量召回"))
                .isEqualTo("retrieval 向量召回");
        // 无名时用规范词兜底，不留空
        assertThat(GenAiStepTypes.spanName("model_call", null)).isEqualTo("chat model_call");
        assertThat(GenAiStepTypes.spanName("reasoning", "   ")).isEqualTo("reasoning");
    }

    /** 回归护栏：真实链路形状（6 步）每一步都必须落到预期 kind，且无一落 TASK 兜底。 */
    @Test
    void realTraceShapeMapsEntirely() {
        record Step(int seq, String type) {
        }
        List<Step> real = List.of(
                new Step(1, "intent"),
                new Step(2, "model_call"),
                new Step(3, "reasoning"),
                new Step(4, "model_call"),
                new Step(5, "reasoning"),
                new Step(6, "agent"));
        List<GenAiSpanKind> kinds = new ArrayList<>();
        for (Step s : real) {
            kinds.add(GenAiStepTypes.toSpanKind(s.type()));
        }
        assertThat(kinds).containsExactly(
                GenAiSpanKind.TASK,      // intent
                GenAiSpanKind.LLM,       // model_call
                GenAiSpanKind.STEP,      // reasoning
                GenAiSpanKind.LLM,       // model_call
                GenAiSpanKind.STEP,      // reasoning
                GenAiSpanKind.STEP);     // agent
        // 关键：model_call 不能退化成 TASK（那正是历史 bug 的表现）
        assertThat(kinds.stream().filter(k -> k == GenAiSpanKind.LLM).count()).isEqualTo(2);
        Map<String, Object> health = GenAiTraceDiagnostics.health();
        assertThat(health).containsKeys("healthy", "warnings");
    }
}
