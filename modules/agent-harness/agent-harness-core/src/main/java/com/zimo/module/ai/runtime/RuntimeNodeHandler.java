package com.zimo.module.ai.runtime;

import com.zimo.module.ai.tool.ToolRegistryService;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图节点运行时处理器：把 {@code agent} / {@code tool} 两类节点接到运行时与工具市场上。
 *
 * <p>为什么要单独一层：{@code GraphNodeExecutor} 的职责是"节点能力 + 参数渲染"，
 * 运行时调用的语义（闸门、路由、工具治理、记忆留痕）属于另一件事。
 * 混在一起会让节点执行器同时承担编排与执行两种变化原因，任何一侧改动都要动它。</p>
 *
 * <p>本类只做<b>结果归一</b>：把 {@link AgentRunResult} 与
 * {@link ToolRegistryService.ToolCallResult} 转成图状态可承载的输出 Map，
 * 并保证失败信息出现在输出里（而不是以异常形式中断整张图）。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class RuntimeNodeHandler {

    private final AgentRuntime agentRuntime;
    private final ToolRegistryService toolRegistry;

    /**
     * 构造运行时节点处理器。
     *
     * @param agentRuntime 统一智能体运行时，可为 {@code null}（agent 节点不可用）
     * @param toolRegistry 工具市场治理服务，可为 {@code null}（tool 节点不可用）
     */
    public RuntimeNodeHandler(AgentRuntime agentRuntime, ToolRegistryService toolRegistry) {
        this.agentRuntime = agentRuntime;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 执行 agent 节点。
     *
     * @param request 智能体执行请求
     * @return 节点输出（含 status / reply / toolIds）
     * @throws IllegalStateException 运行时尚不可用时抛出（属配置错误，应显式失败）
     */
    public Map<String, Object> executeAgent(AgentRunRequest request) {
        if (agentRuntime == null) {
            throw new IllegalStateException("agent 节点未装配运行时（AgentRuntime）");
        }
        AgentRunResult result = agentRuntime.run(request);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("agentId", result.agentId());
        output.put("agentName", result.agentName());
        output.put("status", result.status());
        output.put("reply", result.reply());
        output.put("toolIds", result.toolIds());
        output.put("shortCircuited", result.shortCircuited());
        output.put("elapsedMs", result.elapsedMs());
        if (result.error() != null) {
            output.put("error", result.error());
        }
        return output;
    }

    /**
     * 执行 tool 节点。
     *
     * <p>权限不足时返回 {@code guarded=true} 的输出而不是抛异常：这让图能继续走
     * 兜底分支，也避免"某个工具没权限导致整条流程失败"这类放大伤害。</p>
     *
     * @param request 工具调用请求
     * @return 节点输出（含 status / guarded / result）
     * @throws IllegalStateException 工具市场未装配时抛出
     */
    public Map<String, Object> executeTool(ToolRegistryService.ToolInvokeRequest request) {
        if (toolRegistry == null) {
            throw new IllegalStateException("tool 节点未装配工具市场（ToolRegistryService）");
        }
        ToolRegistryService.ToolCallResult result = toolRegistry.invoke(request);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("toolId", request.toolId());
        output.put("guarded", result.guarded());
        output.put("status", result.success() ? "success" : (result.guarded() ? "guarded" : "failed"));
        output.put("result", result.content());
        if (result.error() != null) {
            output.put("error", result.error());
        }
        output.put("latencyMs", result.latencyMs());
        return output;
    }
}
