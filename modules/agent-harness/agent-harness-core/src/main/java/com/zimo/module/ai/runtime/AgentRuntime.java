package com.zimo.module.ai.runtime;

import com.zimo.framework.ai.AiAgentReply;
import com.zimo.framework.ai.AiAgentService;
import com.zimo.framework.ai.agent.AiAgentRouteRequest;
import com.zimo.framework.common.security.SecurityFacade;
import com.zimo.module.ai.controller.AiChatPreflight;
import com.zimo.module.ai.memory.ChatTurnMemoryRecorder;
import com.zimo.module.ai.tool.ToolRegistryService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 统一智能体运行时：一次智能体执行的<b>唯一入口</b>。
 *
 * <h2>它负责什么、不负责什么</h2>
 * <p>负责：过闸门（权限 / 敏感词 / 意图 / 技能准入）→ 解析工具白名单 → 分层路由 →
 * 调用执行体 → 输出脱敏 → 记忆留痕 → 结果归一。这些正是原先散落在同步与流式两个
 * 控制器里的重复逻辑。</p>
 *
 * <p><b>不负责</b> think-act-observe 的工具调用循环：那个循环在
 * {@code framework-ai} 的 agent 执行体内（模型调用、工具调用、观察回填的生命周期
 * 由它拥有）。harness 是管控面，不重复实现执行引擎 —— 否则会出现两套循环、
 * 两套超时、两套工具权限判断，一旦口径不同就是最难查的线上问题。</p>
 *
 * <h2>两条不可妥协的约束</h2>
 * <ol>
 *   <li><b>闸门不可绕过</b>：本类是新增通道（如图的 agent 节点）进入模型的唯一入口，
 *       必须复用 {@link AiChatPreflight}，不得自行拼一份"轻量校验"。</li>
 *   <li><b>旁路失败不反噬</b>：工具白名单解析与记忆留痕失败都只告警，
 *       绝不让"记录"把"执行"拖垮。</li>
 * </ol>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    private final ObjectProvider<AiChatPreflight> preflightProvider;
    private final ObjectProvider<AiAgentService> agentServiceProvider;
    private final ToolRegistryService toolRegistry;
    private final ObjectProvider<SecurityFacade> security;
    private final ObjectProvider<ChatTurnMemoryRecorder> memoryRecorder;

    /**
     * 构造统一运行时。
     *
     * @param preflightProvider 对话闸门提供者（控制器扫描装配，故用 provider 延迟解析）
     * @param agentServiceProvider 智能体服务提供者，允许缺失
     * @param toolRegistry 工具市场治理服务，可为 {@code null}
     * @param security 安全门面，允许缺失
     * @param memoryRecorder 记忆记录器，允许缺失
     */
    public AgentRuntime(ObjectProvider<AiChatPreflight> preflightProvider,
                        ObjectProvider<AiAgentService> agentServiceProvider,
                        ToolRegistryService toolRegistry,
                        ObjectProvider<SecurityFacade> security,
                        ObjectProvider<ChatTurnMemoryRecorder> memoryRecorder) {
        this.preflightProvider = preflightProvider;
        this.agentServiceProvider = agentServiceProvider;
        this.toolRegistry = toolRegistry;
        this.security = security;
        this.memoryRecorder = memoryRecorder;
    }

    /**
     * 执行一次智能体调用。
     *
     * <p>所有失败都以 {@link AgentRunResult} 的形式返回，不向上抛 —— 图执行需要它
     * 走兜底分支，而不是让整张图失败。</p>
     *
     * @param request 执行请求
     * @return 执行结果
     */
    public AgentRunResult run(AgentRunRequest request) {
        long start = System.currentTimeMillis();
        if (request == null || !hasText(request.agentId()) || !hasText(request.message())) {
            return AgentRunResult.failed("agentId 与 message 均不能为空", request == null ? "" : request.agentId(), 0L);
        }
        try {
            return doRun(request, start);
        } catch (RuntimeException e) {
            log.warn("[agent-runtime] 执行异常: agent={}, err={}", request.agentId(), e.toString());
            return AgentRunResult.failed("智能体执行异常: " + e.getMessage(), request.agentId(), elapsed(start));
        }
    }

    /** 执行主流程：闸门 → 工具解析 → 路由 → 调用 → 脱敏 → 留痕。 */
    private AgentRunResult doRun(AgentRunRequest request, long start) {
        AiChatPreflight preflight = preflightProvider == null ? null : preflightProvider.getIfAvailable();
        if (preflight == null) {
            return AgentRunResult.failed("对话闸门未装配（AiChatPreflight）", request.agentId(), elapsed(start));
        }
        AiChatPreflight.Result gate;
        try {
            gate = preflight.run(gateBody(request));
        } catch (IllegalArgumentException e) {
            // 权限不足 / 敏感词 / 意图拒绝：这是**正确的拒绝**，不是系统故障
            return AgentRunResult.denied(e.getMessage(), request.agentId(), elapsed(start));
        }
        List<String> tools = resolveTools(request);
        if (gate.shortCircuited()) {
            return handleShortCircuit(gate, request, tools, start);
        }
        return callModel(gate, request, tools, start);
    }

    /** 闸门接管：直接返回接管文案，不进入模型。 */
    private AgentRunResult handleShortCircuit(AiChatPreflight.Result gate, AgentRunRequest request,
                                              List<String> tools, long start) {
        Map<String, Object> reply = gate.shortCircuit();
        String text = reply == null ? "" : String.valueOf(reply.getOrDefault("reply", ""));
        record(gate, request.message(), text);
        String agentId = hasText(request.agentId()) ? request.agentId() : "";
        return AgentRunResult.ok(text, agentId, agentId, tools, true, elapsed(start));
    }

    /** 进入模型：分层路由 → 调用 → 输出脱敏 → 记忆留痕。 */
    private AgentRunResult callModel(AiChatPreflight.Result gate, AgentRunRequest request,
                                     List<String> tools, long start) {
        AiAgentService service = agentServiceProvider == null ? null : agentServiceProvider.getIfAvailable();
        if (service == null) {
            return AgentRunResult.failed("智能体服务未装配（AiAgentService）", request.agentId(), elapsed(start));
        }
        AiAgentRouteRequest route = new AiAgentRouteRequest(
                gate.tenantId(), request.channel(), gate.userId(), gate.sessionId(), gate.profile(), null);
        AiAgentReply reply = service.chat(request.message(), route);
        String content = sanitize(reply == null ? "" : reply.content());
        record(gate, request.message(), content);
        return AgentRunResult.ok(content, gate.agent().id(), gate.agent().name(),
                tools, false, elapsed(start));
    }

    /**
     * 解析工具白名单：目录里启用的工具 ∩ 调用方声明的白名单（声明为空表示不限制）。
     *
     * <p>解析失败返回空清单：工具是增强能力，白名单拿不到时宁可"本次不带工具"，
     * 也不能让一次对话因为读目录超时而失败。</p>
     */
    private List<String> resolveTools(AgentRunRequest request) {
        if (toolRegistry == null) {
            return List.of();
        }
        try {
            List<String> available = toolRegistry.listToolIds(request.tenantId());
            List<String> requested = request.requestedToolIds();
            if (requested.isEmpty()) {
                return available;
            }
            List<String> intersected = new ArrayList<>();
            for (String toolId : requested) {
                if (available.contains(toolId)) {
                    intersected.add(toolId);
                }
            }
            return intersected;
        } catch (RuntimeException e) {
            log.warn("[agent-runtime] 工具白名单解析失败，按空清单继续: {}", e.getMessage());
            return List.of();
        }
    }

    /** 输出脱敏：安全门面缺失时原样返回。 */
    private String sanitize(String content) {
        SecurityFacade facade = security == null ? null : security.getIfAvailable();
        if (facade == null || content == null) {
            return content == null ? "" : content;
        }
        try {
            return facade.sanitizeOutput(content);
        } catch (RuntimeException e) {
            log.warn("[agent-runtime] 输出脱敏失败，按原文返回: {}", e.getMessage());
            return content;
        }
    }

    /** 记忆留痕：旁路，失败只告警。 */
    private void record(AiChatPreflight.Result gate, String message, String reply) {
        ChatTurnMemoryRecorder recorder = memoryRecorder == null ? null : memoryRecorder.getIfAvailable();
        if (recorder == null) {
            return;
        }
        try {
            recorder.record(gate.tenantId(), gate.userId(), gate.sessionId(), message, reply);
        } catch (RuntimeException e) {
            log.warn("[agent-runtime] 记忆记录调用失败（不影响执行结果）: {}", e.getMessage());
        }
    }

    /** 组装闸门入参（闸门按 Map 协议工作，与 HTTP 通道完全同构）。 */
    private Map<String, String> gateBody(AgentRunRequest request) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("agentId", request.agentId());
        body.put("message", request.message());
        putIfPresent(body, "tenantId", request.tenantId());
        putIfPresent(body, "userId", request.userId());
        putIfPresent(body, "sessionId", request.sessionId());
        return body;
    }

    private void putIfPresent(Map<String, String> body, String key, String value) {
        if (hasText(value)) {
            body.put(key, value);
        }
    }

    private long elapsed(long start) {
        return Math.max(0L, System.currentTimeMillis() - start);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
