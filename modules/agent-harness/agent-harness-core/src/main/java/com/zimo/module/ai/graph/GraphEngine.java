package com.zimo.module.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.common.validation.ValidationUtil;
import com.zimo.module.ai.mapper.WfWorkflowRunLogMapper;
import com.zimo.module.ai.mapper.WfWorkflowRunMapper;
import com.zimo.module.ai.workflow.WfWorkflow;
import com.zimo.module.ai.workflow.WfWorkflowRun;
import com.zimo.module.ai.workflow.WfWorkflowRunLog;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.util.StringUtils;

/**
 * Agent 运行时图执行引擎（状态机驱动）。
 *
 * <p>所属模块：agent-harness 运行时内核。按 {@link GraphDefinition} 驱动执行，
 * 支持条件分支、图回边循环（受 maxIterations 保护）、并行 fan-out/fan-in、
 * 人工挂起，以及每节点 checkpoint 落库后的崩溃续跑。</p>
 *
 * <p>持久化复用了现有 {@code wf_workflow_run}（checkpoint 写入 context_json）
 * 与 {@code wf_workflow_run_log}（节点级 step 轨迹），不新增表结构。</p>
 *
 * <p>与 {@code WfWorkflowEngine} 的关系：并存而非替换。新引擎先接管新增图能力，
 * 存量工作流仍走旧引擎，待兼容性基线验证通过后再迁移入口，避免破坏已上线流程。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class GraphEngine {

    /** 运行中状态。 */
    public static final String STATUS_RUNNING = "running";

    /** 人工挂起状态，等待 resume 令牌恢复。 */
    public static final String STATUS_SUSPENDED = "suspended";

    /** 成功终态。 */
    public static final String STATUS_SUCCESS = "success";

    /** 失败终态。 */
    public static final String STATUS_FAILED = "failed";

    /** 取消终态。 */
    public static final String STATUS_CANCELED = "canceled";

    /**
     * 部分失败终态：流程走完了，但存在未成功交差的 worker（需人工裁决）。
     *
     * <p>与 {@code failed} 的区别：{@code failed} 是"没走完/走不下去"，
     * {@code conflict} 是"走完了但结果有分歧"。混用会让复核人分不清
     * "该重跑"还是"该裁决"。</p>
     */
    public static final String STATUS_CONFLICT = "conflict";

    private final WfWorkflowRunMapper runMapper;
    private final WfWorkflowRunLogMapper logMapper;
    private final GraphNodeExecutor nodeExecutor;
    private final ObjectMapper objectMapper;
    private final NodeFanout fanout;

    /**
     * 构造图执行引擎。
     *
     * @param runMapper 运行实例 Mapper，不可为空
     * @param logMapper 运行日志 Mapper，不可为空
     * @param nodeExecutor 节点执行器，不可为空
     * @param objectMapper JSON 映射器，不可为空
     * @param parallelExecutor 并行节点执行线程池，不可为空
     */
    public GraphEngine(
            WfWorkflowRunMapper runMapper,
            WfWorkflowRunLogMapper logMapper,
            GraphNodeExecutor nodeExecutor,
            ObjectMapper objectMapper,
            Executor parallelExecutor) {
        this.runMapper = runMapper;
        this.logMapper = logMapper;
        this.nodeExecutor = nodeExecutor;
        this.objectMapper = objectMapper;
        this.fanout = new NodeFanout(parallelExecutor, nodeExecutor, new SupervisorAggregator());
    }

    /**
     * 启动图运行。
     *
     * <p>流程：落运行实例 → 解析图定义 → 从 start 节点推进 → 每节点写 checkpoint。</p>
     *
     * @param workflow 工作流定义（含 definitionJson），不可为空
     * @param input 图输入参数，可为空
     * @param debug 是否调试模式（保留字段，图引擎暂不支持单步）
     * @return 运行实例（含最终状态）
     */
    public WfWorkflowRun start(WfWorkflow workflow, Map<String, Object> input, boolean debug) {
        WfWorkflowRun run = new WfWorkflowRun();
        run.setWorkflowId(workflow.getId());
        run.setVersionNo(workflow.getVersionNo());
        run.setDefinitionJson(workflow.getDefinitionJson());
        run.setStatus(STATUS_RUNNING);
        run.setDebug(debug);
        run.setInputJson(writeJson(input));
        LocalDateTime now = LocalDateTime.now();
        run.setStartedAt(now);
        run.setCreatedAt(now);
        runMapper.insert(run);

        GraphDefinition definition = GraphDefinition.parse(objectMapper, run.getDefinitionJson());
        GraphState state = new GraphState(input);
        state.runId(run.getId());
        String startId = definition.startNodeId();
        run.setCurrentNodeId(startId);
        runMapper.updateById(run);
        logStep(run, startId, definition.type(startId), "enter", "pending", "图运行启动");
        advance(run, definition, state, startId);
        return run;
    }

    /**
     * 恢复运行（崩溃续跑）。
     *
     * <p>从 checkpoint 恢复执行状态，并从 currentNodeId 继续推进；
     * 已完成节点不会重跑（其完成标记已在 checkpoint 中）。</p>
     *
     * <p><b>本方法不处理人工挂起</b>：suspended 状态的 currentNodeId 指向 human 节点，
     * 若从这里继续推进会再次进入该节点并二次挂起，形成「恢复即挂起、令牌被换掉」的死循环。
     * 挂起恢复必须走 {@link #approve}，带上裁决结果与令牌。</p>
     *
     * @param runId 运行实例 ID
     * @return 运行实例（含最终状态）
     * @throws IllegalArgumentException 运行不存在、已终态或处于人工挂起态时抛出
     */
    public WfWorkflowRun resume(Long runId) {
        WfWorkflowRun run = requireRun(runId);
        if (isTerminal(run.getStatus())) {
            throw new IllegalArgumentException("运行已结束，无法恢复: status=" + run.getStatus());
        }
        if (STATUS_SUSPENDED.equals(run.getStatus())) {
            throw new IllegalArgumentException(
                    "运行处于人工审批挂起状态，请使用审批接口恢复: runId=" + runId);
        }
        GraphDefinition definition = GraphDefinition.parse(objectMapper, run.getDefinitionJson());
        GraphState state = GraphState.fromJson(objectMapper, run.getContextJson());
        state.runId(run.getId());
        String nodeId = StringUtils.hasText(run.getCurrentNodeId())
                ? run.getCurrentNodeId() : definition.startNodeId();
        run.setStatus(STATUS_RUNNING);
        runMapper.updateById(run);
        logStep(run, nodeId, definition.type(nodeId), "resume", "pending", "从检查点恢复运行");
        advance(run, definition, state, nodeId);
        return run;
    }

    /**
     * 人工审批：消费挂起现场，落决策并沿决策出口继续执行。
     *
     * <p>三条不可省的校验，缺一条都会让「审批」形同虚设：</p>
     * <ol>
     *   <li>运行必须处于 suspended —— 天然挡住重复审批（第二次开始状态已不是 suspended）；</li>
     *   <li>令牌必须与挂起时一致 —— 挡住拿着旧页面/旧链接误批另一次运行；</li>
     *   <li>挂起节点必须等于当前节点 —— 挡住 checkpoint 与状态字段不一致时的错位推进。</li>
     * </ol>
     *
     * <p>驳回且未配置驳回出口时，本次运行判失败而不是继续走成功路径：
     * 「驳回后照常执行」不会报错，只会静默做错事。</p>
     *
     * @param runId 运行实例 ID
     * @param approval 审批决策，不可为空
     * @return 运行实例（含最终状态；批准后可能再次挂起于下一个人工节点）
     * @throws IllegalArgumentException 状态、令牌或挂起节点不匹配时抛出
     */
    public WfWorkflowRun approve(Long runId, GraphApproval approval) {
        WfWorkflowRun run = requireRun(runId);
        if (!STATUS_SUSPENDED.equals(run.getStatus())) {
            throw new IllegalArgumentException("运行未处于待审批状态，无法审批: status=" + run.getStatus());
        }
        GraphDefinition definition = GraphDefinition.parse(objectMapper, run.getDefinitionJson());
        GraphState state = GraphState.fromJson(objectMapper, run.getContextJson());
        state.runId(run.getId());
        String nodeId = run.getCurrentNodeId();
        requirePendingMatch(state, nodeId, approval.resumeToken());
        String nodeType = definition.type(nodeId);
        state.putOutput(nodeId, approval.toPayload());
        state.markCompleted(nodeId);
        state.clearPending();
        run.setStatus(STATUS_RUNNING);
        logStep(run, nodeId, nodeType, approval.approved() ? "manual_approved" : "manual_rejected",
                approval.approved() ? "success" : "failed",
                approval.approved() ? "人工审批通过" : "人工审批驳回: " + safeComment(approval));
        String next = definition.approvalTarget(nodeId, approval.approved());
        if (next == null) {
            if (!approval.approved()) {
                fail(run, state, "人工审批驳回（未配置驳回出口）: " + safeComment(approval));
                return run;
            }
            checkpoint(run, state, null);
            finish(run, state);
            return run;
        }
        checkpoint(run, state, next);
        advance(run, definition, state, next);
        return run;
    }

    /** 校验挂起现场：节点一致 + 令牌一致。 */
    private void requirePendingMatch(GraphState state, String nodeId, String resumeToken) {
        if (!StringUtils.hasText(nodeId) || !nodeId.equals(state.pendingNodeId())) {
            throw new IllegalArgumentException(
                    "挂起节点与当前节点不一致，拒绝审批: current=" + nodeId
                            + ", pending=" + state.pendingNodeId());
        }
        String expected = state.resumeToken();
        if (!StringUtils.hasText(expected) || !expected.equals(resumeToken)) {
            throw new IllegalArgumentException("恢复令牌无效或已过期，拒绝审批");
        }
    }

    /** 审批意见的兜底展示文本。 */
    private String safeComment(GraphApproval approval) {
        return StringUtils.hasText(approval.comment()) ? approval.comment() : "无审批意见";
    }

    /** 主推进循环：逐节点执行直到结束、失败或挂起。 */
    private void advance(WfWorkflowRun run, GraphDefinition definition, GraphState state, String nodeId) {
        String current = nodeId;
        while (current != null) {
            if (Thread.currentThread().isInterrupted()) {
                fail(run, state, "执行被中断");
                return;
            }
            current = executeAndMove(run, definition, state, current);
        }
        // 循环退出的原因有三种：走到终点、失败、人工挂起。只有第一种才该收尾。
        // 无条件 finish 会把 failed / suspended 覆盖成 success —— 挂起被当成成功结束，
        // 审批现场随即丢失，后续任何审批都会以「运行已结束」被拒。
        if (STATUS_RUNNING.equals(run.getStatus())) {
            finish(run, state);
        }
    }

    /**
     * 执行单个节点并返回下一节点。
     *
     * <p>回边保护：目标节点已完成时视为回边，按 maxIterations 限制次数，
     * 超限则跳过循环体直接走该节点的普通出口。</p>
     */
    private String executeAndMove(WfWorkflowRun run, GraphDefinition definition,
            GraphState state, String nodeId) {
        JsonNode node = definition.node(nodeId);
        if (node == null) {
            fail(run, state, "节点不存在: " + nodeId);
            return null;
        }
        if (definition.edgeCount(nodeId) > 0) {
            int count = state.incrementLoop(nodeId);
            if (state.isCompleted(nodeId) && count > definition.maxIterations(nodeId)) {
                logStep(run, nodeId, definition.type(nodeId), "loop_limit", "success",
                        "回边达到上限 " + definition.maxIterations(nodeId) + " 次，退出循环");
                return definition.exitTarget(nodeId);
            }
        }
        logStep(run, nodeId, definition.type(nodeId), "enter", "pending", "进入节点");
        try {
            NodeOutcome outcome = nodeExecutor.execute(nodeId, node, state);
            return handleOutcome(run, definition, state, nodeId, outcome);
        } catch (Exception e) {
            return handleNodeError(run, definition, state, nodeId, e);
        }
    }

    /** 根据节点执行结果推进：结束、挂起、并行或普通出边。 */
    private String handleOutcome(WfWorkflowRun run, GraphDefinition definition,
            GraphState state, String nodeId, NodeOutcome outcome) {
        if (outcome.finished()) {
            state.markCompleted(nodeId);
            logStep(run, nodeId, definition.type(nodeId), "exit", "success", "流程结束");
            return null;
        }
        if (outcome.suspended()) {
            suspend(run, definition, state, nodeId, outcome.suspendPayload());
            return null;
        }
        if (outcome.parallel()) {
            fanout.runParallel(definition, state, outcome.children(), stepLogger(run));
            state.markCompleted(nodeId);
            logStep(run, nodeId, definition.type(nodeId), "exit", "success", "并行分支完成");
            String next = definition.nextTarget(nodeId);
            checkpoint(run, state, next);
            return next;
        }
        if (outcome.supervisor()) {
            return handleSupervisor(run, definition, state, nodeId, outcome);
        }
        state.putOutput(nodeId, outcome.output());
        state.markCompleted(nodeId);
        logStep(run, nodeId, definition.type(nodeId), "exit", "success", "节点执行完成");
        String next = outcome.conditionResult() == null
                ? definition.nextTarget(nodeId)
                : definition.branchTarget(nodeId, outcome.conditionResult());
        checkpoint(run, state, next);
        return next;
    }

    /**
     * 主管节点：扇出 worker → 汇总回执 → 按是否冲突决定出口与终态标记。
     *
     * <p>冲突时<b>默认继续</b>沿普通出边往下走，只把冲突记进状态，最终终态记为
     * {@code conflict}。原因：现有工单式协作在子任务失败时同样是"父任务进 conflict、
     * 其余结果保留、等人工处理"，而不是当场抛出。若这里直接判 failed，
     * 主管节点已经拿到的其他 worker 结果就失去了被复核的价值。</p>
     *
     * <p>需要走补偿支路的图可以配 {@code config.conflictTarget}。</p>
     */
    private String handleSupervisor(WfWorkflowRun run, GraphDefinition definition,
            GraphState state, String nodeId, NodeOutcome outcome) {
        Map<String, Object> summary = fanout.runSupervisor(definition, state,
                outcome.children(), outcome.strategy(), stepLogger(run));
        state.putOutput(nodeId, summary);
        state.markCompleted(nodeId);
        boolean conflicted = SupervisorAggregator.STATUS_CONFLICT.equals(summary.get("status"));
        if (conflicted) {
            state.markConflict(nodeId);
            logStep(run, nodeId, definition.type(nodeId), "exit", "conflict",
                    "主管汇总存在未成功回执: " + summary.get("conflicts"));
        } else {
            logStep(run, nodeId, definition.type(nodeId), "exit", "success",
                    "主管汇总全部回执成功（" + summary.get("total") + " 个 worker）");
        }
        String next = conflicted ? definition.conflictTarget(nodeId) : null;
        if (next == null) {
            next = definition.nextTarget(nodeId);
        }
        checkpoint(run, state, next);
        return next;
    }

    /** 把引擎的日志方法适配成扇出执行器可用的轨迹记录器。 */
    private NodeFanout.StepLogger stepLogger(WfWorkflowRun run) {
        return (nodeId, nodeType, action, result, message) ->
                logStep(run, nodeId, nodeType, action, result, message);
    }

    /** 节点异常：优先路由到 fallback 节点，否则整体失败。 */
    private String handleNodeError(WfWorkflowRun run, GraphDefinition definition,
            GraphState state, String nodeId, Exception error) {
        state.lastError(error.getMessage());
        logStep(run, nodeId, definition.type(nodeId), "error", "failed", "节点异常: " + error.getMessage());
        String fallbackId = findFallback(definition, state);
        if (fallbackId != null) {
            logStep(run, nodeId, definition.type(nodeId), "error", "failed", "已路由到异常兜底节点");
            checkpoint(run, state, fallbackId);
            return fallbackId;
        }
        fail(run, state, "节点执行失败: " + error.getMessage());
        return null;
    }

    /** 每节点完成后写 checkpoint：上下文快照 + 下一节点 ID。 */
    private void checkpoint(WfWorkflowRun run, GraphState state, String nextNodeId) {
        run.setContextJson(state.toJson(objectMapper));
        run.setCurrentNodeId(nextNodeId);
        run.setStatus(STATUS_RUNNING);
        runMapper.updateById(run);
    }

    /**
     * 人工挂起：置 suspended、记录挂起现场并下发恢复令牌，随后释放执行线程。
     *
     * <p>令牌**不写入运行日志**：审计日志的读者远比审批人多，令牌落日志等于把
     * 「谁能恢复这次运行」公开。令牌只经待审批查询接口下发给当前调用方。</p>
     */
    private void suspend(WfWorkflowRun run, GraphDefinition definition,
            GraphState state, String nodeId, Map<String, Object> payload) {
        String token = UUID.randomUUID().toString().replace("-", "");
        state.resumeToken(token);
        state.pending(nodeId, payload);
        run.setStatus(STATUS_SUSPENDED);
        run.setCurrentNodeId(nodeId);
        run.setContextJson(state.toJson(objectMapper));
        runMapper.updateById(run);
        logStep(run, nodeId, definition.type(nodeId), "manual_wait", "waiting",
                "等待人工介入：" + definition.name(nodeId));
    }

    /**
     * 正常结束（走完全部节点）。
     *
     * <p>终态区分 {@code success} 与 {@code conflict}：只要本次运行有编排节点记录了冲突回执，
     * 即便流程走完也不算 success —— 否则"有人没交差"会被记成"全部完成"，
     * 后续按 success 做的汇总统计与上线判断都会跟着错。</p>
     */
    private void finish(WfWorkflowRun run, GraphState state) {
        boolean conflicted = state.hasConflicts();
        run.setStatus(conflicted ? STATUS_CONFLICT : STATUS_SUCCESS);
        run.setCurrentNodeId(null);
        run.setOutputJson(outputJson(state));
        run.setContextJson(state.toJson(objectMapper));
        run.setFinishedAt(LocalDateTime.now());
        runMapper.updateById(run);
        logStep(run, "end", "end", "exit", conflicted ? "conflict" : "success",
                conflicted ? "图运行结束：部分失败，冲突节点=" + state.conflictedNodes() : "图运行成功");
    }

    /** 失败结束。 */
    private void fail(WfWorkflowRun run, GraphState state, String message) {
        run.setStatus(STATUS_FAILED);
        run.setErrorMessage(message);
        run.setOutputJson(outputJson(state));
        run.setFinishedAt(LocalDateTime.now());
        runMapper.updateById(run);
        logStep(run, "end", "end", "error", "failed", message);
    }

    /** 查找未被使用过的 fallback 节点。 */
    private String findFallback(GraphDefinition definition, GraphState state) {
        for (String id : definition.nodeIds()) {
            if ("fallback".equals(definition.type(id)) && !state.isCompleted(id)) {
                return id;
            }
        }
        return null;
    }

    /** 判断是否为终态（conflict 也是终态：走完了但需人工裁决）。 */
    private boolean isTerminal(String status) {
        return STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status)
                || STATUS_CANCELED.equals(status) || STATUS_CONFLICT.equals(status);
    }

    /** 写节点级执行轨迹（step）。 */
    private void logStep(WfWorkflowRun run, String nodeId, String nodeType,
            String action, String result, String message) {
        WfWorkflowRunLog log = new WfWorkflowRunLog();
        log.setRunId(run.getId());
        log.setNodeId(nodeId);
        log.setNodeName(nodeId);
        log.setNodeType(nodeType);
        log.setAction(action);
        log.setResult(result);
        log.setMessage(message);
        log.setBreakpoint(false);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    /** 读取运行实例，不存在时抛出业务异常。 */
    private WfWorkflowRun requireRun(Long runId) {
        WfWorkflowRun run = runMapper.selectById(runId);
        ValidationUtil.requireNotNull(run, "运行实例不存在: id=");
        return run;
    }

    /** 输出快照：节点输出 + 已完成节点，便于前端展示与回放。 */
    private String outputJson(GraphState state) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("nodeOutputs", state.outputs());
        output.put("executedNodes", state.completedNodes());
        output.put("conflictedNodes", state.conflictedNodes());
        return writeJson(output);
    }

    /** JSON 序列化，失败时返回空对象而非抛出。 */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
