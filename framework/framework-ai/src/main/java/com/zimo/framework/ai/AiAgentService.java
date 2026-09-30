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
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Flux;

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
     * 记忆感知增强器（M3，可空）：非空时在推理<b>前</b>预召回并注入长期记忆、
     * 回复后投递异步事实回写。
     *
     * <p>为什么用「可空装配」而不是必需依赖：记忆是<b>增强</b>能力，未接入记忆模块的部署
     * 不应因为缺这个 Bean 而起不来。装配侧用 {@code ObjectProvider#getIfAvailable} 取。</p>
     */
    private final com.zimo.module.agentmemory.engine.MemoryAwarePromptBuilder memoryAwarePromptBuilder;
    /**
     * 计划模式回写协调器（M4-2b，可空）：驳回守卫 + 程序性经验回写。
     *
     * <p>{@code null} 时不回写计划经验（其余记忆能力不受影响）。</p>
     */
    private final com.zimo.framework.ai.memory.PlanWritebackCoordinator planWritebackCoordinator;
    /**
     * AgentState task 分区探针（M4-3，可空）：任务进入终态时清理会话里的计划任务清单。
     *
     * <p>标准 10 要求「任务结束后条数为 0」。不清理的后果不是报错，而是
     * <b>上一轮的计划残留被下一轮对话读到</b> —— 表现为模型莫名沿用旧计划，
     * 且从外部完全看不出原因。{@code null} 时跳过清理（未启用 RocksDB 存储）。</p>
     */
    private final com.zimo.framework.ai.agent.memory.AgentStateTaskPartitionProbe taskPartitionProbe;

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
                middlewares, null, null);
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
        this(properties, skillRegistry, runtime, chatClient, router, registry, sessionKeyFactory,
                fileStorageService, oltpMemoryRepository, eventPublisher, requestInterceptors,
                middlewares, pluginEventBus, null);
    }

    /**
     * 创建 AI 智能体统一调用服务（完整形态）。
     *
     * @param pluginEventBus         插件事件总线（可空）
     * @param memoryAwarePromptBuilder 记忆感知增强器（可空；为空时不做预召回与回写）
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
            com.zimo.framework.ai.plugin.PluginEventBus pluginEventBus,
            com.zimo.module.agentmemory.engine.MemoryAwarePromptBuilder memoryAwarePromptBuilder) {
        this(properties, skillRegistry, runtime, chatClient, router, registry, sessionKeyFactory,
                fileStorageService, oltpMemoryRepository, eventPublisher, requestInterceptors,
                middlewares, pluginEventBus, memoryAwarePromptBuilder, null);
    }

    /**
     * 创建 AI 智能体统一调用服务（完整形态，含计划模式回写）。
     *
     * <p>与上一版唯一差异是多收一个 {@code planWritebackCoordinator}：计划执行完成且未被
     * 用户驳回时，把本次规划抽象成程序性记忆回写（M4-2b，PRD §4.2）。</p>
     *
     * @param planWritebackCoordinator 计划回写协调器（可空）；为空时不回写计划经验
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
            com.zimo.framework.ai.plugin.PluginEventBus pluginEventBus,
            com.zimo.module.agentmemory.engine.MemoryAwarePromptBuilder memoryAwarePromptBuilder,
            com.zimo.framework.ai.memory.PlanWritebackCoordinator planWritebackCoordinator) {
        this(properties, skillRegistry, runtime, chatClient, router, registry, sessionKeyFactory,
                fileStorageService, oltpMemoryRepository, eventPublisher, requestInterceptors,
                middlewares, pluginEventBus, memoryAwarePromptBuilder, planWritebackCoordinator, null);
    }

    /**
     * 创建 AI 智能体统一调用服务（完整形态，含计划回写与 task 分区清理）。
     *
     * @param planWritebackCoordinator 计划回写协调器（可空）；为空时不回写计划经验
     * @param taskPartitionProbe       AgentState task 分区探针（可空）；为空时不做终态清理
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
            com.zimo.framework.ai.plugin.PluginEventBus pluginEventBus,
            com.zimo.module.agentmemory.engine.MemoryAwarePromptBuilder memoryAwarePromptBuilder,
            com.zimo.framework.ai.memory.PlanWritebackCoordinator planWritebackCoordinator,
            com.zimo.framework.ai.agent.memory.AgentStateTaskPartitionProbe taskPartitionProbe) {
        this.taskPartitionProbe = taskPartitionProbe;
        this.planWritebackCoordinator = planWritebackCoordinator;
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
        this.memoryAwarePromptBuilder = memoryAwarePromptBuilder;
        this.conversationMemory = new AiConversationMemory(
                properties.getConversationMaxSessions(),
                properties.isConversationSharedStore() ? fileStorageService : null,
                oltpMemoryRepository);
    }

    /**
     * 把本轮的隔离维度与召回查询文本绑进 {@code RuntimeContext}（M3，PRD §3.3）。
     *
     * <p>真正的召回与系统提示词注入发生在 {@code MemoryPromptMiddleware#onSystemPrompt}
     * —— 那份代码跑在 Reactor 调度线程上，拿不到本方法的局部变量，只能通过
     * {@code RuntimeContext} 传递（与 traceId 同一个坑）。</p>
     *
     * <p><b>为什么不在服务层直接改 messages</b>：AgentScope 拒绝 hooks 往
     * {@code PreCallEvent.inputMessages} 注入 SYSTEM 消息，真机上会让每条命中记忆的对话失败。
     * 受支持的注入点是系统提示词钩子。</p>
     *
     * <p><b>{@code agentType} 必须在路由确定之后才绑</b>（M4-2a）：计划模式要额外召回程序性记忆，
     * 而类型是路由的产出。因此本方法改为接收已解析的 {@code profile}，
     * 而不是从 {@code routeRequest.explicitProfile} 猜 —— 后者可能为 {@code null}
     * （未显式指定时走注册表默认），猜错会让计划模式静默退化成普通召回，
     * 而链路里看起来一切正常。</p>
     */
    private void bindMemoryContext(RuntimeContext context, AiAgentRouteRequest routeRequest,
                                   String query, AiAgentProfile profile) {
        if (memoryAwarePromptBuilder == null || context == null || routeRequest == null) {
            return;
        }
        String agentType = profile == null ? null : profile.agentType();
        com.zimo.framework.ai.memory.MemoryPromptMiddleware.bindContext(context,
                com.zimo.module.agentmemory.engine.MemoryScope.of(
                        routeRequest.tenantId(), routeRequest.userId(),
                        routeRequest.conversationId(), agentType),
                query);
    }

    /**
     * 回复后投递记忆回写：会话事实（M3，PRD §7.2）+ 计划经验（M4-2b，PRD §4.2）。
     *
     * <p>必须在 {@code TraceCollector.end()} <b>之前</b>调用：同步骨架 span 只有此刻发出
     * 才会被导出，收尾之后发出的 span 会「既不导出也不释放」（PRD §4.4）。</p>
     *
     * <p><b>为什么计划回写也在这里</b>：它需要 HITL 确认信号，而该信号挂在
     * {@code RuntimeContext} 上（推理跑在 Reactor 线程，ThreadLocal 取不到）；
     * 本方法是收尾前<b>唯一</b>还能拿到 {@code context} 与 {@code profile} 的位置。</p>
     */
    private void writebackMemory(AiAgentRouteRequest routeRequest, String traceId,
                                 String userMessage, String reply,
                                 RuntimeContext context, AiAgentProfile profile) {
        if (memoryAwarePromptBuilder != null) {
            memoryAwarePromptBuilder.afterReply(
                    com.zimo.module.agentmemory.engine.MemoryScope.of(
                            routeRequest.tenantId(), routeRequest.userId(),
                            routeRequest.conversationId()),
                    traceId, userMessage, reply);
        }
        if (planWritebackCoordinator != null) {
            // 计划模式另外回写一条程序性记忆；被驳回时不写（守卫在协调器内部）
            planWritebackCoordinator.afterPlan(
                    com.zimo.module.agentmemory.engine.MemoryScope.of(
                            routeRequest.tenantId(), routeRequest.userId(),
                            routeRequest.conversationId(),
                            profile == null ? null : profile.agentType()),
                    context, traceId);
        }
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
        List<Msg> requestMessages = new java.util.ArrayList<>(history);
        requestMessages.add(userMessage(message, routeRequest.userId()));
        return runAgentTurn(routeRequest, profile, message, traceId, start, requestMessages, true);
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
        Msg requestMessage = userMessage(message, routeRequest.userId());
        return runAgentTurn(routeRequest, profile, message, traceId, start,
                List.of(requestMessage), false);
    }

    /**
     * 一次 agent 轮次的公共骨架（M4 抽取，消除 {@code executeCore} 与
     * {@code executeCoreWithHistory} 的重复）。
     *
     * <p>两个入口的差异只有两点：<b>请求消息怎么组装</b>（单条 vs 历史+新消息）与
     * <b>链路 intent 标签</b>（{@code profile.agentType()} vs {@code "fork_resume"}）。
     * 其余（绑 traceId、意图 span、模型调用、空响应兜底、记忆回写、终态清理、
     * 异常兜底）逐行相同 —— 抽在这里，避免两处各改一遍而漏掉其中一处。</p>
     *
     * <h2>为什么 {@code sessionKey} 在 try 之外</h2>
     * <p>异常分支也要能做终态清理（M4-3）。若声明在 {@code try} 内，{@code catch}
     * 里就看不见它，任务失败时会留下计划残留。</p>
     *
     * @param requestMessages 已组装好的请求消息（单条或历史+新消息）
     * @param withHistory     {@code true} 时按「历史+新消息」调 {@code call(List)}；
     *                        {@code false} 时按单条调 {@code call(Msg)}。
     *                        <b>两条路径的调用形态必须保持各自原样</b> —— 见下方说明。
     * @return agent 回复信封
     */
    private com.zimo.framework.ai.agent.AiMiddlewareResult runAgentTurn(
            AiAgentRouteRequest routeRequest,
            AiAgentProfile profile,
            String message,
            String traceId,
            long start,
            List<Msg> requestMessages,
            boolean withHistory) {
        String sessionKey = sessionKeyFactory.create(routeRequest, profile);
        try {
            RuntimeContext context2 = runtimeContext(routeRequest, sessionKey);
            prepareTurnContext(context2, routeRequest, profile, message, traceId, requestMessages);
            Msg response = registry.withAgent(profile, agent -> withHistory
                    // 无历史路径沿用 call(Msg) 单条重载：这是抽取前的原始形态。
                    // ⚠️ 不要图省事统一成 call(List) —— AgentScope 把两种重载当作
                    // 语义等价的入口，但已有调用方（含测试桩）按具体重载 stub，
                    // 统一会自动破坏它们，且属于与本次改动无关的行为变更。
                    ? agent.call(requestMessages, context2).block()
                    : agent.call(requestMessages.get(0), context2).block());
            if (response == null || !hasText(response.getTextContent())) {
                String failMsg = "AI 智能体未返回有效内容";
                clearTaskPartition(profile, sessionKey);
                com.zimo.framework.ai.observ.TraceCollector.end("failed", message, failMsg, 0,
                        System.currentTimeMillis() - start);
                return com.zimo.framework.ai.agent.AiMiddlewareResult.reply(
                        new AiAgentReply(agentName(profile), failMsg));
            }
            String content = response.getTextContent();
            com.zimo.framework.ai.observ.TraceCollector.step("generation", "智能体回复",
                    "{\"prompt\":\"" + safeJson(message) + "\"}",
                    "{\"response\":\"" + safeJson(content) + "\"}", System.currentTimeMillis() - start, "ok");
            writebackMemory(routeRequest, traceId, message, content, context2, profile);
            clearTaskPartition(profile, sessionKey);
            com.zimo.framework.ai.observ.TraceCollector.end("ok", message, content, estimateTokens(content),
                    System.currentTimeMillis() - start);
            return com.zimo.framework.ai.agent.AiMiddlewareResult.reply(
                    new AiAgentReply(agentName(profile), content));
        } catch (RuntimeException exception) {
            String failMsg = "AI 智能体调用失败：" + safeMessage(exception);
            clearTaskPartition(profile, sessionKey);
            com.zimo.framework.ai.observ.TraceCollector.end("failed", message, failMsg, 0,
                    System.currentTimeMillis() - start);
            return com.zimo.framework.ai.agent.AiMiddlewareResult.reply(
                    new AiAgentReply(agentName(profile), failMsg));
        }
    }

    /**
     * 轮次前置准备（同步轮次与流式轮次共用）。
     *
     * <p><b>为什么必须共用</b>：traceId 与记忆上下文的绑定若在两条通道各写一份，
     * 某一侧漏掉一行就会表现为「流式通道的可观测链路断掉」或「流式通道读不到记忆」
     * —— 两者都极难从外部察觉（页面看起来一切正常）。抽成一个方法，从结构上排除漂移。</p>
     */
    private void prepareTurnContext(
            RuntimeContext runtimeCtx,
            AiAgentRouteRequest routeRequest,
            AiAgentProfile profile,
            String message,
            String traceId,
            List<Msg> requestMessages) {
        // 把 traceId 挂到 RuntimeContext：agent.call() / agent.stream() 都会把执行切到
        // Reactor 调度线程，TraceCollector 的 ThreadLocal 在异步边界处失效。中间件通过
        // RuntimeContext 的 key-value 区取回 traceId，从而把推理/模型/工具事件
        // 写回同一条链路（详见 HarnessTraceMiddleware）。
        com.zimo.framework.ai.observ.HarnessTraceMiddleware.bindTraceId(runtimeCtx, traceId);
        com.zimo.framework.ai.observ.TraceCollector.step("intent", "意图路由",
                "{\"agentType\":\"" + safeJson(profile.agentType())
                        + "\",\"model\":\"" + safeJson(profile.modelName())
                        + "\",\"messages\":" + requestMessages.size() + "}",
                "路由至 " + agentName(profile), 0, "ok");
        bindMemoryContext(runtimeCtx, routeRequest, message, profile);
    }

    /**
     * 流式对话：与 {@link #chat(String, AiAgentRouteRequest)} 同闸门、同路由，
     * 差异只在回复以增量事件流产出，供 SSE 通道逐块推送到前端。
     *
     * <p><b>失败为什么也走事件流而不是 onError</b>：前端拿到 HTTP 200 之后再遭遇
     * 传输层的 onError，会被当成「网络故障、可以重试」，而实际是业务失败（需按归因处置）。
     * 统一以 {@link AiAgentStreamEvent.Kind#ERROR} 事件送达，让失败在通道内可表达。</p>
     *
     * @param message      用户消息，不允许为空白
     * @param routeRequest 路由请求，不允许为空
     * @return 增量事件流；末事件必为 {@code DONE} 或 {@code ERROR}
     */
    public Flux<AiAgentStreamEvent> stream(
            String message,
            AiAgentRouteRequest routeRequest) {
        if (StrUtil.isBlank(message)) {
            return Flux.just(AiAgentStreamEvent.error("消息内容不能为空", 0L));
        }
        AiAgentReply unavailable = unavailableReply(routeAgentName(routeRequest));
        if (unavailable != null) {
            return Flux.just(AiAgentStreamEvent.error(unavailable.content(), 0L));
        }
        if (routeRequest == null) {
            return Flux.just(AiAgentStreamEvent.error("未找到可用智能体：路由请求不能为空", 0L));
        }
        Optional<AiAgentProfile> routed = router.route(routeRequest);
        if (routed.isEmpty()) {
            return Flux.just(AiAgentStreamEvent.error(
                    "未找到可用智能体，请检查租户、绑定和启用状态", 0L));
        }
        return streamTurn(message, routeRequest, routed.get());
    }

    /**
     * 流式轮次：与 {@link #runAgentTurn} 共用前置准备与收尾，仅模型调用形态不同。
     *
     * <p><b>收尾为什么写在 Flux 回调里</b>：增量回调跑在 Reactor 调度线程上，因此
     * trace 写入一律走显式传 {@code traceId} 的 {@code stepFor/endFor} 重载，
     * 记忆回写整份走参数传递 —— 这两处都已刻意避开 ThreadLocal（见
     * {@code MemoryPromptMiddleware} 的同类说明），跨线程是安全的。</p>
     *
     * <p><b>为什么消费 {@code REASONING} 而不是只等 {@code AGENT_RESULT}</b>：
     * AgentScope 把模型输出拆成 {@code TextBlock}（答复）与 {@code ThinkingBlock}
     * （思维链）分别累加，逐块产出挂在 REASONING 事件上；只等 AGENT_RESULT 会退化成
     * 「等全部生成完再一次性返回」，也就是假流式。思维链不外发，否则前端会把
     * 内部推理当成答复渲染。</p>
     */
    private Flux<AiAgentStreamEvent> streamTurn(
            String message,
            AiAgentRouteRequest routeRequest,
            AiAgentProfile profile) {
        String sessionKey = sessionKeyFactory.create(routeRequest, profile);
        long start = System.currentTimeMillis();
        String traceId = com.zimo.framework.ai.observ.TraceCollector.begin(
                routeRequest.conversationId(), profile.id(), agentName(profile),
                profile.agentType(), routeRequest.channel());
        List<Msg> requestMessages = List.of(userMessage(message, routeRequest.userId()));
        try {
            RuntimeContext context2 = runtimeContext(routeRequest, sessionKey);
            prepareTurnContext(context2, routeRequest, profile, message, traceId, requestMessages);
            StreamAccumulator accumulator = new StreamAccumulator();
            // registry.withAgent 也可能抛（构建智能体失败），故整段都在 try 内 —— 否则它会
            // 漏出到 Web 层变成 HTTP 500，前端拿不到「失败在通道内可表达」的 error 事件
            return registry.withAgent(profile,
                            agent -> agent.stream(requestMessages, streamOptions(), context2))
                    .filter(this::isReplyEvent)
                    .map(event -> event.getMessage() == null
                            ? "" : event.getMessage().getTextContent())
                    .map(accumulator::merge)
                    .filter(delta -> !delta.isEmpty())
                    .map(AiAgentStreamEvent::delta)
                    .concatWith(Flux.defer(() -> Flux.just(finishStream(
                            accumulator.text(), routeRequest, profile, message,
                            sessionKey, traceId, start, context2))))
                    .onErrorResume(exception -> Flux.just(
                            failEvent(exception, sessionKey, profile, traceId, message, start)));
        } catch (RuntimeException exception) {
            return Flux.just(failEvent(exception, sessionKey, profile, traceId, message, start));
        }
    }

    /** 流式选项：{@code incremental} 决定拿到逐块而非每轮一次的整段文本。 */
    private StreamOptions streamOptions() {
        return StreamOptions.builder()
                .incremental(true)
                .eventTypes(EventType.REASONING, EventType.AGENT_RESULT)
                .includeReasoningChunk(true)
                .includeReasoningResult(true)
                .build();
    }

    /**
     * 是否为「可见答复」事件。
     *
     * <p>只认 {@code REASONING}（逐块答复文本）与 {@code AGENT_RESULT}（最终装配结果，
     * 可在丢块时补齐）。{@code TOOL_RESULT} / {@code HINT} 属于内部过程，外发会让用户
     * 看到工具原始输出。</p>
     */
    private boolean isReplyEvent(Event event) {
        if (event == null || event.getType() == null) {
            return false;
        }
        return event.getType() == EventType.REASONING || event.getType() == EventType.AGENT_RESULT;
    }

    /** 流式收尾：与 {@link #runAgentTurn} 的同步收尾逐项对应，写入顺序不可调换。 */
    private AiAgentStreamEvent finishStream(
            String fullText,
            AiAgentRouteRequest routeRequest,
            AiAgentProfile profile,
            String message,
            String sessionKey,
            String traceId,
            long start,
            RuntimeContext context2) {
        long elapsed = System.currentTimeMillis() - start;
        if (!hasText(fullText)) {
            String failMsg = "AI 智能体未返回有效内容";
            clearTaskPartition(profile, sessionKey);
            com.zimo.framework.ai.observ.TraceCollector.endFor(
                    traceId, "failed", message, failMsg, 0, elapsed);
            return AiAgentStreamEvent.error(failMsg, elapsed);
        }
        com.zimo.framework.ai.observ.TraceCollector.stepFor(traceId, "generation", "智能体回复",
                "{\"prompt\":\"" + safeJson(message) + "\"}",
                "{\"response\":\"" + safeJson(fullText) + "\"}", elapsed, "ok");
        // 必须在 endFor 之前：收尾之后发出的 span 既不导出也不释放
        writebackMemory(routeRequest, traceId, message, fullText, context2, profile);
        clearTaskPartition(profile, sessionKey);
        com.zimo.framework.ai.observ.TraceCollector.endFor(traceId, "ok", message, fullText,
                estimateTokens(fullText), elapsed);
        return AiAgentStreamEvent.done(fullText, elapsed);
    }

    /** 流式轮次失败收尾：与同步 catch 分支逐项对应。 */
    private AiAgentStreamEvent failEvent(
            Throwable exception,
            String sessionKey,
            AiAgentProfile profile,
            String traceId,
            String message,
            long start) {
        long elapsed = System.currentTimeMillis() - start;
        String failMsg = "AI 智能体调用失败：" + safeMessage(exception);
        clearTaskPartition(profile, sessionKey);
        com.zimo.framework.ai.observ.TraceCollector.endFor(
                traceId, "failed", message, failMsg, 0, elapsed);
        return AiAgentStreamEvent.error(failMsg, elapsed);
    }

    /**
     * 流式文本累加器：同时兼容「增量块」与「累积快照」两种事件语义。
     *
     * <p><b>为什么不假定其中一种</b>：AgentScope 的 {@code incremental} 开关决定事件携带
     * 的是块还是全量快照，而这两种形态在该库的公开 API 上没有稳定承诺。这里用
     * 「新文本是否以已累积文本为前缀」自行判别：是前缀 → 视为快照，只外发多出来的尾部；
     * 不是前缀 → 视为块，整体外发。任一种语义（甚至两者混用）都能拼出正确结果，
     * 且快照的出现天然修正此前丢块造成的偏差。</p>
     */
    private static final class StreamAccumulator {

        private final StringBuilder text = new StringBuilder();

        String text() {
            return text.toString();
        }

        /**
         * 合并一段事件文本。
         *
         * @param incoming 事件携带的文本
         * @return 本次应外发的增量，可能为空串（快照与已累积内容一致时）
         */
        String merge(String incoming) {
            if (incoming == null || incoming.isEmpty()) {
                return "";
            }
            String current = text.toString();
            if (!current.isEmpty() && incoming.startsWith(current)) {
                String suffix = incoming.substring(current.length());
                text.setLength(0);
                text.append(incoming);
                return suffix;
            }
            text.append(incoming);
            return incoming;
        }
    }

    /**
     * 任务终态清理 AgentState 的 task 分区（M4-3，PRD 标准 10）。
     *
     * <p><b>为什么必须清理</b>：AgentScope 把计划任务清单（task 分区）挂在会话状态上，
     * 不清理则上一轮的残留会被下一轮对话读到 —— 表现为模型莫名沿用旧计划，
     * 且外部完全看不出原因。</p>
     *
     * <p><b>为什么清理失败不影响返回</b>：清理是收尾动作，失败只应留下日志噪音，
     * 不该把一次成功的对话变成报错。这里吞掉异常并降级为 warn。</p>
     *
     * <p>注意存储 key 的第一段是 <b>userId</b>（AgentScope 的 {@code SlotRef} 语义），
     * 不是 agentId；第二段是完整会话隔离键。反解交给
     * {@link com.zimo.framework.ai.agent.AiHarnessSessionKey}。</p>
     */
    private void clearTaskPartition(AiAgentProfile profile, String sessionKey) {
        if (taskPartitionProbe == null || !hasText(sessionKey) || profile == null) {
            return;
        }
        try {
            String userId =
                    com.zimo.framework.ai.agent.AiHarnessSessionKey.userIdOf(sessionKey);
            if (taskPartitionProbe.clear(userId, sessionKey)) {
                log.debug("[agentstate] 任务终态已清理 task 分区 sessionKey={}", sessionKey);
            }
        } catch (RuntimeException exception) {
            log.warn("[agentstate] 任务终态清理失败 sessionKey={}：{}", sessionKey, exception.toString());
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

    /**
     * 把底层异常转成给用户看的失败原因：脱敏 + 归因。
     *
     * <p>脱敏是既有行为（key 可能出现在上游报文里）。归因是 2026-09-18 新增：
     * 上游原文会输出两遍同样的 JSON 且不指明是哪个配置的问题，导致排查被引向
     * 模型名 / base-url / 记忆模块等错误方向。这里叠加一行【归因】+【处置】，
     * 但<b>原始报文照旧保留</b> —— 归因是辅助，证据本身不能被替换掉。</p>
     */
    private String safeMessage(Throwable exception) {
        Throwable error = exception;
        while (error.getCause() != null && error.getCause() != error) {
            error = error.getCause();
        }
        String message = hasText(error.getMessage())
                ? error.getMessage()
                : error.getClass().getSimpleName();
        String apiKey = properties.getApiKey();
        String sanitized = hasText(apiKey) ? message.replace(apiKey, "[redacted]") : message;
        com.zimo.framework.ai.startup.ModelCallFailureDiagnosis.Diagnosis diagnosis =
                com.zimo.framework.ai.startup.ModelCallFailureDiagnosis.diagnose(sanitized);
        if (diagnosis == null) {
            return sanitized;
        }
        log.warn("[AI 调用归因] {} | 处置：{}", diagnosis.summary(), diagnosis.action());
        return diagnosis.render() + "\n原文：" + sanitized;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
