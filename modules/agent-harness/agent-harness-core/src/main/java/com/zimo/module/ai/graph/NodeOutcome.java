package com.zimo.module.ai.graph;

import java.util.List;
import java.util.Map;

/**
 * 节点执行结果（节点能力与引擎调度之间的唯一契约）。
 *
 * <p>所属模块：agent-harness 运行时内核。包含输出、条件结果与调度提示：
 * 引擎据此决定下一跳、并行、主管扇出或挂起。</p>
 *
 * <p>为什么单独成类：它被节点执行器产出、被引擎与扇出执行器消费，
 * 是三者共享的类型；留在 {@link GraphNodeExecutor} 里会让"节点能力"与
 * "调度契约"绑死，也让节点执行器无谓地膨胀。</p>
 *
 * <p>调度提示用 {@link Kind} 枚举而非若干布尔位：并行、主管、挂起、结束彼此互斥，
 * 用布尔位表达时"同时为真"是可能的非法状态，靠调用方自觉；枚举让非法状态不可表示。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public final class NodeOutcome {

    /** 调度提示类型。 */
    private enum Kind {
        /** 普通输出节点。 */
        OUTPUT,
        /** 结束节点。 */
        FINISHED,
        /** 条件节点。 */
        CONDITION,
        /** 人工挂起节点。 */
        SUSPEND,
        /** 并行扇出节点。 */
        PARALLEL,
        /** 主管（supervisor）扇出节点。 */
        SUPERVISOR
    }

    private final Kind kind;

    /** 节点输出，写入 GraphState.nodeOutputs。 */
    private final Map<String, Object> output;

    /** 条件节点结果；非条件节点为 null。 */
    private final Boolean conditionResult;

    /** 扇出子节点清单（并行 / 主管）。 */
    private final List<String> children;

    /** 主管节点执行策略：parallel / sequential。 */
    private final String strategy;

    /** 人工挂起的现场载荷（提示语/表单），非挂起节点为空 Map。 */
    private final Map<String, Object> suspendPayload;

    private NodeOutcome(Kind kind, Map<String, Object> output, Boolean conditionResult,
            List<String> children, String strategy, Map<String, Object> suspendPayload) {
        this.kind = kind;
        this.output = output == null ? Map.of() : output;
        this.conditionResult = conditionResult;
        this.children = children == null ? List.of() : children;
        this.strategy = strategy == null ? "" : strategy;
        this.suspendPayload = suspendPayload == null ? Map.of() : suspendPayload;
    }

    /** 普通输出节点。 */
    static NodeOutcome output(Map<String, Object> output) {
        return new NodeOutcome(Kind.OUTPUT, output, null, null, null, null);
    }

    /** 结束节点。 */
    static NodeOutcome finished(Map<String, Object> output) {
        return new NodeOutcome(Kind.FINISHED, output, null, null, null, null);
    }

    /** 条件节点。 */
    static NodeOutcome condition(Map<String, Object> output, boolean result) {
        return new NodeOutcome(Kind.CONDITION, output, result, null, null, null);
    }

    /**
     * 人工挂起节点。
     *
     * @param payload 挂起现场载荷，供待审批查询与审批界面渲染
     * @return 挂起结果
     */
    static NodeOutcome suspend(Map<String, Object> payload) {
        return new NodeOutcome(Kind.SUSPEND, Map.of(), null, null, null, payload);
    }

    /** 并行节点。 */
    static NodeOutcome parallel(List<String> children) {
        return new NodeOutcome(Kind.PARALLEL, Map.of(), null, children, null, null);
    }

    /**
     * 主管节点。
     *
     * @param children worker 节点 ID 列表
     * @param strategy 执行策略：parallel / sequential
     * @return 主管扇出结果
     */
    static NodeOutcome supervisor(List<String> children, String strategy) {
        return new NodeOutcome(Kind.SUPERVISOR, Map.of(), null, children, strategy, null);
    }

    /** 节点输出。 */
    public Map<String, Object> output() {
        return output;
    }

    /** 条件结果；非条件节点为 null。 */
    public Boolean conditionResult() {
        return conditionResult;
    }

    /** 是否为结束节点。 */
    public boolean finished() {
        return kind == Kind.FINISHED;
    }

    /** 是否需要人工挂起。 */
    public boolean suspended() {
        return kind == Kind.SUSPEND;
    }

    /** 是否为并行节点。 */
    public boolean parallel() {
        return kind == Kind.PARALLEL;
    }

    /** 是否为主管扇出节点。 */
    public boolean supervisor() {
        return kind == Kind.SUPERVISOR;
    }

    /** 扇出子节点清单。 */
    public List<String> children() {
        return children;
    }

    /** 主管节点执行策略；非主管节点为空串。 */
    public String strategy() {
        return strategy;
    }

    /** 人工挂起现场载荷；非挂起节点为空 Map。 */
    public Map<String, Object> suspendPayload() {
        return suspendPayload;
    }
}
