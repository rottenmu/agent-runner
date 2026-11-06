package com.zimo.module.ai.graph;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.common.validation.ValidationUtil;
import com.zimo.module.ai.mapper.WfWorkflowRunLogMapper;
import com.zimo.module.ai.mapper.WfWorkflowRunMapper;
import com.zimo.module.ai.workflow.WfWorkflow;
import com.zimo.module.ai.workflow.WfWorkflowRun;
import com.zimo.module.ai.workflow.WfWorkflowRunLog;
import com.zimo.module.ai.workflow.WfWorkflowService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 运行时图运行控制服务。
 *
 * <p>所属模块：agent-harness 运行时内核。对外提供图运行的启动、恢复、状态查询与
 * 节点轨迹查询能力，是 Controller 与 {@link GraphEngine} 之间的业务边界。</p>
 *
 * <p>事务边界：本服务不开启事务。图运行是长流程且需要每节点独立提交 checkpoint，
 * 若整体包在一个事务内，检查点将随事务回滚而失效，破坏崩溃续跑语义。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class GraphRunService {

    private final WfWorkflowService workflowService;
    private final GraphEngine engine;
    private final WfWorkflowRunMapper runMapper;
    private final WfWorkflowRunLogMapper logMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构造图运行控制服务。
     *
     * @param workflowService 工作流定义服务，用于校验并加载定义，不可为空
     * @param engine 图执行引擎，不可为空
     * @param runMapper 运行实例 Mapper，不可为空
     * @param logMapper 运行日志 Mapper，不可为空
     * @param objectMapper JSON 映射器，用于解析 checkpoint 与图定义，不可为空
     */
    public GraphRunService(
            WfWorkflowService workflowService,
            GraphEngine engine,
            WfWorkflowRunMapper runMapper,
            WfWorkflowRunLogMapper logMapper,
            ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.engine = engine;
        this.runMapper = runMapper;
        this.logMapper = logMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 启动一次图运行。
     *
     * @param workflowId 工作流定义 ID，不可为空且必须存在
     * @param input 图输入参数，可为空
     * @param debug 是否调试模式
     * @return 运行实例（含最终状态）
     * @throws IllegalArgumentException 工作流不存在时抛出
     */
    public WfWorkflowRun start(Long workflowId, Map<String, Object> input, boolean debug) {
        WfWorkflow workflow = workflowService.requireWorkflow(workflowId);
        return engine.start(workflow, input, debug);
    }

    /**
     * 恢复一次未结束的运行（崩溃续跑或人工挂起恢复）。
     *
     * @param runId 运行实例 ID
     * @return 运行实例（含最终状态）
     * @throws IllegalArgumentException 运行不存在或已结束
     */
    public WfWorkflowRun resume(Long runId) {
        return engine.resume(runId);
    }

    /**
     * 人工审批：批准或驳回挂起中的运行。
     *
     * @param runId 运行实例 ID
     * @param approval 审批决策，不可为空
     * @return 运行实例（含最终状态）
     * @throws IllegalArgumentException 运行未挂起、令牌无效或挂起节点错位时抛出
     */
    public WfWorkflowRun approve(Long runId, GraphApproval approval) {
        return engine.approve(runId, approval);
    }

    /**
     * 查询待人工处理事项（审批界面数据源）。
     *
     * <p>返回挂起现场的全部渲染要素（节点名、审批提示语、表单定义）与一次性恢复令牌，
     * 前端审批组件据此渲染，不需要再拉一次图定义，也不会出现「图已改版、提示语对不上」。</p>
     *
     * @param runId 运行实例 ID
     * @return 待处理视图；运行不在挂起态时 {@code awaitingHuman=false} 且不含令牌
     */
    public Map<String, Object> pending(Long runId) {
        WfWorkflowRun run = status(runId);
        GraphState state = GraphState.fromJson(objectMapper, run.getContextJson());
        GraphDefinition definition = GraphDefinition.parse(objectMapper, run.getDefinitionJson());
        boolean awaiting = GraphEngine.STATUS_SUSPENDED.equals(run.getStatus());
        String nodeId = run.getCurrentNodeId();
        Map<String, Object> view = new LinkedHashMap<>();
        view.putAll(state.pendingMeta());
        view.put("runId", run.getId());
        view.put("workflowId", run.getWorkflowId());
        view.put("status", run.getStatus());
        view.put("awaitingHuman", awaiting);
        view.put("nodeId", nodeId);
        view.put("nodeType", nodeId == null ? null : definition.type(nodeId));
        view.put("nodeName", nodeId == null ? null : definition.name(nodeId));
        if (awaiting) {
            view.put("resumeToken", state.resumeToken());
        }
        return view;
    }

    /**
     * 查询运行状态。
     *
     * @param runId 运行实例 ID
     * @return 运行实例；不存在时抛出业务异常
     */
    public WfWorkflowRun status(Long runId) {
        WfWorkflowRun run = runMapper.selectById(runId);
        ValidationUtil.requireNotNull(run, "运行实例不存在: id=");
        return run;
    }

    /**
     * 查询节点级执行轨迹（step 列表），按写入顺序升序返回。
     *
     * @param runId 运行实例 ID
     * @return 轨迹列表；无记录返回空列表
     */
    public List<WfWorkflowRunLog> logs(Long runId) {
        return logMapper.selectList(Wrappers.<WfWorkflowRunLog>lambdaQuery()
                .eq(WfWorkflowRunLog::getRunId, runId)
                .orderByAsc(WfWorkflowRunLog::getId));
    }
}
