package com.zimo.module.ai.controller;

import com.zimo.framework.common.ApiResponse;
import com.zimo.module.ai.workflow.WfRunService;
import com.zimo.module.ai.workflow.WfWorkflow;
import com.zimo.module.ai.workflow.WfWorkflowRun;
import com.zimo.module.ai.workflow.WfWorkflowRunLog;
import com.zimo.module.ai.workflow.WfWorkflowService;
import com.zimo.module.ai.workflow.WfWorkflowVersion;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作流编排接口：流程 CRUD、发布版本、运行控制（调试/断点/人工介入）。
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@RestController
@RequestMapping("/api/biz/wf/workflows")
public class WfWorkflowController {

    private final WfWorkflowService workflowService;
    private final WfRunService runService;

    public WfWorkflowController(WfWorkflowService workflowService, WfRunService runService) {
        this.workflowService = workflowService;
        this.runService = runService;
    }

    /* ---------- 流程定义 ---------- */

    @GetMapping
    public ApiResponse<List<WfWorkflow>> list() {
        return ApiResponse.ok(workflowService.list());
    }

    @PostMapping
    public ApiResponse<WfWorkflow> create(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(workflowService.create(
                body.get("name"), body.get("description"), body.get("definitionJson")));
    }

    @PutMapping("/{id}")
    public ApiResponse<WfWorkflow> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(workflowService.update(
                id, body.get("name"), body.get("description"), body.get("definitionJson")));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        workflowService.delete(id);
        return ApiResponse.ok();
    }

    /** 发布流程（保存版本快照，版本号 +1）。 */
    @PostMapping("/{id}/publish")
    public ApiResponse<WfWorkflow> publish(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String changeNote = body == null ? null : body.get("changeNote");
        return ApiResponse.ok(workflowService.publish(id, changeNote));
    }

    /** 流程版本列表。 */
    @GetMapping("/{id}/versions")
    public ApiResponse<List<WfWorkflowVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(workflowService.versions(id));
    }

    /* ---------- 运行控制 ---------- */

    /** 启动运行（debug=true 进入断点调试模式）。 */
    @PostMapping("/{id}/run")
    public ApiResponse<WfWorkflowRun> startRun(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> input = body == null ? null : (Map<String, Object>) body.get("input");
        boolean debug = body != null && Boolean.TRUE.equals(body.get("debug"));
        return ApiResponse.ok(runService.start(id, input, debug));
    }

    /** 单步执行（断点暂停状态）。 */
    @PostMapping("/runs/{runId}/step")
    public ApiResponse<WfWorkflowRun> step(@PathVariable Long runId) {
        return ApiResponse.ok(runService.step(runId));
    }

    /** 恢复执行（继续到下一断点或结束）。 */
    @PostMapping("/runs/{runId}/resume")
    public ApiResponse<WfWorkflowRun> resume(@PathVariable Long runId) {
        return ApiResponse.ok(runService.resume(runId));
    }

    /** 人工介入：批准 / 驳回。 */
    @PostMapping("/runs/{runId}/approve")
    public ApiResponse<WfWorkflowRun> approve(
            @PathVariable Long runId,
            @RequestBody Map<String, Boolean> body) {
        boolean approved = body == null || !Boolean.FALSE.equals(body.get("approved"));
        return ApiResponse.ok(runService.approve(runId, approved));
    }

    /** 停止运行。 */
    @PostMapping("/runs/{runId}/stop")
    public ApiResponse<WfWorkflowRun> stop(@PathVariable Long runId) {
        return ApiResponse.ok(runService.stop(runId));
    }

    /** 流程运行实例列表。 */
    @GetMapping("/{id}/runs")
    public ApiResponse<List<WfWorkflowRun>> runs(@PathVariable Long id) {
        return ApiResponse.ok(runService.runs(id));
    }

    /** 运行日志（节点轨迹）。 */
    @GetMapping("/runs/{runId}/logs")
    public ApiResponse<List<WfWorkflowRunLog>> logs(@PathVariable Long runId) {
        return ApiResponse.ok(runService.logs(runId));
    }

    /** 全部运行实例（跨流程，调试台用）。 */
    @GetMapping("/runs")
    public ApiResponse<List<WfWorkflowRun>> allRuns(@RequestParam(required = false) Long workflowId) {
        return ApiResponse.ok(runService.runs(workflowId));
    }
}
