package com.zimo.framework.ai.observ;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.UserConfirmResultEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.message.ToolUseBlock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * HITL 确认信号中间件测试（M4-2b）。
 *
 * <h2>为什么要测「未驳回」与「无事件」两种默认</h2>
 * <p>回写守卫依赖 {@link HitlConfirmSignalMiddleware#isPlanRejected} 的返回值。
 * 它有三个可能取值场景：明确驳回（true）、明确确认（false）、整轮没出现确认事件（false）。
 * 若把第三种写成 {@code true}，功能会静默失效（永不回写）而没有任何报错 ——
 * 这正是本项目最警惕的失败模式，因此三种都要有独立断言。</p>
 *
 * <p>驱动方式：直接给中间件一个可控的 {@code Flux<AgentEvent>}，
 * 而不是去构造真实 Agent —— 本类的职责只是「从事件流里抽取信号」，
 * 用真 Agent 会让测试依赖模型与工具链，反而测不到这个职责。</p>
 */
class HitlConfirmSignalMiddlewareTest {

    private final HitlConfirmSignalMiddleware middleware = new HitlConfirmSignalMiddleware();

    /** 用给定事件流跑一次 onAgent，返回被写入信号的 RuntimeContext。 */
    private static RuntimeContext run(HitlConfirmSignalMiddleware mw, AgentEvent... events) {
        RuntimeContext context = RuntimeContext.empty();
        AgentInput input = new AgentInput(List.of());
        mw.onAgent(null, context, input,
                ignored -> Flux.fromArray(events)).blockLast();
        return context;
    }

    @Test
    @DisplayName("驳回：USER_CONFIRM_RESULT 里 confirmed=false → isPlanRejected 为 true")
    void marksRejectedWhenNotConfirmed() {
        RuntimeContext context = run(middleware,
                new UserConfirmResultEvent("reply-1",
                        List.of(new ConfirmResult(false, (ToolUseBlock) null))));

        assertThat(HitlConfirmSignalMiddleware.isPlanRejected(context))
                .as("框架事件明确说未确认，必须判为驳回").isTrue();
    }

    @Test
    @DisplayName("确认：confirmed=true → isPlanRejected 保持 false（与驳回成对）")
    void staysAcceptedWhenConfirmed() {
        RuntimeContext context = run(middleware,
                new UserConfirmResultEvent("reply-1",
                        List.of(new ConfirmResult(true, (ToolUseBlock) null))));

        assertThat(HitlConfirmSignalMiddleware.isPlanRejected(context))
                .as("已确认不得被误判为驳回 —— 否则计划经验永远回写不了").isFalse();
    }

    @Test
    @DisplayName("无确认事件（模型没走确认流程）→ 默认 false（兜底，PRD「未驳回即回写」）")
    void defaultsToNotRejectedWithoutEvents() {
        RuntimeContext context = run(middleware,
                new io.agentscope.core.event.AgentEvent() {
                    @Override
                    public io.agentscope.core.event.AgentEventType getType() {
                        return io.agentscope.core.event.AgentEventType.AGENT_START;
                    }
                });

        assertThat(HitlConfirmSignalMiddleware.isPlanRejected(context))
                .as("没出现确认事件时应取兜底值 false，而不是 true").isFalse();
        assertThat(HitlConfirmSignalMiddleware.isConfirmPending(context))
                .as("未出现待确认事件时 pending 应为 false").isFalse();
    }

    @Test
    @DisplayName("待确认（REQUIRE_USER_CONFIRM）只标记 pending，不直接判为驳回")
    void requireConfirmOnlyMarksPending() {
        RuntimeContext context = run(middleware,
                new RequireUserConfirmEvent("reply-1", List.of()));

        assertThat(HitlConfirmSignalMiddleware.isConfirmPending(context))
                .as("出现过待确认应被记下，便于区分「没这一环」与「过后被驳回」").isTrue();
        assertThat(HitlConfirmSignalMiddleware.isPlanRejected(context))
                .as("仅仅走到待确认不等于用户驳回，不能提前判驳回").isFalse();
    }

    @Test
    @DisplayName("多条确认结果里任意一条被驳回 → 整体判为驳回")
    void anyRejectionWins() {
        RuntimeContext context = run(middleware,
                new UserConfirmResultEvent("reply-1", List.of(
                        new ConfirmResult(true, (ToolUseBlock) null),
                        new ConfirmResult(false, (ToolUseBlock) null))));

        assertThat(HitlConfirmSignalMiddleware.isPlanRejected(context))
                .as("部分驳回也应视为计划未被认可，不能只认同意的那些").isTrue();
    }

    @Test
    @DisplayName("context 为 null 时不抛异常（信号采集是旁路能力）")
    void toleratesNullContext() {
        RuntimeContext context = RuntimeContext.empty();
        AgentInput input = new AgentInput(List.of());
        middleware.onAgent(null, null, input, ignored -> Flux.just(
                (AgentEvent) new UserConfirmResultEvent("r",
                        List.of(new ConfirmResult(false, (ToolUseBlock) null))))).blockLast();

        assertThat(HitlConfirmSignalMiddleware.isPlanRejected(context))
                .as("没有 context 时读值应为 false，不抛错").isFalse();
        assertThat(HitlConfirmSignalMiddleware.isPlanRejected(null))
                .as("直接传 null 也应安全返回 false").isFalse();
    }
}
