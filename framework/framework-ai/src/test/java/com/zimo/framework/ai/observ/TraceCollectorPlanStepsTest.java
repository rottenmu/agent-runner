package com.zimo.framework.ai.observ;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TraceCollector} 计划步骤旁路缓冲测试（M4-2b）。
 *
 * <p><b>为什么这条缓冲需要单独测</b>：它是计划经验回写的唯一步骤来源，
 * 而它的失效方式全是静默的 —— 归类规则写错（步骤收不到）、
 * 忘了在链路收尾时清理（内存随请求量增长）。两者都不会报错，
 * 只会在真机上表现为「回写的记忆没有步骤」或「跑久了 OOM」。</p>
 */
class TraceCollectorPlanStepsTest {

    private static final String TRACE = "tr-plan-steps";

    @AfterEach
    void cleanUp() {
        // 清掉测试链路，避免跨用例互相看见残留
        TraceCollector.endFor(TRACE, "ok", null, null, 0, 0L);
    }

    @Test
    @DisplayName("plan_ 前缀步骤被收集（plan_enter / plan_write / plan_exit）")
    void collectsPlanPrefixedSteps() {
        TraceCollector.stepFor(TRACE, "plan", "plan_enter", null, null, 0, "ok");
        TraceCollector.stepFor(TRACE, "plan", "plan_write", null, null, 0, "ok");
        TraceCollector.stepFor(TRACE, "plan", "plan_exit", null, null, 0, "ok");

        assertThat(TraceCollector.planStepsOf(TRACE))
                .containsExactly("plan_enter", "plan_write", "plan_exit");
    }

    @Test
    @DisplayName("todo_write 也被收集（计划步骤的另一种载体）")
    void collectsTodoWrite() {
        TraceCollector.stepFor(TRACE, "plan", "todo_write", null, null, 0, "ok");

        assertThat(TraceCollector.planStepsOf(TRACE)).containsExactly("todo_write");
    }

    @Test
    @DisplayName("对照：普通步骤（model_call / tool_call）不得混入 —— 否则回写内容被噪音灌满")
    void ignoresNonPlanSteps() {
        TraceCollector.stepFor(TRACE, "model_call", "模型调用 DashScopeChatModel", null, null, 0, "ok");
        TraceCollector.stepFor(TRACE, "retrieval", "memory.recall", null, null, 0, "ok");
        TraceCollector.stepFor(TRACE, "tool_call", "工具执行 search", null, null, 0, "ok");

        assertThat(TraceCollector.planStepsOf(TRACE))
                .as("非计划步骤混进来会让「共 N 步」虚高").isEmpty();
    }

    @Test
    @DisplayName("同一步骤重复上报只记一次（begin/ok 两相位）")
    void deduplicatesSameStep() {
        TraceCollector.stepFor(TRACE, "plan", "plan_write", null, null, 0, "begin");
        TraceCollector.stepFor(TRACE, "plan", "plan_write", null, null, 0, "ok");

        assertThat(TraceCollector.planStepsOf(TRACE)).containsExactly("plan_write");
    }

    @Test
    @DisplayName("链路结束后缓冲被清理（防内存泄漏）")
    void clearsOnEnd() {
        TraceCollector.stepFor(TRACE, "plan", "plan_enter", null, null, 0, "ok");
        assertThat(TraceCollector.planStepsOf(TRACE)).isNotEmpty();

        TraceCollector.endFor(TRACE, "ok", null, null, 0, 0L);

        assertThat(TraceCollector.planStepsOf(TRACE))
                .as("不清理会随请求量持续泄漏 —— 这是静默的内存泄漏点").isEmpty();
    }

    @Test
    @DisplayName("未知 traceId / null 返回空列表而非 null")
    void returnsEmptyForUnknown() {
        assertThat(TraceCollector.planStepsOf("never-existed")).isEmpty();
        assertThat(TraceCollector.planStepsOf(null)).isEmpty();
    }

    @Test
    @DisplayName("stepFor 传 null traceId 时不记录（不伪造 ID）")
    void ignoresNullTraceId() {
        TraceCollector.stepFor(null, "plan", "plan_enter", null, null, 0, "ok");

        assertThat(TraceCollector.planStepsOf(null)).isEmpty();
    }

    @Test
    @DisplayName("多个链路的步骤互不串（隔离）")
    void isolatesBetweenTraces() {
        String other = "tr-plan-other";
        try {
            TraceCollector.stepFor(TRACE, "plan", "plan_enter", null, null, 0, "ok");
            TraceCollector.stepFor(other, "plan", "todo_write", null, null, 0, "ok");

            assertThat(TraceCollector.planStepsOf(TRACE)).containsExactly("plan_enter");
            assertThat(TraceCollector.planStepsOf(other)).containsExactly("todo_write");
            assertThat(TraceCollector.planStepsOf(TRACE))
                    .as("两条链路的内容不得互相污染").doesNotContain("todo_write");
        } finally {
            TraceCollector.endFor(other, "ok", null, null, 0, 0L);
        }
    }

    @Test
    @DisplayName("返回列表不可变（调用方改不动内部缓冲）")
    void returnsImmutableCopy() {
        TraceCollector.stepFor(TRACE, "plan", "plan_enter", null, null, 0, "ok");
        List<String> steps = TraceCollector.planStepsOf(TRACE);

        assertThat(steps).isNotNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> steps.add("x"))
                .as("返回内部 List 会让调用方意外改坏采集缓冲")
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
