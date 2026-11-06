package com.zimo.module.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zimo.framework.common.ApiResponse;
import com.zimo.module.ai.tool.GraphToolInvokeLog;
import com.zimo.module.ai.tool.GraphToolInvokeLogMapper;
import com.zimo.module.ai.tool.ToolMeta;
import com.zimo.module.ai.tool.ToolRegistryService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工具市场管理接口：注册工具、查看目录、试调用、查审计。
 *
 * <p>所属模块：agent-harness 管控面。控制器只做参数装配与结果包装，
 * 权限校验与审计全部下沉到 {@link ToolRegistryService} —— 这样"谁调工具都要校验、
 * 都要留痕"这条约束不依赖某个入口是否自觉。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
@RestController
@RequestMapping("/api/biz/ai/tool")
public class ToolController {

    /** 审计查询默认条数上限。 */
    private static final int DEFAULT_LOG_LIMIT = 50;

    private final ToolRegistryService registry;
    private final GraphToolInvokeLogMapper logMapper;

    public ToolController(ToolRegistryService registry, GraphToolInvokeLogMapper logMapper) {
        this.registry = registry;
        this.logMapper = logMapper;
    }

    /**
     * 注册或更新工具（按租户 + 工具标识幂等）。
     *
     * @param meta 工具元数据
     * @return 落库后的元数据
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> register(@RequestBody ToolMeta meta) {
        ToolMeta saved = registry.register(meta);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", saved.getId());
        result.put("toolId", saved.getToolId());
        result.put("tenantId", saved.getTenantId());
        result.put("providerType", saved.getProviderType());
        result.put("providerRef", saved.getProviderRef());
        result.put("version", saved.getVersion());
        result.put("enabled", saved.getEnabled());
        return ApiResponse.ok(result);
    }

    /**
     * 查询工具目录。
     *
     * @param tenantId 租户标识
     * @param enabledOnly 是否只返回启用项
     * @return 工具列表
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String tenantId,
                                                 @RequestParam(defaultValue = "false") boolean enabledOnly) {
        List<ToolMeta> tools = registry.list(tenantId, enabledOnly);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", tools.size());
        result.put("items", tools);
        return ApiResponse.ok(result);
    }

    /**
     * 删除工具（逻辑删除）。
     *
     * @param toolId 工具业务标识
     * @param tenantId 租户标识
     * @return {removed:true|false}
     */
    @DeleteMapping("/{toolId}")
    public ApiResponse<Map<String, Object>> remove(@PathVariable String toolId,
                                                   @RequestParam(required = false) String tenantId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toolId", toolId);
        result.put("removed", registry.remove(tenantId, toolId));
        return ApiResponse.ok(result);
    }

    /**
     * 试调用工具（与图节点走完全相同的治理链路）。
     *
     * @param toolId 工具业务标识
     * @param body 请求体：{@code tenantId}/{@code userId}/{@code args}
     * @return 调用结果（含 guarded 标记）
     */
    @PostMapping("/{toolId}/invoke")
    public ApiResponse<Map<String, Object>> invoke(@PathVariable String toolId,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> safeBody = body == null ? Map.of() : body;
        ToolRegistryService.ToolCallResult result = registry.invoke(
                new ToolRegistryService.ToolInvokeRequest(
                        toolId,
                        text(safeBody.get("tenantId")),
                        text(safeBody.get("userId")),
                        readArgs(safeBody.get("args")),
                        null,
                        "api"));
        return ApiResponse.ok(toMap(result));
    }

    /**
     * 查询调用审计。
     *
     * @param toolId 工具标识（可空，空表示查全部）
     * @param tenantId 租户标识（可空）
     * @param limit 返回条数上限
     * @return 审计记录列表
     */
    @GetMapping("/logs")
    public ApiResponse<Map<String, Object>> logs(@RequestParam(required = false) String toolId,
                                                 @RequestParam(required = false) String tenantId,
                                                 @RequestParam(required = false) Integer limit) {
        LambdaQueryWrapper<GraphToolInvokeLog> query = new LambdaQueryWrapper<GraphToolInvokeLog>()
                .eq(toolId != null && !toolId.isBlank(), GraphToolInvokeLog::getToolId, toolId)
                .eq(tenantId != null && !tenantId.isBlank(), GraphToolInvokeLog::getTenantId, tenantId)
                .orderByDesc(GraphToolInvokeLog::getId)
                .last("limit " + resolveLimit(limit));
        List<GraphToolInvokeLog> items = logMapper.selectList(query);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", items == null ? 0 : items.size());
        result.put("items", items == null ? List.of() : items);
        return ApiResponse.ok(result);
    }

    /** 调用结果转 Map（对外契约显式化，避免直接暴露内部 record 结构）。 */
    private Map<String, Object> toMap(ToolRegistryService.ToolCallResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", result.success());
        map.put("guarded", result.guarded());
        map.put("result", result.content());
        map.put("error", result.error());
        map.put("latencyMs", result.latencyMs());
        return map;
    }

    /** 读取调用参数；非对象类型按空参数处理。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readArgs(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    /** 审计查询条数：非法或超限值统一收敛到 [1, 500]。 */
    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LOG_LIMIT;
        }
        return Math.min(limit, 500);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
