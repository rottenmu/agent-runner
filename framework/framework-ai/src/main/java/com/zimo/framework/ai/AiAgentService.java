package com.zimo.framework.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zimo.framework.ai.agent.AiAgentProfile;
import cn.hutool.core.util.StrUtil;
import com.zimo.framework.ai.agent.AiAgentRouteRequest;
import com.zimo.framework.ai.agent.AiHarnessAgentRegistry;
import com.zimo.framework.ai.agent.AiHarnessAgentRouter;
import com.zimo.framework.ai.agent.AiHarnessSessionKeyFactory;
import com.zimo.module.agentmemory.chat.AiChatMessage;
import com.zimo.framework.ai.chat.AiChatClient;
import com.zimo.framework.ai.chat.AiChatRequest;
import com.zimo.framework.ai.chat.AiChatResponse;
import com.zimo.module.agentmemory.chat.AiConversationMemory;
import com.zimo.framework.ai.runtime.AiAgentRuntime;
import com.zimo.framework.ai.runtime.AiAgentRuntimeStatus;
import com.zimo.framework.ai.skill.AiSkillRegistry;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AI 智能体统一调用服务。
 *
 * <p>旧版 reply/chat API 保留 AiChatClient 兼容行为；带 {@link AiAgentRouteRequest} 的调用使用
 * 分层路由和 HarnessAgent 注册表，租户、智能体、渠道、会话和用户共同定义状态隔离边界。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
public class AiAgentService {

    private static final Logger log = LoggerFactory.getLogger(AiAgentService.class);

    private final AiAgentProperties properties;
    private final AiSkillRegistry skillRegistry;
    private final AiAgentRuntime runtime;
    private final AiChatClient chatClient;
    private final AiHarnessAgentRouter router;
    private final AiHarnessAgentRegistry registry;
    private final AiHarnessSessionKeyFactory sessionKeyFactory;
    private final AiConversationMemory conversationMemory;
    private final com.zimo.module.agentmemory.storage.OltpMemoryRepository oltpMemoryRepository;
    private final com.zimo.framework.common.ai.event.AiEventPublisher eventPublisher;
    /** 请求拦截器链（对应 dsh agent/pre-step 权威决策点）：任一 DENY 即终止请求。 */
    private final List<com.zimo.framework.ai.agent.AiRequestInterceptor> requestInterceptors;
    private final List<com.zimo.framework.ai.agent.AiAgentMiddleware> middlewares;
    /** 插件事件总线（可空）：非空时派发 turn begin/end 事件（dsh A1 事件级瀑布）。 */
    private final com.zimo.framework.ai.plugin.PluginEventBus pluginEventBus;

    /**
     * 创建 AI 智能体统一调用服务。
     *
     * @param properties starter 配置，不允许为空
     * @param skillRegistry 技能注册表，不允许为空
     * @param runtime 运行时状态，不允许为空
     * @param chatClient 旧版 API 兼容聊天客户端，不允许为空
     * @param router 多 HarnessAgent 分层路由器，不允许为空
     * @param registry 多 HarnessAgent 实例注册表，不允许为空
     * @param sessionKeyFactory 会话隔离键工厂，不允许为空
     * @param fileStorageService 文件存储服务；配合 {@code conversation-shared-store} 启用会话记忆共享时传入
     * @param oltpMemoryRepository L0 事件流（非空时对话消息全量落 L0，可回放）
     */
    public AiAgentService(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry,
            AiAgentRuntime runtime,
            AiChatClient chatClient,
            AiHarnessAgentRouter router,
            AiHarnessAgentRegistry registry,
            AiHarnessSessionKeyFactory sessionKeyFactory,
            com.zimo.framework.common.storage.FileStorageService fileStorageService,
            com.zimo.module.agentmemory.storage.OltpMemoryRepository oltpMemoryRepository,
            com.zimo.framework.common.ai.event.AiEventPublisher eventPublisher,
            List<com.zimo.framework.ai.agent.AiRequestInterceptor> requestInterceptors,
            List<com.zimo.framework.ai.agent.AiAgentMiddleware> middlewares) {
        this(properties, skillRegistry, runtime, chatClient, router, registry, sessionKeyFactory,
                fileStorageService, oltpMemoryRepository, eventPublisher, requestInterceptors,
                middlewares, null);
    }

