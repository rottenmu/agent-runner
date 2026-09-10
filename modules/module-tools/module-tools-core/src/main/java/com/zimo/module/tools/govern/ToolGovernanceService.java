package com.zimo.module.tools.govern;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.ai.mcp.ToolBridge;
import com.zimo.framework.ai.skill.AiSkillResult;
import com.zimo.framework.ai.skill.AiSkillRegistry;
import com.zimo.framework.common.security.SecurityFacade;
import com.zimo.module.tools.entity.ToolAgentPermission;
import com.zimo.module.tools.mapper.ToolAgentPermissionMapper;
import com.zimo.module.tools.entity.ToolDefinition;
import com.zimo.module.tools.mapper.ToolDefinitionMapper;
import com.zimo.module.tools.entity.ToolInvokeLog;
import com.zimo.module.tools.mapper.ToolInvokeLogMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * 工具调用治理服务：权限管控、频次限制、失败重试、熔断保护、调用日志。
 *
 * <p>同时作为 {@code ToolBridge} 接入 MCP：动态工具合并进 tools/list，调用经治理。
 * 集成安全管控：高危 URL 拦截、写操作审批、调用审计留痕。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class ToolGovernanceService implements ToolBridge {

    private static final Logger log = LoggerFactory.getLogger(ToolGovernanceService.class);
    private static final int MAX_RESULT_CHARS = 2000;

    private final ToolRegistry registry;
    private final ToolGovernor governor;
    private final ToolAgentPermissionMapper permissionMapper;
    private final ToolDefinitionMapper definitionMapper;
    private final ToolInvokeLogMapper invokeLogMapper;
    private final AiSkillRegistry skillRegistry;
    private final ObjectMapper objectMapper;
    private final Optional<SecurityFacade> security;

    public ToolGovernanceService(
            ToolRegistry registry,
            ToolGovernor governor,
            ToolAgentPermissionMapper permissionMapper,
            ToolDefinitionMapper definitionMapper,
            ToolInvokeLogMapper invokeLogMapper,
            AiSkillRegistry skillRegistry,
            ObjectMapper objectMapper,
            Optional<SecurityFacade> security) {
        this.registry = registry;
        this.governor = governor;
        this.permissionMapper = permissionMapper;
        this.definitionMapper = definitionMapper;
        this.invokeLogMapper = invokeLogMapper;
        this.skillRegistry = skillRegistry;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.security = security;
    }

    /* ---------------- 调用治理入口 ---------------- */

    /**
     * 治理调用（指定智能体，完整权限校验）。
     *
     * @param toolName 工具名
     * @param agentId 智能体 ID
     * @param arguments 参数
     * @param isAdmin 是否管理员（管理员跳过权限校验）
     * @return 结果
     */
    public String invoke(String toolName, String agentId, Map<String, Object> arguments, boolean isAdmin) {
        long start = System.currentTimeMillis();
        String result;
        boolean success;
        String error = null;
        int attempts = 0;
        try {
            if (!StringUtils.hasText(toolName)) {
                return fail("工具名称不能为空", agentId, toolName, arguments, 0, null);
            }
            if (!isAdmin && !allowed(toolName, agentId)) {
                return fail("智能体无权调用工具 " + toolName + "（请在工具权限中授权）",
                        agentId, toolName, arguments, 0, null);
            }
            if (!governor.tryAcquire(toolName)) {
                return fail("工具 " + toolName + " 调用过于频繁，请稍后再试",
                        agentId, toolName, arguments, 0, null);
            }
            if (!governor.allowCall(toolName)) {
                return fail("工具 " + toolName + " 已触发熔断保护（连续失败过多），冷却中",
                        agentId, toolName, arguments, 0, null);
            }
            // 安全：高危 URL 拦截（禁止 Agent 访问管理/危险接口）
            if (security.isPresent()) {
                String url = arguments == null ? null : strArg(arguments.get("url"), arguments.get("target"));
                if (StrUtil.isNotBlank(url)) {
                    String blocked = security.get().checkDangerousUrl(url);
                    if (blocked != null) {
                        security.get().audit("agent", agentId, "tool.blocked_url", "tool", toolName, blocked, false);
                        return fail(blocked, agentId, toolName, arguments, 0, null);
                    }
                }
            }
            // 安全：写操作转人工审批
            if (security.isPresent()) {
                String riskAction = writeRiskAction(toolName, arguments);
                if (riskAction != null) {
                    SecurityFacade.ApprovalRequest approval = security.get().requireApproval(
                            riskAction, "tool", toolName, arguments, agentId);
                    if (approval.required()) {
                        String msg = "高危操作已转人工审批（" + approval.message() + "），审批通过后自动执行";
                        security.get().audit("agent", agentId, "tool.approval", "tool", toolName, msg, true);
                        logInvoke(toolName, agentId, arguments, null, false, msg, System.currentTimeMillis() - start);
                        return "需审批: " + msg;
                    }
                }
            }
            int maxAttempts = readOnlyTool(toolName) ? 3 : 2;
            Exception lastError = null;
            for (attempts = 1; attempts <= maxAttempts; attempts++) {
                try {
                    result = executeTool(toolName, arguments);
                    success = true;
                    governor.recordSuccess(toolName);
                    logInvoke(toolName, agentId, arguments, result, true, null, System.currentTimeMillis() - start);
                    return result;
                } catch (Exception e) {
                    lastError = e;
                    log.warn("工具调用失败(尝试 {}/{}): {} - {}", attempts, maxAttempts, toolName, safeMessage(e));
                }
            }
            governor.recordFailure(toolName);
            error = "工具执行失败（已重试 " + (attempts - 1) + " 次）: " + safeMessage(lastError);
            logInvoke(toolName, agentId, arguments, null, false, error, System.currentTimeMillis() - start);
            return "调用失败: " + error;
        } catch (Exception e) {
            error = safeMessage(e);
            logInvoke(toolName, agentId, arguments, null, false, error, System.currentTimeMillis() - start);
            return "调用异常: " + error;
        }
    }

    private String fail(String message, String agentId, String toolName, Map<String, Object> arguments,
                        long latency, String result) {
        logInvoke(toolName, agentId, arguments, result, false, message, latency);
        return "调用被拒绝: " + message;
    }

    /** 执行工具（动态注册表优先，其次内置技能注册表）。 */
    private String executeTool(String toolName, Map<String, Object> arguments) throws Exception {
        long start = System.currentTimeMillis();
        String inputJson = toJson(arguments);
        try {
            ToolExecutor executor = registry.get(toolName);
            String result;
            if (executor != null) {
                result = executor.execute(arguments == null ? Map.of() : arguments);
            } else {
                AiSkillResult skillResult = skillRegistry.call(toolName, arguments == null ? Map.of() : arguments);
                if (skillResult == null) {
                    throw new IllegalArgumentException("工具不存在: " + toolName);
                }
                if (!skillResult.success()) {
                    throw new IllegalStateException(skillResult.content());
                }
                result = skillResult.content();
            }
            com.zimo.framework.ai.observ.TraceCollector.step("tool_call", toolName,
                    inputJson, truncate(result, 1500), System.currentTimeMillis() - start, "ok");
            return result;
        } catch (Exception e) {
            com.zimo.framework.ai.observ.TraceCollector.step("tool_call", toolName,
                    inputJson, "{\"error\":\"" + safeMessage(e) + "\"}", System.currentTimeMillis() - start, "failed");
            throw e;
        }
    }

    /** 权限判定：权限表记录优先，其次动态工具默认拒绝、内置工具默认允许。 */
    private boolean allowed(String toolName, String agentId) {
        if (!StringUtils.hasText(agentId)) {
            return true; // 未指定智能体视为服务级调用
        }
        ToolAgentPermission permission = permissionMapper.selectOne(Wrappers.<ToolAgentPermission>lambdaQuery()
                .eq(ToolAgentPermission::getToolName, toolName)
                .eq(ToolAgentPermission::getAgentId, agentId));
        if (permission != null) {
            return Boolean.TRUE.equals(permission.getEnabled());
        }
        if (registry.contains(toolName)) {
            ToolDefinition definition = definitionMapper.selectOne(Wrappers.<ToolDefinition>lambdaQuery()
                    .eq(ToolDefinition::getName, toolName));
            return definition != null && Boolean.TRUE.equals(definition.getDefaultAllow());
        }
        return true; // 内置工具默认允许
    }

    private boolean readOnlyTool(String toolName) {
        ToolExecutor executor = registry.get(toolName);
        if (executor != null) {
            return executor.readOnly();
        }
        return skillRegistry.list().stream()
                .anyMatch(d -> toolName.equals(d.name()) && d.readOnly());
    }

    private void logInvoke(String toolName, String agentId, Map<String, Object> arguments,
                           String result, boolean success, String error, long latencyMs) {
        try {
            ToolInvokeLog logEntity = new ToolInvokeLog();
            logEntity.setToolName(toolName);
            logEntity.setAgentId(agentId);
            logEntity.setParams(truncate(toJson(arguments), 1000));
            logEntity.setResult(truncate(result, MAX_RESULT_CHARS));
            logEntity.setSuccess(success);
            logEntity.setError(truncate(error, 500));
            logEntity.setLatencyMs(latencyMs);
            logEntity.setCreatedAt(LocalDateTime.now());
            invokeLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.warn("工具调用日志记录失败: {}", safeMessage(e));
        }
        // 安全：审计留痕（每次工具调用永久留痕）
        if (security.isPresent()) {
            try {
                security.get().audit("agent", agentId, success ? "tool.call" : "tool.call_failed",
                        "tool", toolName,
                        "{\"params\":" + truncate(toJson(arguments), 500)
                                + (error == null ? "" : ",\"error\":\"" + safeJson(error) + "\"") + "}",
                        success);
            } catch (Exception ignored) {
            }
        }
    }

    /** 判定写类工具对应的高危动作（返回 null 表示非写操作）。 */
    private String writeRiskAction(String toolName, Map<String, Object> arguments) {
        if (toolName == null) {
            return null;
        }
        String action = arguments == null ? "" : String.valueOf(arguments.getOrDefault("action", ""));
        switch (toolName) {
            case "email", "email_send" -> {
                return "send_email";
            }
            case "file" -> {
                if (action.contains("delete")) {
                    return "delete_data";
                }
                if (action.contains("write") || action.contains("append")) {
                    return "update_db";
                }
            }
            case "schedule" -> {
                if ("create".equals(action)) {
                    return "external_approval";
                }
            }
            case "code" -> {
                return "execute_ddl";
            }
            case "http_request", "http" -> {
                String method = arguments == null ? "GET" : String.valueOf(arguments.getOrDefault("method", "GET"));
                if (!"GET".equalsIgnoreCase(method)) {
                    return "external_approval";
                }
            }
            default -> {
                // 动态插件工具按定义方法判断
            }
        }
        return null;
    }

    private static String strArg(Object a, Object b) {
        return a != null && !String.valueOf(a).isBlank() ? String.valueOf(a)
                : (b == null ? null : String.valueOf(b));
    }

    private static String safeJson(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("\n", " ");
    }

    /* ---------------- 权限管理 ---------------- */

    /** 授权/取消授权：智能体 × 工具。 */
    public ToolAgentPermission grant(String toolName, String agentId, boolean enabled) {
        if (!StringUtils.hasText(toolName) || !StringUtils.hasText(agentId)) {
            throw new IllegalArgumentException("toolName 与 agentId 不能为空");
        }
        ToolAgentPermission permission = permissionMapper.selectOne(Wrappers.<ToolAgentPermission>lambdaQuery()
                .eq(ToolAgentPermission::getToolName, toolName)
                .eq(ToolAgentPermission::getAgentId, agentId));
        if (permission == null) {
            permission = new ToolAgentPermission();
            permission.setToolName(toolName);
            permission.setAgentId(agentId);
            permission.setEnabled(enabled);
            permission.setCreatedAt(LocalDateTime.now());
            permissionMapper.insert(permission);
        } else {
            permission.setEnabled(enabled);
            permissionMapper.updateById(permission);
        }
        return permission;
    }

    /** 权限列表。 */
    public List<ToolAgentPermission> listPermissions(String toolName, String agentId) {
        return permissionMapper.selectList(Wrappers.<ToolAgentPermission>lambdaQuery()
                .eq(StringUtils.hasText(toolName), ToolAgentPermission::getToolName, toolName)
                .eq(StringUtils.hasText(agentId), ToolAgentPermission::getAgentId, agentId)
                .orderByAsc(ToolAgentPermission::getId));
    }

    /* ---------------- 日志与治理状态 ---------------- */

    /** 调用日志。 */
    public List<ToolInvokeLog> listLogs(String toolName, String agentId, Boolean success, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return invokeLogMapper.selectList(Wrappers.<ToolInvokeLog>lambdaQuery()
                .eq(StringUtils.hasText(toolName), ToolInvokeLog::getToolName, toolName)
                .eq(StringUtils.hasText(agentId), ToolInvokeLog::getAgentId, agentId)
                .eq(success != null, ToolInvokeLog::getSuccess, success)
                .orderByDesc(ToolInvokeLog::getId)
                .last("LIMIT " + safeLimit));
    }

    /** 治理状态（限流/熔断）。 */
    public List<Map<String, Object>> governanceStates() {
        List<Map<String, Object>> states = new ArrayList<>();
        java.util.Set<String> names = new java.util.TreeSet<>();
        for (ToolExecutor executor : registry.list()) {
            names.add(executor.name());
        }
        for (String name : names) {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("toolName", name);
            state.put("circuit", governor.circuitState(name));
            state.put("ratePerSecond", ToolGovernor.DEFAULT_RATE_PER_SECOND);
            state.put("capacity", ToolGovernor.DEFAULT_CAPACITY);
            states.add(state);
        }
        return states;
    }

    /** 复位熔断与限流。 */
    public void resetGovernance(String toolName) {
        if (StringUtils.hasText(toolName)) {
            governor.reset(toolName.trim());
        }
    }

    /* ---------------- ToolBridge（MCP 集成） ---------------- */

    @Override
    public List<Map<String, Object>> listTools() {
        return registry.describeTools();
    }

    @Override
    public AiSkillResult call(String name, Map<String, Object> arguments) {
        String content = invoke(name, null, arguments, true);
        if (content.startsWith("调用被拒绝") || content.startsWith("调用失败") || content.startsWith("调用异常")) {
            return AiSkillResult.fail(content);
        }
        return AiSkillResult.ok(content);
    }

    /* ---------------- 工具 ---------------- */

    private boolean readOnly(String name) {
        ToolExecutor executor = registry.get(name);
        return executor != null && executor.readOnly();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return StrUtil.isBlank(message)
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}
