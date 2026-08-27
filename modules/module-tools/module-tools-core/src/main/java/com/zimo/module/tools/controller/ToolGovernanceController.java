package com.zimo.module.tools.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.zimo.framework.common.ApiResponse;
import com.zimo.module.tools.entity.ToolAgentPermission;
import com.zimo.module.tools.entity.ToolDefinition;
import com.zimo.module.tools.entity.ToolInvokeLog;
import com.zimo.module.tools.entity.ToolPlugin;
import com.zimo.module.tools.service.ToolPluginService;
import com.zimo.module.tools.entity.ToolPythonScript;
import com.zimo.module.tools.service.ToolScriptService;
import com.zimo.module.tools.govern.ToolGovernanceService;
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
 * 工具中心接口：治理调用、插件市场、自定义脚本、权限管控、调用日志、熔断状态。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
@RestController
@RequestMapping("/api/biz/ai/tools")
public class ToolGovernanceController {

    private final ToolGovernanceService governanceService;
    private final ToolPluginService pluginService;
    private final ToolScriptService scriptService;

    public ToolGovernanceController(ToolGovernanceService governanceService,
                                    ToolPluginService pluginService,
                                    ToolScriptService scriptService) {
        this.governanceService = governanceService;
        this.pluginService = pluginService;
        this.scriptService = scriptService;
    }

    /* ---------------- 治理调用 ---------------- */

    /** 工具调用（完整治理：权限/限流/重试/熔断/日志）。 */
    @PostMapping("/invoke")
    public ApiResponse<Map<String, Object>> invoke(@RequestBody Map<String, Object> body) {
        String toolName = str(body.get("toolName"));
        String agentId = str(body.get("agentId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = body.get("arguments") instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
        long start = System.currentTimeMillis();
        String result = governanceService.invoke(toolName, agentId, arguments, isAdmin());
        return ApiResponse.ok(Map.of(
                "toolName", toolName == null ? "" : toolName,
                "agentId", agentId,
                "result", result,
                "latencyMs", System.currentTimeMillis() - start));
    }

    /* ---------------- 权限管控 ---------------- */

    @PostMapping("/permissions")
    public ApiResponse<ToolAgentPermission> grantPermission(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(governanceService.grant(
                body.get("toolName"), body.get("agentId"),
                body.get("enabled") == null || Boolean.parseBoolean(body.get("enabled"))));
    }

    @GetMapping("/permissions")
    public ApiResponse<List<ToolAgentPermission>> permissions(
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) String agentId) {
        return ApiResponse.ok(governanceService.listPermissions(toolName, agentId));
    }

    /* ---------------- 调用日志与治理状态 ---------------- */

    @GetMapping("/logs")
    public ApiResponse<List<ToolInvokeLog>> logs(
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) Boolean success,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(governanceService.listLogs(toolName, agentId, success, limit));
    }

    @GetMapping("/governance")
    public ApiResponse<List<Map<String, Object>>> governance() {
        return ApiResponse.ok(governanceService.governanceStates());
    }

    @PostMapping("/governance/reset")
    public ApiResponse<Void> resetGovernance(@RequestBody Map<String, String> body) {
        governanceService.resetGovernance(body.get("toolName"));
        return ApiResponse.ok();
    }

    /* ---------------- 插件市场 ---------------- */

    @GetMapping("/plugins")
    public ApiResponse<List<Map<String, Object>>> plugins() {
        return ApiResponse.ok(pluginService.listPlugins());
    }

    @GetMapping("/plugins/{id}/tools")
    public ApiResponse<List<ToolDefinition>> pluginTools(@PathVariable Long id) {
        return ApiResponse.ok(pluginService.pluginTools(id));
    }

    @PostMapping("/plugins/{id}/enable")
    public ApiResponse<Void> enablePlugin(@PathVariable Long id) {
        pluginService.enablePlugin(id);
        return ApiResponse.ok();
    }

    @PostMapping("/plugins/{id}/disable")
    public ApiResponse<Void> disablePlugin(@PathVariable Long id) {
        pluginService.disablePlugin(id);
        return ApiResponse.ok();
    }

    @PutMapping("/plugins/{id}/config")
    public ApiResponse<ToolPlugin> updatePluginConfig(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(pluginService.updateConfig(id, body));
    }

    /* ---------------- 自定义脚本 ---------------- */

    @GetMapping("/scripts")
    public ApiResponse<List<ToolPythonScript>> scripts() {
        return ApiResponse.ok(scriptService.listScripts());
    }

    @PostMapping("/scripts")
    public ApiResponse<ToolPythonScript> createScript(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(scriptService.create(body.get("name"), body.get("description"), body.get("script")));
    }

    @PutMapping("/scripts/{id}")
    public ApiResponse<ToolPythonScript> updateScript(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ApiResponse.ok(scriptService.update(id, body.get("name"), body.get("description"), body.get("script")));
    }

    @PostMapping("/scripts/{id}/enabled")
    public ApiResponse<Void> setScriptEnabled(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        boolean enabled = body.get("enabled") == null || Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        scriptService.setEnabled(id, enabled);
        return ApiResponse.ok();
    }

    @DeleteMapping("/scripts/{id}")
    public ApiResponse<Void> deleteScript(@PathVariable Long id) {
        scriptService.delete(id);
        return ApiResponse.ok();
    }

    /* ---------------- 上下文 ---------------- */

    private boolean isAdmin() {
        try {
            return StpUtil.isLogin() && StpUtil.hasRole("admin");
        } catch (Exception e) {
            return false;
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
