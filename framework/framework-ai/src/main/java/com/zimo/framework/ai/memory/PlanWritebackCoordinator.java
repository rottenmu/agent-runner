package com.zimo.framework.ai.memory;

import com.zimo.framework.ai.observ.HitlConfirmSignalMiddleware;
import com.zimo.module.agentmemory.engine.MemoryScope;
import com.zimo.module.agentmemory.engine.PlanExperienceWriter;
import io.agentscope.core.agent.RuntimeContext;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 计划模式回写协调器（M4-2b，PRD §4.2）。
 *
 * <p><b>为什么单独成类而不是写进 {@code AiAgentService}</b>：该服务有效代码行已达 509
 * （规范上限 500，见 {@code docs/rules/CODE_SIZE_RULES.md}），且这是既有欠债 ——
 * 往里继续堆逻辑会让欠债扩大。因此服务层只保留<b>一行</b>调用，本类承载全部新增判断：
 * 读 HITL 信号、判定驳回、抽取计划步骤、投递回写。</p>
 *
 * <h2>两个信号，两种来源</h2>
 * <ul>
 *   <li><b>驳回与否</b>：来自 {@link HitlConfirmSignalMiddleware} 写在 {@link RuntimeContext}
 *       上的标记（源头是 AgentScope 的 HITL 事件）。用 RuntimeContext 而非 ThreadLocal，
 *       因为推理在 Reactor 调度线程上跑，ThreadLocal 过不了异步边界。</li>
 *   <li><b>计划步骤</b>：来自本轮链路里已落库的 plan 步骤（{@code TraceCollector} 通道），
 *       由 {@code stepProvider} 回调提供。刻意不重复采集一遍 ——
 *       {@code ObservEventBridge} 已把 {@code plan_*} / {@code todo_write} 工具事件
 *       归类为 {@code plan} 步骤写进链路，直接读是唯一真相源。</li>
 * </ul>
 *
 * <h2>只在计划模式生效</h2>
 * <p>非计划模式直接返回：普通对话没有「计划被驳回」这一环，回写程序性记忆属于凭空造数据。</p>
 *
 * @author WorkBuddy
 * @since 2026-09-17
 */
public class PlanWritebackCoordinator {

    private static final Logger log = LoggerFactory.getLogger(PlanWritebackCoordinator.class);

    private final PlanExperienceWriter writer;
    /** 计划步骤提供方（按 traceId 读链路里的 plan 步骤）；可为空，为空时步骤退化为空。 */
    private final PlanStepProvider stepProvider;

    /**
     * @param writer       计划经验回写器（承载实际的落库与驳回守卫）
     * @param stepProvider 按 traceId 取本轮计划步骤；可为 {@code null}（步骤按空处理）
     */
    public PlanWritebackCoordinator(PlanExperienceWriter writer, PlanStepProvider stepProvider) {
        this.writer = writer;
        this.stepProvider = stepProvider;
    }

    /** 计划步骤来源：由 framework-ai 侧适配自研链路通道。 */
    @FunctionalInterface
    public interface PlanStepProvider {
        /**
         * @param traceId 链路 ID
         * @return 本轮的步骤描述列表；无计划步骤时返回空列表（不返回 {@code null}）
         */
        List<String> stepsOf(String traceId);
    }

    /**
     * 本轮结束后按需回写计划经验。
     *
     * @param scope     隔离维度（须带 agentType，用于判定是否计划模式）
     * @param context   AgentScope 运行时上下文（HITL 信号载体）；可为空
     * @param traceId   链路 ID；为空时不做（不埋无主的点）
     * @return 是否真的回写了记忆
     */
    public boolean afterPlan(MemoryScope scope, RuntimeContext context, String traceId) {
        if (writer == null || scope == null || !scope.usable() || traceId == null) {
            return false;
        }
        if (!scope.planMode()) {
            // 非计划模式：没有「计划被驳回」这一环，不做回写
            return false;
        }
        boolean rejected = HitlConfirmSignalMiddleware.isPlanRejected(context);
        List<String> steps = stepsOf(traceId);
        log.debug("[plan-writeback] traceId={} rejected={} confirmPending={} steps={}",
                traceId, rejected, HitlConfirmSignalMiddleware.isConfirmPending(context),
                steps.size());
        return writer.writeback(scope, rejected, steps, traceId);
    }

    /** 取步骤：提供方缺失或抛错时退化为空列表（回写退化为不写，不影响对话）。 */
    private List<String> stepsOf(String traceId) {
        if (stepProvider == null) {
            return List.of();
        }
        try {
            List<String> steps = stepProvider.stepsOf(traceId);
            return steps == null ? List.of() : new ArrayList<>(steps);
        } catch (RuntimeException e) {
            log.warn("[plan-writeback] 计划步骤读取失败 traceId={}：{}", traceId, e.toString());
            return List.of();
        }
    }
}
