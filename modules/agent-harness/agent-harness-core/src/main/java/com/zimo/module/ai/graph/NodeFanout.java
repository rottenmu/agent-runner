package com.zimo.module.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 节点扇出执行器：并行节点与主管节点的子节点调度。
 *
 * <p>所属模块：agent-harness 运行时内核。从 {@link GraphEngine} 中拆出，
 * 因为这两类节点的共同点是"一个节点驱动多个子节点"，而它们对失败的<b>态度不同</b>：</p>
 *
 * <table border="1">
 *   <caption>两种扇出的失败语义</caption>
 *   <tr><th>节点类型</th><th>子节点失败时</th><th>结果去向</th></tr>
 *   <tr>
 *     <td>{@code parallel}</td><td>整体抛异常，由引擎走 fallback / 判失败</td>
 *     <td>「要么全成、要么停」的流水线语义</td>
 *   </tr>
 *   <tr>
 *     <td>{@code supervisor}</td><td>折成失败回执，其余 worker 继续</td>
 *     <td>「部分成功 + 人工裁决」的多智能体语义</td>
 *   </tr>
 * </table>
 *
 * <p>把失败语义写在这里而不是散落在引擎里，是因为它是最容易被"顺手统一"的地方：
 * 一旦为了复用把 supervisor 改成抛出，部分成功的结果就会连同异常一起消失。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class NodeFanout {

    /** 主管节点默认执行策略：并发（worker 之间互不依赖时最快）。 */
    public static final String STRATEGY_PARALLEL = "parallel";

    /** 主管节点串行策略：后一个 worker 可读到前一个的输出。 */
    public static final String STRATEGY_SEQUENTIAL = "sequential";

    /** 不允许作为 worker 的编排类节点类型。 */
    private static final List<String> CONTROL_TYPES =
            List.of("start", "end", "human", "manual", "parallel", "supervisor");

    private final Executor parallelExecutor;
    private final GraphNodeExecutor nodeExecutor;
    private final SupervisorAggregator aggregator;

    /**
     * 构造扇出执行器。
     *
     * @param parallelExecutor 并行线程池，不可为空
     * @param nodeExecutor 节点执行器，不可为空
     * @param aggregator 主管回执汇总器，不可为空
     */
    public NodeFanout(Executor parallelExecutor, GraphNodeExecutor nodeExecutor,
            SupervisorAggregator aggregator) {
        this.parallelExecutor = parallelExecutor;
        this.nodeExecutor = nodeExecutor;
        this.aggregator = aggregator;
    }

    /**
     * 节点级轨迹记录器（由引擎注入，避免扇出执行器依赖运行实例）。
     */
    @FunctionalInterface
    public interface StepLogger {

        /**
         * 记录一条节点轨迹。
         *
         * @param nodeId 节点 ID
         * @param nodeType 节点类型
         * @param action 动作：enter / exit / error
         * @param result 结果：pending / success / failed / conflict
         * @param message 说明
         */
        void log(String nodeId, String nodeType, String action, String result, String message);
    }

    /**
     * 执行并行节点的子节点（fan-out），任一失败即抛出（fan-in 由调用方统一处理）。
     *
     * @param definition 图定义
     * @param state 运行状态
     * @param children 子节点 ID 列表
     * @param logger 轨迹记录器
     * @throws IllegalStateException 任一子节点执行失败时抛出
     */
    public void runParallel(GraphDefinition definition, GraphState state,
            List<String> children, StepLogger logger) {
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (String childId : children) {
            tasks.add(CompletableFuture.runAsync(
                    () -> runParallelChild(definition, state, childId, logger), parallelExecutor));
        }
        try {
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("并行节点执行失败: " + cause.getMessage(), cause);
        }
    }

    /**
     * 执行主管节点的 worker 并汇总回执。
     *
     * <p>worker 的任何失败（异常、闸门拒绝、自报冲突）都只影响它自己那一条回执，
     * 不会中断其余 worker —— 这正是"部分成功"的实现点。</p>
     *
     * @param definition 图定义
     * @param state 运行状态
     * @param workers worker 节点 ID 列表
     * @param strategy 执行策略：parallel（缺省）/ sequential
     * @param logger 轨迹记录器
     * @return 主管节点输出（含 status / receipts / conflicts）
     */
    public Map<String, Object> runSupervisor(GraphDefinition definition, GraphState state,
            List<String> workers, String strategy, StepLogger logger) {
        List<Map<String, Object>> receipts = STRATEGY_SEQUENTIAL.equalsIgnoreCase(strategy)
                ? runSequentially(definition, state, workers, logger)
                : runInParallel(definition, state, workers, logger);
        return aggregator.summarize(receipts);
    }

    /** 串行执行 worker：后一个 worker 能看到前一个的输出。 */
    private List<Map<String, Object>> runSequentially(GraphDefinition definition, GraphState state,
            List<String> workers, StepLogger logger) {
        List<Map<String, Object>> receipts = new ArrayList<>();
        for (String workerId : workers) {
            receipts.add(runWorker(definition, state, workerId, logger));
        }
        return receipts;
    }

    /** 并发执行 worker，但按定义顺序回收回执（顺序稳定，便于前端展示与断言）。 */
    private List<Map<String, Object>> runInParallel(GraphDefinition definition, GraphState state,
            List<String> workers, StepLogger logger) {
        Map<String, Map<String, Object>> collected = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (String workerId : workers) {
            tasks.add(CompletableFuture.runAsync(() -> collected.put(workerId,
                    runWorker(definition, state, workerId, logger)), parallelExecutor));
        }
        try {
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException e) {
            // runWorker 自身不抛异常，走到这里说明线程池本身出了问题
            Throwable cause = e.getCause() == null ? e : e.getCause();
            logger.log("", "supervisor", "error", "failed", "worker 调度异常: " + cause.getMessage());
        }
        List<Map<String, Object>> receipts = new ArrayList<>();
        for (String workerId : workers) {
            Map<String, Object> receipt = collected.get(workerId);
            receipts.add(receipt == null
                    ? aggregator.receipt(workerId, definition.type(workerId), null, "worker 未产出回执")
                    : receipt);
        }
        return receipts;
    }

    /** 执行单个 worker：异常与非法类型都折成失败回执。 */
    private Map<String, Object> runWorker(GraphDefinition definition, GraphState state,
            String workerId, StepLogger logger) {
        JsonNode node = definition.node(workerId);
        String nodeType = definition.type(workerId);
        if (node == null) {
            logger.log(workerId, nodeType, "error", "failed", "worker 节点不存在");
            return aggregator.receipt(workerId, nodeType, null, "worker 节点不存在: " + workerId);
        }
        if (CONTROL_TYPES.contains(nodeType)) {
            logger.log(workerId, nodeType, "error", "failed", "worker 不支持编排类节点");
            return aggregator.receipt(workerId, nodeType, null, "worker 不支持编排类节点: " + nodeType);
        }
        logger.log(workerId, nodeType, "enter", "pending", "worker 开始");
        try {
            NodeOutcome outcome = nodeExecutor.execute(workerId, node, state);
            if (outcome.suspended() || outcome.supervisor()) {
                logger.log(workerId, nodeType, "error", "failed", "worker 不能是挂起或主管节点");
                return aggregator.receipt(workerId, nodeType, null,
                        "worker 不能是挂起或主管节点: " + workerId);
            }
            Object output = outcome.output();
            state.putOutput(workerId, output);
            state.markCompleted(workerId);
            Map<String, Object> receipt = aggregator.receipt(workerId, nodeType, output, null);
            Object status = receipt.get("status");
            logger.log(workerId, nodeType, "exit", String.valueOf(status),
                    SupervisorAggregator.STATUS_SUCCESS.equals(status) ? "worker 完成" : "worker 回执非成功");
            return receipt;
        } catch (Exception e) {
            logger.log(workerId, nodeType, "error", "failed", "worker 异常: " + e.getMessage());
            return aggregator.receipt(workerId, nodeType, null, e.getMessage());
        }
    }

    /** 执行单个并行子节点，写入输出与轨迹（失败直接向上抛）。 */
    private void runParallelChild(GraphDefinition definition, GraphState state,
            String childId, StepLogger logger) {
        JsonNode childNode = definition.node(childId);
        if (childNode == null) {
            throw new IllegalStateException("并行子节点不存在: " + childId);
        }
        logger.log(childId, definition.type(childId), "enter", "pending", "并行子节点开始");
        NodeOutcome outcome = nodeExecutor.execute(childId, childNode, state);
        state.putOutput(childId, outcome.output());
        state.markCompleted(childId);
        logger.log(childId, definition.type(childId), "exit", "success", "并行子节点完成");
    }
}