    /**
     * 创建 AI 智能体统一调用服务（带插件事件总线）。
     *
     * @param pluginEventBus 插件事件总线（可空；非空时派发 turn begin/end 事件）
     */
    public AiAgentService(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry,
            AiAgentRuntime runtime,
            AiChatClient chatClient,
            AiHarnessAgentRouter router,
            AiHarnessAgentRegistry registry,
            AiHarnessSessionKeyFactory sessionKeyFactory,
            com.zimo.framework.common.storage.FileStorageService fileStorageService,
            com.zimo.module.agentmemory.storage.OltpMemoryRepository oltpMemoryRepository,
            com.zimo.framework.common.ai.event.AiEventPublisher eventPublisher,
            List<com.zimo.framework.ai.agent.AiRequestInterceptor> requestInterceptors,
            List<com.zimo.framework.ai.agent.AiAgentMiddleware> middlewares,
            com.zimo.framework.ai.plugin.PluginEventBus pluginEventBus) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.skillRegistry = Objects.requireNonNull(skillRegistry, "skillRegistry must not be null");
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
        this.router = Objects.requireNonNull(router, "router must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.sessionKeyFactory = Objects.requireNonNull(
                sessionKeyFactory,
                "sessionKeyFactory must not be null");
        this.oltpMemoryRepository = oltpMemoryRepository;
        this.eventPublisher = eventPublisher;
        this.requestInterceptors = requestInterceptors == null ? List.of() : requestInterceptors;
        this.middlewares = middlewares == null ? List.of() : middlewares;
        this.pluginEventBus = pluginEventBus;
        this.conversationMemory = new AiConversationMemory(
                properties.getConversationMaxSessions(),
                properties.isConversationSharedStore() ? fileStorageService : null,
                oltpMemoryRepository);
    }

    /**
     * 使用旧版兼容调用链回复单条消息。
     *
     * @param message 用户消息，不允许为空白
     * @return 智能体回复；配置或模型调用失败时返回可读错误
     */
    public AiAgentReply reply(String message) {
        if (eventPublisher != null) {
            eventPublisher.publish(new com.zimo.framework.common.ai.event.AgentStepEvent(
                    null, null, "begin", message, null, "ok", System.currentTimeMillis()));
        }
        AiAgentReply result = legacyChat(message, null, null);
        if (eventPublisher != null) {
            eventPublisher.publish(new com.zimo.framework.common.ai.event.AgentStepEvent(
                    null, null, "end", message,
                    result == null ? null : result.content(), "ok", System.currentTimeMillis()));
        }
        return result;
    }

    /**
     * 使用旧版兼容调用链执行带会话的聊天。
     *
     * @param message 用户消息，不允许为空白
     * @param sessionId 会话标识，允许为空
     * @return 智能体回复；同 sessionId 会携带最近会话历史
     */
    public AiAgentReply chat(String message, String sessionId) {
        return legacyChat(message, sessionId, null);
    }

    /**
     * 使用旧版兼容调用链按显式 profile 执行聊天。
     *
     * @param message 用户消息，不允许为空白
     * @param sessionId 会话标识，允许为空
     * @param agent 显式智能体配置，允许为空
     * @return 智能体回复
     */
    public AiAgentReply chat(
            String message,
            String sessionId,
            AiAgentProfile agent) {
        return legacyChat(message, sessionId, agent);
    }

    /**
     * 使用分层路由选择并调用独立 HarnessAgent。
     *
     * @param message 用户消息，不允许为空白
     * @param routeRequest 包含租户、渠道、用户和会话维度的路由请求，不允许为空
     * @return 路由后智能体的回复；无可用配置或调用失败时返回脱敏错误
     */
    public AiAgentReply chat(
            String message,
            AiAgentRouteRequest routeRequest) {
        if (StrUtil.isBlank(message)) {
            return new AiAgentReply(routeAgentName(routeRequest), "消息内容不能为空");
        }
        AiAgentReply unavailable = unavailableReply(routeAgentName(routeRequest));
        if (unavailable != null) {
            return unavailable;
        }
        if (routeRequest == null) {
            return new AiAgentReply(properties.getName(), "未找到可用智能体：路由请求不能为空");
        }

        Optional<AiAgentProfile> routed = router.route(routeRequest);
        if (routed.isEmpty()) {
            return new AiAgentReply(routeAgentName(routeRequest), "未找到可用智能体，请检查租户、绑定和启用状态");
        }
        return invokeHarness(message, routeRequest, routed.get());
    }

    /**
     * 获取当前技能注册表。
     *
     * @return starter 管理的技能注册表
     */
    public AiSkillRegistry skillRegistry() {
        return skillRegistry;
    }

    /**
     * 带历史上下文的续跑（fork/resume 底座）。
     *
     * <p>与 {@link #chat(String, AiAgentRouteRequest)} 的区别：除当前消息外，显式注入
     * 已有的对话历史（如从事件日志重建的 {@code List<Msg>}）。配合新 conversationId
     * 即"会话键级 fork"（继承上下文开新会话）；配合同一 conversationId 即"resume"
     * （断点续跑，不破坏原链路）。历史为空时退化为普通 chat。</p>
     *
     * @param message 当前用户消息，不允许为空白
     * @param routeRequest 路由请求（conversationId 决定会话键归属）
     * @param history 历史消息（user/assistant 交替，可空）
     * @return 续跑回复
     */
    public AiAgentReply chatWithHistory(
            String message,
            AiAgentRouteRequest routeRequest,
            List<Msg> history) {
        if (StrUtil.isBlank(message)) {
            return new AiAgentReply(routeAgentName(routeRequest), "消息内容不能为空");
        }
        if (routeRequest == null) {
            return new AiAgentReply(properties.getName(), "未找到可用智能体：路由请求不能为空");
        }
        AiAgentReply unavailable = unavailableReply(routeAgentName(routeRequest));
        if (unavailable != null) {
            return unavailable;
        }
        Optional<AiAgentProfile> routed = router.route(routeRequest);
        if (routed.isEmpty()) {
            return new AiAgentReply(routeAgentName(routeRequest), "未找到可用智能体，请检查租户、绑定和启用状态");
        }
        return invokeHarnessWithHistory(message, routeRequest, routed.get(),
                history == null ? List.of() : history);
    }

    /**
     * 智能体运行时入口：仅执行带模型上下文注入的主流程（走 middleware 瀑布）。
     *
     * <p>供 fork/resume 等服务在持锁线程内直接驱动运行时（与外部 chat 入口隔离，
     * 避免重复走拦截器/路由的副作用）。</p>
     *
     * @param message 用户消息
     * @param routeRequest 路由请求（内含 profile 等）
     * @param profile 已路由智能体
     * @param history 历史消息（可空）
     * @return 智能体回复
     */
    public AiAgentReply driveRuntime(
            String message,
            AiAgentRouteRequest routeRequest,
            AiAgentProfile profile,
            List<Msg> history) {
        return invokeHarnessWithHistory(message, routeRequest, profile,
                history == null ? List.of() : history);
    }

    private AiAgentReply invokeHarnessWithHistory(
            String message,
            AiAgentRouteRequest routeRequest,
            AiAgentProfile profile,
            List<Msg> history) {
        com.zimo.framework.ai.agent.AiRequestContext context =
                com.zimo.framework.ai.agent.AiRequestContext.of(
                        routeRequest.conversationId(), profile.id(), profile);
        com.zimo.framework.ai.agent.MiddlewareChain terminal =
                (ctx, msg) -> executeCoreWithHistory(ctx, msg, routeRequest, profile, history);
        com.zimo.framework.ai.agent.MiddlewareChain chain =
                com.zimo.framework.ai.agent.AiAgentMiddleware.buildChain(middlewares, terminal);
        com.zimo.framework.ai.agent.AiMiddlewareResult result = chain.proceed(context, message);
        switch (result.kind()) {
            case DENY:
                return new AiAgentReply(agentName(profile), result.message());
            case REPLY:
                return result.reply();
            case CONTINUE:
            default:
                return new AiAgentReply(agentName(profile),
                        "middleware 未产生有效回复：" + (result.message() == null ? "" : result.message()));
        }
    }

    private com.zimo.framework.ai.agent.AiMiddlewareResult executeCoreWithHistory(
            com.zimo.framework.ai.agent.AiRequestContext context,
            String message,
            AiAgentRouteRequest routeRequest,
            AiAgentProfile profile,
            List<Msg> history) {
        InterceptResult intercepted = runInterceptors(
                context.sessionId(), context.agentId(), profile, message);
        if (intercepted.denied()) {
            return com.zimo.framework.ai.agent.AiMiddlewareResult.deny(
                    "请求被拦截：" + intercepted.deniedReason());
        }
        message = intercepted.message();
        long start = System.currentTimeMillis();
        String traceId = com.zimo.framework.ai.observ.TraceCollector.begin(
                routeRequest.conversationId(), profile.id(), agentName(profile),
                "fork_resume", routeRequest.channel());
        try {
            String sessionKey = sessionKeyFactory.create(routeRequest, profile);
            RuntimeContext context2 = runtimeContext(routeRequest, sessionKey);
            // 把 traceId 挂到 RuntimeContext：agent.call().block() 会把执行切到 Reactor
            // 调度线程，TraceCollector 的 ThreadLocal 在异步边界处失效。中间件通过
            // RuntimeContext 的 key-value 区取回 traceId，从而把推理/模型/工具事件
            // 写回同一条链路（详见 HarnessTraceMiddleware）。
            com.zimo.framework.ai.observ.HarnessTraceMiddleware.bindTraceId(context2, traceId);
            com.zimo.framework.ai.observ.TraceCollector.step("intent", "意图路由",
                    "{\"agentType\":\"" + safeJson(profile.agentType())
                            + "\",\"model\":\"" + safeJson(profile.modelName())
                            + "\",\"history\":" + history.size() + "}",
                    "路由至 " + agentName(profile), 0, "ok");
            List<Msg> requestMessages = new java.util.ArrayList<>(history);
            requestMessages.add(userMessage(message, routeRequest.userId()));
            Msg response = registry.withAgent(
                    profile,
                    agent -> agent.call(requestMessages, context2).block());
            if (response == null || !hasText(response.getTextContent())) {
                String failMsg = "AI 智能体未返回有效内容";
                com.zimo.framework.ai.observ.TraceCollector.end("failed", message, failMsg, 0,
                        System.currentTimeMillis() - start);
                return com.zimo.framework.ai.agent.AiMiddlewareResult.reply(
                        new AiAgentReply(agentName(profile), failMsg));
            }
            String content = response.getTextContent();
            com.zimo.framework.ai.observ.TraceCollector.step("generation", "智能体回复",
                    "{\"prompt\":\"" + safeJson(message) + "\"}",
                    "{\"response\":\"" + safeJson(content) + "\"}", System.currentTimeMillis() - start, "ok");
            com.zimo.framework.ai.observ.TraceCollector.end("ok", message, content, estimateTokens(content),
                    System.currentTimeMillis() - start);
            return com.zimo.framework.ai.agent.AiMiddlewareResult.reply(
                    new AiAgentReply(agentName(profile), content));
        } catch (RuntimeException exception) {
            String failMsg = "AI 智能体调用失败：" + safeMessage(exception);
            com.zimo.framework.ai.observ.TraceCollector.end("failed", message, failMsg, 0,
                    System.currentTimeMillis() - start);
            return com.zimo.framework.ai.agent.AiMiddlewareResult.reply(
                    new AiAgentReply(agentName(profile), failMsg));
        }
    }

    /** 标准入口执行器（无历史注入）：middleware 链尾为 executeCore。 */
    private AiAgentReply invokeHarness(
            String message,
            AiAgentRouteRequest routeRequest,
            AiAgentProfile profile) {
        // around-middleware 瀑布：middlewares 环绕包裹主流程（executeCore）。
        // 每个 middleware 可 pre 改写/短路、next() 委托、post 包裹回复。
        com.zimo.framework.ai.agent.AiRequestContext context =
                com.zimo.framework.ai.agent.AiRequestContext.of(
                        routeRequest.conversationId(), profile.id(), profile);
        com.zimo.framework.ai.agent.MiddlewareChain terminal =
                (ctx, msg) -> executeCore(ctx, msg, routeRequest, profile);
        com.zimo.framework.ai.agent.MiddlewareChain chain =
                com.zimo.framework.ai.agent.AiAgentMiddleware.buildChain(middlewares, terminal);
        if (pluginEventBus != null) {
            pluginEventBus.emit(com.zimo.framework.ai.plugin.PluginEventBus.AGENT_TURN_BEGIN,
                    java.util.Map.of(
                            "conversationId", routeRequest.conversationId() == null ? "" : routeRequest.conversationId(),
                            "agentId", profile.id()));
        }
        com.zimo.framework.ai.agent.AiMiddlewareResult result = chain.proceed(context, message);
        if (pluginEventBus != null) {
            pluginEventBus.emit(com.zimo.framework.ai.plugin.PluginEventBus.AGENT_TURN_END,
                    java.util.Map.of(
                            "conversationId", routeRequest.conversationId() == null ? "" : routeRequest.conversationId(),
                            "agentId", profile.id(),
                            "kind", result == null ? "none" : result.kind().name()));
        }
        switch (result.kind()) {
            case DENY:
                return new AiAgentReply(agentName(profile), result.message());
            case REPLY:
                return result.reply();
            case CONTINUE:
            default:
                // 链尾总是返回 REPLY/DENY，此处兜底
                return new AiAgentReply(agentName(profile),
                        "middleware 未产生有效回复：" + (result.message() == null ? "" : result.message()));
        }
    }

    /** 主流程执行器（拦截链 + 意图路由 + 模型调用 + 回复组装），作为 middleware 链尾。 */
    private com.zimo.framework.ai.agent.AiMiddlewareResult executeCore(
            com.zimo.framework.ai.agent.AiRequestContext context,
            String message,
            AiAgentRouteRequest routeRequest,
            AiAgentProfile profile) {
        InterceptResult intercepted = runInterceptors(
                context.sessionId(), context.agentId(), profile, message);
        if (intercepted.denied()) {
            return com.zimo.framework.ai.agent.AiMiddlewareResult.deny(
                    "请求被拦截：" + intercepted.deniedReason());
        }
        message = intercepted.message();
        long start = System.currentTimeMillis();
        String traceId = com.zimo.framework.ai.observ.TraceCollector.begin(
                routeRequest.conversationId(), profile.id(), agentName(profile),
                profile.agentType(), routeRequest.channel());
        try {
            String sessionKey = sessionKeyFactory.create(routeRequest, profile);
            RuntimeContext context2 = runtimeContext(routeRequest, sessionKey);
            // 把 traceId 挂到 RuntimeContext：agent.call().block() 会把执行切到 Reactor
            // 调度线程，TraceCollector 的 ThreadLocal 在异步边界处失效。中间件通过
            // RuntimeContext 的 key-value 区取回 traceId，从而把推理/模型/工具事件
            // 写回同一条链路（详见 HarnessTraceMiddleware）。
            com.zimo.framework.ai.observ.HarnessTraceMiddleware.bindTraceId(context2, traceId);
            Msg requestMessage = userMessage(message, routeRequest.userId());
            com.zimo.framework.ai.observ.TraceCollector.step("intent", "意图路由",
                    "{\"agentType\":\"" + safeJson(profile.agentType()) + "\",\"model\":\"" + safeJson(profile.modelName()) + "\"}",
                    "路由至 " + agentName(profile), 0, "ok");
            Msg response = registry.withAgent(
                    profile,
                    agent -> agent.call(requestMessage, context2).block());
            if (response == null || !hasText(response.getTextContent())) {
                String failMsg = "AI 智能体未返回有效内容";
                com.zimo.framework.ai.observ.TraceCollector.end("failed", message, failMsg, 0,
                        System.currentTimeMillis() - start);
                return com.zimo.framework.ai.agent.AiMiddlewareResult.reply(
                        new AiAgentReply(agentName(profile), failMsg));
            }
            String content = response.getTextContent();
            com.zimo.framework.ai.observ.TraceCollector.step("generation", "智能体回复",
                    "{\"prompt\":\"" + safeJson(message) + "\"}",
                    "{\"response\":\"" + safeJson(content) + "\"}", System.currentTimeMillis() - start, "ok");
            com.zimo.framework.ai.observ.TraceCollector.end("ok", message, content, estimateTokens(content),
                    System.currentTimeMillis() - start);
            return com.zimo.framework.ai.agent.AiMiddlewareResult.reply(
                    new AiAgentReply(agentName(profile), content));
        } catch (RuntimeException exception) {
            String failMsg = "AI 智能体调用失败：" + safeMessage(exception);
            com.zimo.framework.ai.observ.TraceCollector.end("failed", message, failMsg, 0,
                    System.currentTimeMillis() - start);
            return com.zimo.framework.ai.agent.AiMiddlewareResult.reply(
                    new AiAgentReply(agentName(profile), failMsg));
        }
    }

    private String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private int estimateTokens(String content) {
        if (content == null) {
            return 0;
        }
        return (content.length() + 2) / 3; // 中文约 3 字符/token
    }

    private RuntimeContext runtimeContext(
            AiAgentRouteRequest request,
            String sessionKey) {
        RuntimeContext.Builder builder = RuntimeContext.builder()
                .sessionId(sessionKey)
                .userId(hasText(request.userId()) ? request.userId() : "_")
                .put("tenantId", request.tenantId());
        if (hasText(request.channel())) {
            builder.put("channel", request.channel());
        }
        if (hasText(request.conversationId())) {
            builder.put("conversationId", request.conversationId());
        }
        return builder.build();
    }

    private Msg userMessage(String message, String userId) {
        return Msg.builder()
                .name(hasText(userId) ? userId : "user")
                .role(MsgRole.USER)
                .textContent(message)
                .build();
    }

    /** 拦截结果：改写后的消息或拒绝原因。 */
    private record InterceptResult(String message, String deniedReason) {
        boolean denied() {
            return deniedReason != null;
        }
    }

    /** 程序化进入 Plan Mode（等价 plan_enter，不触发 HITL）。 */
    public boolean enterPlanMode(String sessionKey, AiAgentRouteRequest routeRequest) {
        return Boolean.TRUE.equals(planMode(sessionKey, routeRequest, 0));
    }

    /** 程序化退出 Plan Mode（等价 plan_exit，程序入口不触发 HITL）。 */
    public boolean exitPlanMode(String sessionKey, AiAgentRouteRequest routeRequest) {
        return Boolean.TRUE.equals(planMode(sessionKey, routeRequest, 1));
    }

    /** 查询 Plan Mode 是否激活。 */
    public boolean isPlanModeActive(String sessionKey, AiAgentRouteRequest routeRequest) {
        return Boolean.TRUE.equals(planMode(sessionKey, routeRequest, 2));
    }

    /** 统一 plan 控制：0=enter / 1=exit / 2=active。 */
    private Object planMode(String sessionKey, AiAgentRouteRequest routeRequest, int op) {
        Optional<AiAgentProfile> routed = router.route(routeRequest);
        if (routed.isEmpty()) {
            return false;
        }
        AiAgentProfile profile = routed.get();
        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(hasText(sessionKey) ? sessionKey : sessionKeyFactory.create(routeRequest, profile))
                .userId(hasText(routeRequest.userId()) ? routeRequest.userId() : "_")
                .build();
        return registry.withAgent(profile, agent -> {
            switch (op) {
                case 0:
                    agent.enterPlanMode(ctx);
                    return true;
                case 1:
                    agent.exitPlanMode(ctx);
                    return true;
                default:
                    return agent.isPlanModeActive(ctx);
            }
        });
    }

    /** 运行请求拦截链；返回最终消息（可能被改写）或拒绝原因。 */
    private InterceptResult runInterceptors(String sessionId, String agentId, AiAgentProfile profile, String message) {
        String current = message;
        for (com.zimo.framework.ai.agent.AiRequestInterceptor interceptor : requestInterceptors) {
            com.zimo.framework.ai.agent.AiRequestDecision decision =
                    interceptor.intercept(com.zimo.framework.ai.agent.AiRequestContext.of(sessionId, agentId, profile), current);
            if (decision == null || decision.action() == com.zimo.framework.ai.agent.AiRequestDecision.Action.PASS) {
                continue;
            }
            if (decision.action() == com.zimo.framework.ai.agent.AiRequestDecision.Action.DENY) {
                return new InterceptResult(current, decision.message());
            }
            current = decision.message();
        }
        return new InterceptResult(current, null);
    }

    private AiAgentReply legacyChat(
            String message,
            String sessionId,
            AiAgentProfile agent) {
        if (StrUtil.isBlank(message)) {
            return new AiAgentReply(agentName(agent), "消息内容不能为空");
        }
        InterceptResult intercepted = runInterceptors(sessionId, agentName(agent), agent, message);
        if (intercepted.denied()) {
            return new AiAgentReply(agentName(agent), "请求被拦截：" + intercepted.deniedReason());
        }
        message = intercepted.message();
        AiAgentReply unavailable = unavailableReply(agentName(agent));
        if (unavailable != null) {
            return unavailable;
        }
        AiChatResponse response = chatClient.chat(new AiChatRequest(
                agentName(agent),
                message,
                sessionId,
                modelName(agent),
                properties.getTemperature(),
                properties.getMaxTokens(),
                systemPrompt(agent),
                conversationMemory.snapshot(sessionId)));
        if (!response.success()) {
            return new AiAgentReply(agentName(agent), response.errorMessage());
        }
        conversationMemory.appendTurn(
                sessionId,
                message,
                response.content(),
                properties.getChatHistoryLimit());
        tryCompressConversation(sessionId);
        return new AiAgentReply(agentName(agent), response.content());
    }

    /**
     * 进程内会话记忆压缩：消息数达到触发阈值时，用 LLM 生成摘要替换最旧消息。
     *
     * <p>HarnessAgent 主链路已由 AgentScope {@code CompactionConfig} 接管；此处仅
     * 补齐旧版兼容链路（{@code legacyChat}）的进程内记忆压缩，避免长对话静默
     * 丢弃历史事实。摘要失败或候选项过期时保持会话记忆不变。</p>
     */
    private void tryCompressConversation(String sessionId) {
        AiAgentProperties.ContextCompressionSettings compression =
                properties.getEffectiveContextCompressionSettings();
        if (!compression.enabled() || !hasText(sessionId)) {
            return;
        }
        conversationMemory.prepareCompression(
                        sessionId,
                        compression.triggerMessages(),
                        compression.recentMessages())
                .ifPresent(candidate -> {
                    // 严格重试：摘要失败按 maxRetries 重试；耗尽后保持会话记忆不变（回退）
                    String summary = summarizeWithRetry(
                            candidate.messages(), compression.summaryMaxCharacters(),
                            compression.maxRetries());
                    if (!hasText(summary)) {
                        log.warn("会话压缩摘要生成失败（已重试 {} 次），保持会话记忆不变", compression.maxRetries());
                        return;
                    }
                    boolean applied = conversationMemory.applyCompression(
                            candidate, summary, properties.getChatHistoryLimit());
                    if (!applied) {
                        // revision 冲突或候选项过期：会话已演进，放弃本次压缩（下次触发时重新生成）
                        log.debug("会话压缩应用失败（候选项过期），放弃本次压缩：{}", sessionId);
                    }
                });
    }

    /**
     * 摘要生成（带严格重试，对应 dsh compaction 重试语义）。
     *
     * <p>摘要为空或调用异常均视为失败，最多重试 {@code maxRetries} 次；
     * 全部失败返回空串，由调用方回退为保持会话记忆不变。</p>
     */
    private String summarizeWithRetry(
            java.util.List<com.zimo.module.agentmemory.chat.AiChatMessage> messages,
            int maxCharacters,
            int maxRetries) {
        int attempts = Math.max(0, maxRetries) + 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                String summary = summarize(messages, maxCharacters);
                if (hasText(summary)) {
                    return summary;
                }
                log.warn("会话摘要为空（第 {}/{} 次）", attempt, attempts);
            } catch (Exception e) {
                log.warn("会话摘要生成异常（第 {}/{} 次）: {}", attempt, attempts, e.getMessage());
            }
        }
        return "";
    }

    /**
     * 生成对话摘要（独立 LLM 调用，不携带会话历史，避免递归膨胀）。
     *
     * @param messages 待压缩的原始消息
     * @param maxCharacters 摘要最大字符数
     * @return 摘要文本；调用失败时返回空串（调用方保持会话记忆不变）
     */
    private String summarize(List<AiChatMessage> messages, int maxCharacters) {
        if (messages.isEmpty()) {
            return "";
        }
        StringBuilder prompt = new StringBuilder("请将以下对话压缩为简洁的中文摘要，"
                + "保留事实、约束、用户偏好等关键信息，不要超过 ")
                .append(maxCharacters).append(" 字：\n");
        for (AiChatMessage message : messages) {
            prompt.append(message.role()).append(": ").append(message.content()).append('\n');
        }
        AiChatResponse response = chatClient.chat(new AiChatRequest(
                agentName(null),
                prompt.toString(),
                null,
                modelName(null),
                properties.getTemperature(),
                Math.min(properties.getMaxTokens(), maxCharacters * 2),
                "你是对话摘要助手，只输出摘要本身。",
                List.of()));
        return response.success() ? response.content() : "";
    }

    private AiAgentReply unavailableReply(String agentName) {
        if (runtime.status() == AiAgentRuntimeStatus.NOT_CONFIGURED) {
            return new AiAgentReply(agentName, runtime.message());
        }
        if (runtime.status() == AiAgentRuntimeStatus.INITIALIZATION_FAILED) {
            return new AiAgentReply(agentName, "AI 智能体初始化失败：" + runtime.message());
        }
        return null;
    }

    private String routeAgentName(AiAgentRouteRequest request) {
        return request == null ? properties.getName() : agentName(request.explicitProfile());
    }

    private String agentName(AiAgentProfile agent) {
        return agent != null && hasText(agent.name()) ? agent.name() : properties.getName();
    }

    private String modelName(AiAgentProfile agent) {
        return agent != null && hasText(agent.modelName()) ? agent.modelName() : properties.getModelName();
    }

    private String systemPrompt(AiAgentProfile agent) {
        return agent != null && hasText(agent.systemPrompt())
                ? agent.systemPrompt()
                : properties.getSystemPrompt();
    }

    private String safeMessage(Throwable exception) {
        Throwable error = exception;
        while (error.getCause() != null && error.getCause() != error) {
            error = error.getCause();
        }
        String message = hasText(error.getMessage())
                ? error.getMessage()
                : error.getClass().getSimpleName();
        String apiKey = properties.getApiKey();
        return hasText(apiKey) ? message.replace(apiKey, "[redacted]") : message;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
