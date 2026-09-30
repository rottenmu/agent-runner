package com.zimo.framework.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.module.agentmemory.engine.MemoryScope;
import com.zimo.module.agentmemory.engine.PlanExperienceWriter;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.UserConfirmResultEvent;
import io.agentscope.core.message.ToolUseBlock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 计划回写协调器测试（M4-2b）。
 *
 * <p><b>本类的核心职责有两条</b>：① 只在计划模式生效；② 把 HITL 信号正确转成驳回判定。
 * 两条都属「做错了不会报错、只会静默错」的类型，因此都用可证伪的对照用例覆盖。</p>
 *
 * <p>用 spy 式假 writer（记录调用参数）而非真实落库：本类不负责落库，
 * 若测试里掺进 H2，就分不清失败来自「协调器判定错」还是「仓储写不进」。</p>
 */
class PlanWritebackCoordinatorTest {

    /** 记录调用参数的假回写器：只关心「被调用时传了什么」。 */
    private static final class RecordingWriter extends PlanExperienceWriter {

        boolean rejected;
        List<String> steps;
        int calls;

        RecordingWriter() {
            super(null);
        }

        @Override
        public boolean writeback(MemoryScope scope, boolean rejected,
                                 List<String> steps, String traceId) {
            this.calls++;
            this.rejected = rejected;
            this.steps = steps;
            return true;
        }
    }

    private static MemoryScope scope(String agentType) {
        return MemoryScope.of("t1", "u1", "s1", agentType);
    }

    @Test
    @DisplayName("计划模式 + 无确认事件 → 调用回写，rejected=false 且步骤来自 provider")
    void writesForPlanMode() {
        RecordingWriter writer = new RecordingWriter();
        PlanWritebackCoordinator coordinator = new PlanWritebackCoordinator(
                writer, traceId -> List.of("梳理未达账项", "核对流水"));

        boolean written = coordinator.afterPlan(scope(MemoryScope.TYPE_PLAN),
                RuntimeContext.empty(), "tr-1");

        assertThat(written).isTrue();
        assertThat(writer.calls).as("计划模式应触发一次回写").isEqualTo(1);
        assertThat(writer.rejected).as("无确认事件时按未驳回处理").isFalse();
        assertThat(writer.steps).as("步骤应来自 provider").containsExactly("梳理未达账项", "核对流水");
    }

    @Test
    @DisplayName("非计划模式 → 一次都不调用（对照：换个 agentType 就不触发）")
    void skipsForConversationMode() {
        RecordingWriter writer = new RecordingWriter();
        PlanWritebackCoordinator coordinator = new PlanWritebackCoordinator(
                writer, traceId -> List.of("步骤"));

        boolean written = coordinator.afterPlan(scope("conversation"), RuntimeContext.empty(), "tr-1");

        assertThat(written).as("非计划模式应返回 false").isFalse();
        assertThat(writer.calls)
                .as("普通对话没有「计划被驳回」这一环，回写程序性记忆等于凭空造数据")
                .isZero();
    }

    @Test
    @DisplayName("计划模式 + HITL 驳回 → 仍调用回写器但 rejected=true（守卫由 writer 落地）")
    void propagatesRejectionSignal() {
        RecordingWriter writer = new RecordingWriter();
        PlanWritebackCoordinator coordinator = new PlanWritebackCoordinator(
                writer, traceId -> List.of("步骤"));

        RuntimeContext context = RuntimeContext.empty();
        context.put("zimo.hitl.planRejected", Boolean.TRUE);

        coordinator.afterPlan(scope(MemoryScope.TYPE_PLAN), context, "tr-1");

        assertThat(writer.rejected)
                .as("RuntimeContext 上的驳回标记必须传到回写器 —— 这是标准 2b 的链路关键点")
                .isTrue();
    }

    @Test
    @DisplayName("步骤 provider 抛异常时退化为空步骤，不抛错（不反噬对话）")
    void toleratesFailingStepProvider() {
        RecordingWriter writer = new RecordingWriter();
        PlanWritebackCoordinator coordinator = new PlanWritebackCoordinator(writer, traceId -> {
            throw new IllegalStateException("链路读取故意失败");
        });

        boolean written = coordinator.afterPlan(scope(MemoryScope.TYPE_PLAN),
                RuntimeContext.empty(), "tr-1");

        assertThat(written).as("provider 失败不应中断主流程").isTrue();
        assertThat(writer.steps).as("退化为空步骤").isEmpty();
    }

    @Test
    @DisplayName("traceId 为空时不动作（不埋无主的点）")
    void skipsWithoutTraceId() {
        RecordingWriter writer = new RecordingWriter();
        PlanWritebackCoordinator coordinator = new PlanWritebackCoordinator(writer, t -> List.of());

        assertThat(coordinator.afterPlan(scope(MemoryScope.TYPE_PLAN), RuntimeContext.empty(), null))
                .isFalse();
        assertThat(writer.calls).isZero();
    }

    @Test
    @DisplayName("scope 不可用（缺租户）时不动作")
    void skipsWithoutTenant() {
        RecordingWriter writer = new RecordingWriter();
        PlanWritebackCoordinator coordinator = new PlanWritebackCoordinator(writer, t -> List.of());

        assertThat(coordinator.afterPlan(MemoryScope.of(null, "u1", "s1", MemoryScope.TYPE_PLAN),
                RuntimeContext.empty(), "tr-1")).isFalse();
        assertThat(writer.calls).isZero();
    }

    @Test
    @DisplayName("端到端信号链：UserConfirmResultEvent(驳回) → context → 协调器识别为驳回")
    void endToEndRejectionSignal() {
        RecordingWriter writer = new RecordingWriter();
        PlanWritebackCoordinator coordinator = new PlanWritebackCoordinator(
                writer, traceId -> List.of("步骤"));

        // 模拟中间件消费到框架事件后的效果
        RuntimeContext context = RuntimeContext.empty();
        new com.zimo.framework.ai.observ.HitlConfirmSignalMiddleware()
                .onAgent(null, context, new io.agentscope.core.middleware.AgentInput(List.of()),
                        ignored -> reactor.core.publisher.Flux.just(
                                new UserConfirmResultEvent("r", List.of(
                                        new ConfirmResult(false, (ToolUseBlock) null)))))
                .blockLast();

        coordinator.afterPlan(scope(MemoryScope.TYPE_PLAN), context, "tr-1");

        assertThat(writer.rejected)
                .as("从框架事件一路传到回写器的驳回标记不得丢失")
                .isTrue();
        assertThat(new ArrayList<>(writer.steps)).hasSize(1);
    }
}
