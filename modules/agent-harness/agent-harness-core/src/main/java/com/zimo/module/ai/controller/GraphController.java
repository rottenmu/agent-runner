package com.zimo.module.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.zimo.framework.common.ApiResponse;
import com.zimo.module.ai.graph.GraphApproval;
import com.zimo.module.ai.graph.GraphRunService;
import com.zimo.module.ai.workflow.WfWorkflowRun;
import com.zimo.module.ai.workflow.WfWorkflowRunLog;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 运行时图编排接口。
 *
 * <p>所属模块：agent-harness 运行时内核。统一前缀 {@code /api/biz/ai/graph}，
 * 提供图运行的启动、恢复、状态与节点轨迹查询。</p>
 *
 * <p>权限规则：沿用现有管理端鉴权链路，不新增白名单；返回体统一为
 * {@link ApiResponse}（{@code code/msg/data} 信封）。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
@RestController
@RequestMapping("/api/biz/ai/graph")
public class GraphController {

    private final GraphRunService graphRunService;

    /**
     * 构造图编排控制器。
     *
     * @param graphRunService 图运行控制服务，不可为空
     */
    public GraphController(GraphRunService graphRunService) {
        this.graphRunService = graphRunService;
    }

    /**
     * 启动一次图运行。
     *
     * @param body 请求体：{@code workflowId} 必填；{@code input} 选填（Map）；
     *             {@code debug} 选填（默认 false）
     * @return 运行实例（含 status/currentNodeId/contextJson）
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/run")
    public ApiResponse<WfWorkflowRun> run(@RequestBody Map<String, Object> body) {
        Long workflowId = body.get("workflowId") == null
                ? null : Long.valueOf(String.valueOf(body.get("workflowId")));
        Map<String, Object> input = body.get("input") instanceof Map<?, ?> map
                ? (Map<String, Object>) map : null;
        boolean debug = Boolean.TRUE.equals(body.get("debug"));
        return ApiResponse.ok(graphRunService.start(workflowId, input, debug));
    }

    /**
     * 恢复一次未结束的运行（崩溃续跑）。
     *
     * <p>注意：人工挂起（suspended）的运行不由此端点恢复，需走
     * {@code /runs/{runId}/approve}，因为恢复必须带上裁决结果与令牌。</p>
     *
     * @param runId 运行实例 ID
     * @return 运行实例（含最终状态）
     */
    @PostMapping("/runs/{runId}/resume")
    public ApiResponse<WfWorkflowRun> resume(@PathVariable Long runId) {
        return ApiResponse.ok(graphRunService.resume(runId));
    }

    /**
     * 查询待人工处理事项（审批界面数据源）。
     *
     * @param runId 运行实例 ID
     * @return 挂起现场视图（节点名/提示语/表单/一次性令牌）
     */
    @GetMapping("/runs/{runId}/pending")
    public ApiResponse<Map<String, Object>> pending(@PathVariable Long runId) {
        return ApiResponse.ok(graphRunService.pending(runId));
    }

    /**
     * 人工审批：批准或驳回挂起中的运行。
     *
     * <p>请求体与既有协同审批接口（{@code /api/biz/ai/collab/tasks/{id}/approve}）同形，
     * 前端审批表单可直接复用，只需替换请求地址。</p>
     *
     * <p>安全口径：{@code approved} 缺省按 <b>驳回</b> 处理（fail-closed）。
     * 若缺字段默认批准，任何前端漏传或拼错字段名都会变成"自动放行"，
     * 而这类错误不会报错，只会在事后审计里表现为"没人批过却通过了"。</p>
     *
     * @param runId 运行实例 ID
     * @param body 请求体：{@code approved}（缺省 false）、{@code comment}、
     *             {@code operatorType}/{@code operatorId}（缺省取当前登录用户）、
     *             {@code resumeToken}（必填）、{@code form}（选填 Map）
     * @return 运行实例（含最终状态）
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/runs/{runId}/approve")
    public ApiResponse<WfWorkflowRun> approve(
            @PathVariable Long runId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> safeBody = body == null ? Map.of() : body;
        boolean approved = Boolean.parseBoolean(String.valueOf(safeBody.get("approved")));
        Map<String, Object> form = safeBody.get("form") instanceof Map<?, ?> map
                ? (Map<String, Object>) map : null;
        String operatorId = text(safeBody.get("operatorId"));
        GraphApproval approval = new GraphApproval(
                approved,
                text(safeBody.get("comment")),
                text(safeBody.get("operatorType")),
                StringUtils.hasText(operatorId) ? operatorId : currentUserId(),
                text(safeBody.get("resumeToken")),
                form);
        return ApiResponse.ok(graphRunService.approve(runId, approval));
    }

    /**
     * 查询运行状态（含 checkpoint 与当前节点）。
     *
     * @param runId 运行实例 ID
     * @return 运行实例
     */
    @GetMapping("/runs/{runId}")
    public ApiResponse<WfWorkflowRun> status(@PathVariable Long runId) {
        return ApiResponse.ok(graphRunService.status(runId));
    }

    /**
     * 查询节点级执行轨迹（step 列表）。
     *
     * @param runId 运行实例 ID
     * @return 轨迹列表，按写入顺序升序
     */
    @GetMapping("/runs/{runId}/logs")
    public ApiResponse<List<WfWorkflowRunLog>> logs(@PathVariable Long runId) {
        return ApiResponse.ok(graphRunService.logs(runId));
    }

    /** 读取字符串参数，null 与空白统一为空串。 */
    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** 当前登录用户 ID；未登录时沿用管理端既有缺省值。 */
    private String currentUserId() {
        try {
            if (StpUtil.isLogin()) {
                return String.valueOf(StpUtil.getLoginIdAsLong());
            }
        } catch (Exception ignored) {
            // 无会话上下文（如内部调用）时按缺省用户处理
        }
        return "u001";
    }
}
