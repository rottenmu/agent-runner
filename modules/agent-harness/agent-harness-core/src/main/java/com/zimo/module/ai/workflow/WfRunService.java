package com.zimo.module.ai.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zimo.module.ai.mapper.WfWorkflowRunLogMapper;
import com.zimo.module.ai.mapper.WfWorkflowRunMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工作流运行控制服务：启动 / 单步 / 恢复 / 人工介入 / 停止 / 日志查询。
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
public class WfRunService {

    private final WfWorkflowService workflowService;
    private final WfWorkflowEngine engine;
    private final WfWorkflowRunMapper runMapper;
    private final WfWorkflowRunLogMapper logMapper;

    public WfRunService(
            WfWorkflowService workflowService,
            WfWorkflowEngine engine,
            WfWorkflowRunMapper runMapper,
            WfWorkflowRunLogMapper logMapper) {
        this.workflowService = Objects.requireNonNull(workflowService, "workflowService must not be null");
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.runMapper = Objects.requireNonNull(runMapper, "runMapper must not be null");
        this.logMapper = Objects.requireNonNull(logMapper, "logMapper must not be null");
    }

    /** 启动流程（可调试模式 / 带输入）。 */
    public WfWorkflowRun start(Long workflowId, Map<String, Object> input, boolean debug) {
        WfWorkflow workflow = workflowService.requireWorkflow(workflowId);
        return engine.start(workflow, input, debug);
    }

    public WfWorkflowRun step(Long runId) {
        return engine.step(runId);
    }

    public WfWorkflowRun resume(Long runId) {
        return engine.resume(runId);
    }

    public WfWorkflowRun approve(Long runId, boolean approved) {
        return engine.approve(runId, approved);
    }

    public WfWorkflowRun stop(Long runId) {
        return engine.stop(runId);
    }

    /** 流程运行实例列表。 */
    public List<WfWorkflowRun> runs(Long workflowId) {
        return runMapper.selectList(Wrappers.<WfWorkflowRun>lambdaQuery()
                .eq(workflowId != null, WfWorkflowRun::getWorkflowId, workflowId)
                .orderByDesc(WfWorkflowRun::getId));
    }

    /** 运行日志（节点级轨迹）。 */
    public List<WfWorkflowRunLog> logs(Long runId) {
        return logMapper.selectList(Wrappers.<WfWorkflowRunLog>lambdaQuery()
                .eq(WfWorkflowRunLog::getRunId, runId)
                .orderByAsc(WfWorkflowRunLog::getId));
    }
}
