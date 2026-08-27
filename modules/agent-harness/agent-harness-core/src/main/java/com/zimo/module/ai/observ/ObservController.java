package com.zimo.module.ai.observ;

import com.zimo.framework.common.ApiResponse;
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
 * 观测中心接口：执行链路 Trace、自动化测试、监控告警、数据大盘。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
@RestController
@RequestMapping("/api/biz/ai/observ")
public class ObservController {

    private final ObservTraceService traceService;
    private final TestRunnerService testRunnerService;
    private final AlertService alertService;
    private final DashboardService dashboardService;
    private final SessionEventLogService eventLogService;

    public ObservController(ObservTraceService traceService,
                            TestRunnerService testRunnerService,
                            AlertService alertService,
                            DashboardService dashboardService,
                            SessionEventLogService eventLogService) {
        this.traceService = traceService;
        this.testRunnerService = testRunnerService;
        this.alertService = alertService;
        this.dashboardService = dashboardService;
        this.eventLogService = eventLogService;
    }

    /* ---------------- 数据大盘 ---------------- */

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(
            @RequestParam(defaultValue = "7") int days) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("summary", dashboardService.summary());
        result.put("trend", dashboardService.trend(days));
        result.put("byAgent", dashboardService.byAgent(10));
        return ApiResponse.ok(result);
    }

    /* ---------------- 执行链路 Trace ---------------- */

    @GetMapping("/traces")
    public ApiResponse<List<ObservTrace>> traces(
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String traceKey,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(traceService.listTraces(agentId, status, traceKey, limit));
    }

    /**
     * 链路详情（执行路径时间线）。{id} 支持数据库主键（纯数字）或链路 ID traceKey
     * （如前端下发的 32 位 traceId）。
     */
    @GetMapping("/traces/{id}")
    public ApiResponse<Map<String, Object>> traceDetail(@PathVariable String id) {
        if (id != null && id.matches("\\d+")) {
            return ApiResponse.ok(traceService.traceDetail(Long.parseLong(id)));
        }
        return ApiResponse.ok(traceService.traceDetailByKey(id));
    }

    /** 会话回放（步骤序列），支持主键或 traceKey。 */
    /* ================= 追加式会话事件日志（append-only session log） ================= */

    /** 链路完整事件流（BEGIN → STEP… → END，seq 升序）。 */
    @GetMapping("/events/{traceId}")
    public ApiResponse<java.util.List<SessionEventLog>> events(@PathVariable String traceId) {
        return ApiResponse.ok(eventLogService.listByTraceId(traceId));
    }

    /** Token 计量 / 事件统计（按链路）。 */
    @GetMapping("/events/{traceId}/token-summary")
    public ApiResponse<java.util.Map<String, Object>> tokenSummary(@PathVariable String traceId) {
        return ApiResponse.ok(eventLogService.tokenSummary(traceId));
    }

    @GetMapping("/traces/{id}/replay")
    public ApiResponse<Map<String, Object>> replay(@PathVariable String id) {
        if (id != null && id.matches("\\d+")) {
            return ApiResponse.ok(traceService.replay(Long.parseLong(id)));
        }
        return ApiResponse.ok(traceService.traceDetailByKey(id));
    }

    /* ---------------- 自动化测试 ---------------- */

    @GetMapping("/test-cases")
    public ApiResponse<List<ObservTestCase>> testCases(
            @RequestParam(required = false) String category) {
        return ApiResponse.ok(testRunnerService.listCases(category));
    }

    @PostMapping("/test-cases")
    public ApiResponse<ObservTestCase> createCase(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> tags = body.get("tags") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        return ApiResponse.ok(testRunnerService.createCase(
                str(body.get("name")), str(body.get("category")), str(body.get("input")),
                str(body.get("expected")), str(body.get("agentId")), tags,
                body.get("enabled") == null || Boolean.parseBoolean(String.valueOf(body.get("enabled")))));
    }

    @PutMapping("/test-cases/{id}")
    public ApiResponse<ObservTestCase> updateCase(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(testRunnerService.updateCase(id, body));
    }

    @DeleteMapping("/test-cases/{id}")
    public ApiResponse<Void> deleteCase(@PathVariable Long id) {
        testRunnerService.deleteCase(id);
        return ApiResponse.ok();
    }

    /** 运行批量测评。 */
    @PostMapping("/test-runs")
    public ApiResponse<Map<String, Object>> runTests(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> caseIds = body.get("caseIds") instanceof List<?> list
                ? list.stream().filter(v -> v != null).map(v -> Long.valueOf(String.valueOf(v))).toList()
                : List.of();
        return ApiResponse.ok(testRunnerService.runTests(str(body.get("name")), caseIds));
    }

    @GetMapping("/test-runs")
    public ApiResponse<List<ObservTestRun>> testRuns(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(testRunnerService.listRuns(limit));
    }

    @GetMapping("/test-runs/{id}/report")
    public ApiResponse<Map<String, Object>> testReport(@PathVariable Long id) {
        return ApiResponse.ok(testRunnerService.runReport(id));
    }

    /* ---------------- 监控告警 ---------------- */

    @GetMapping("/alerts/rules")
    public ApiResponse<List<ObservAlertRule>> alertRules() {
        return ApiResponse.ok(alertService.listRules());
    }

    @PutMapping("/alerts/rules/{id}")
    public ApiResponse<ObservAlertRule> updateRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(alertService.updateRule(id, body));
    }

    @GetMapping("/alerts/events")
    public ApiResponse<List<ObservAlertEvent>> alertEvents(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Boolean handled,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(alertService.listEvents(type, level, handled, limit));
    }

    @PostMapping("/alerts/events/{id}/handle")
    public ApiResponse<Void> handleEvent(@PathVariable Long id) {
        alertService.handleEvent(id);
        return ApiResponse.ok();
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
