package com.zimo.framework.ai.autoconfig;

import com.zimo.framework.common.storage.FileStorageService;
import com.zimo.framework.ai.AiAgentProperties;
import com.zimo.framework.ai.AiAgentService;
import com.zimo.framework.ai.agent.AiAgentProfile;
import com.zimo.framework.ai.agent.AiAgentProfileResolver;
import com.zimo.framework.ai.agent.AiAgentRouteRequest;
import com.zimo.framework.ai.agent.AiHarnessAgentFactory;
import com.zimo.framework.ai.agent.LoggingToolExecutionListener;
import com.zimo.framework.ai.agent.AiCapabilityProvider;
import com.zimo.framework.ai.agent.ToolExecutionListener;
import com.zimo.framework.ai.agent.AiHarnessAgentRegistry;
import com.zimo.framework.ai.agent.AiHarnessAgentRouter;
import com.zimo.framework.ai.agent.AiHarnessSessionKeyFactory;
import com.zimo.framework.ai.channel.AiChannelHandler;
import com.zimo.framework.ai.channel.AiChannelIntentHandler;
import com.zimo.framework.ai.chat.AiChatClient;
import com.zimo.framework.ai.chat.OpenAiCompatibleChatClient;
import com.zimo.module.agentmemory.memory.AiMemoryService;
import com.zimo.framework.ai.runtime.AiAgentRuntime;
import com.zimo.framework.ai.runtime.AiAgentRuntimeFactory;
import com.zimo.framework.common.skill.AiSkill;
import com.zimo.framework.ai.skill.AiSkillRegistry;
import com.zimo.framework.ai.skill.DefaultAiSkills;
import com.zimo.framework.ai.skill.ToolApprovalHandler;
import com.zimo.framework.ai.skill.ToolGuard;
import com.zimo.framework.ai.skill.ToolHook;
import com.zimo.framework.ai.skill.ToolPipeline;
import com.zimo.framework.ai.preset.AiAgentPreset;
import com.zimo.framework.ai.preset.AiAgentPresetRegistry;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * AI 智能体运行时自动装配。
 *
 * <p>仅装配聊天、技能注册、渠道、MCP 与 A2A 运行时能力；管理接口、Service 和 DAO
 * 由 module-ai 业务模块负责。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
@AutoConfiguration(after = RestClientAutoConfiguration.class)
@EnableConfigurationProperties(AiAgentProperties.class)
@ConditionalOnProperty(prefix = "ai.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiAgentAutoConfiguration {

    /** 注册回显内置技能。 */
    @Bean
    @ConditionalOnMissingBean(name = "echoAiSkill")
    public AiSkill echoAiSkill() {
        return DefaultAiSkills.echo();
    }

    /** 注册摘要内置技能。 */
    @Bean
    @ConditionalOnMissingBean(name = "summarizeAiSkill")
    public AiSkill summarizeAiSkill() {
        return DefaultAiSkills.summarize();
    }

    /** 注册计划生成内置技能。 */
    @Bean
    @ConditionalOnMissingBean(name = "generatePlanAiSkill")
    public AiSkill generatePlanAiSkill() {
        return DefaultAiSkills.generatePlan();
    }

    /** 注册插件任务路由内置技能。 */
    @Bean
    @ConditionalOnMissingBean(name = "routePluginTaskAiSkill")
    public AiSkill routePluginTaskAiSkill() {
        return DefaultAiSkills.routePluginTask();
    }

    /**
     * 互操作规则文件读取器（AGENTS.md / CLAUDE.md 发现与解析，dsh A8）。
     * 从应用工作目录向上最多 8 层发现，文件名按配置顺序优先。
     */
    @Bean
    @ConditionalOnMissingBean(name = "agentInstructionReader")
    public com.zimo.framework.ai.interop.AgentInstructionReader agentInstructionReader(
            AiAgentProperties properties) {
        return new com.zimo.framework.ai.interop.AgentInstructionReader(
                properties.getInteropInstructionFiles(),
                null,
                8);
    }

    /** 互操作规则注入服务：渲染规则片段、供技能与提示词拼接消费。 */
    @Bean
    @ConditionalOnMissingBean
    public com.zimo.framework.ai.interop.AgentInteropService agentInteropService(
            com.zimo.framework.ai.interop.AgentInstructionReader reader) {
        return new com.zimo.framework.ai.interop.AgentInteropService(reader);
    }

    /** 互操作规则内存技能（interop_instructions，可按 topic 过滤）。 */
    @Bean
    @ConditionalOnMissingBean(name = "instructionFileSkill")
    public com.zimo.framework.ai.interop.InstructionFileSkill instructionFileSkill(
            com.zimo.framework.ai.interop.AgentInteropService interopService,
            AiAgentProperties properties) {
        return new com.zimo.framework.ai.interop.InstructionFileSkill(
                interopService, properties.getInteropInstructionMaxChars());
    }

    /** 外部 harness 子智能体 provider（dsh A8 meta-harness）：解析 agentConfig.externalHarness.tasks[]。 */
    @Bean
    @ConditionalOnMissingBean
    public com.zimo.framework.ai.interop.ExternalHarnessSubagentProvider externalHarnessSubagentProvider() {
        return com.zimo.framework.ai.interop.ExternalHarnessSubagentProvider.localDefault();
    }

    /**
     * AGENTS.md hook 注册表（dsh A8：Claude Code hooks 桥接）：
     * 从规则文件解析 {@code hook:} 指令并按触发点注册。
     */
    @Bean
    @ConditionalOnMissingBean(name = "agentHookRegistry")
    public com.zimo.framework.ai.interop.AgentHookRegistry agentHookRegistry(
            com.zimo.framework.ai.interop.AgentInstructionReader reader,
            AiAgentProperties properties) {
        com.zimo.framework.ai.interop.AgentHookRegistry registry =
                new com.zimo.framework.ai.interop.AgentHookRegistry();
        if (!properties.isInteropInstructionFilesEnabled()) {
            return registry;
        }
        com.zimo.framework.ai.interop.AgentHookParser parser =
                new com.zimo.framework.ai.interop.AgentHookParser();
        for (com.zimo.framework.ai.interop.InteropInstruction instruction : reader.findAll()) {
            registry.registerAll(parser.parse(instruction.instructions()));
        }
        return registry;
    }

    /** AGENTS.md hook → ToolPipeline 桥接器（ToolHook Bean，自动汇入工具流水线 pre/post 阶段）。 */
    @Bean
    @ConditionalOnMissingBean(name = "agentHookBridge")
    public com.zimo.framework.ai.interop.AgentHookBridge agentHookBridge(
            com.zimo.framework.ai.interop.AgentHookRegistry registry,
            com.zimo.framework.ai.sandbox.SandboxBackend sandboxBackend) {
        return new com.zimo.framework.ai.interop.AgentHookBridge(registry, sandboxBackend);
    }

    /** 汇总全部技能并创建运行时注册表。 */
    @Bean
    @ConditionalOnMissingBean
    public AiSkillRegistry aiSkillRegistry(List<AiSkill> skills, RestClient.Builder restClientBuilder,
            java.util.List<ToolHook> hooks,
            java.util.List<ToolGuard> guards,
            org.springframework.beans.factory.ObjectProvider<ToolApprovalHandler> approvalProvider,
            AiAgentProperties properties) {
        ToolPipeline pipeline = new ToolPipeline(
                hooks == null ? java.util.List.of() : hooks,
                guards == null ? java.util.List.of() : guards,
                approvalProvider.getIfAvailable(),
                properties.getToolPipelineMaxRetries());
        List<AiSkill> allSkills = new java.util.ArrayList<>(skills == null ? List.of() : skills);
        if (properties.isInteropInstructionFilesEnabled()
                && properties.isInteropInstructionSkill()) {
            allSkills.add(new com.zimo.framework.ai.interop.InstructionFileSkill(
                    new com.zimo.framework.ai.interop.AgentInteropService(
                            new com.zimo.framework.ai.interop.AgentInstructionReader(
                                    properties.getInteropInstructionFiles(), null, 8)),
                    properties.getInteropInstructionMaxChars()));
        }
        return new AiSkillRegistry(allSkills, restClientBuilder, pipeline);
    }

    /** 创建智能体运行时工厂。 */
    @Bean
    @ConditionalOnMissingBean
    public AiAgentRuntimeFactory aiAgentRuntimeFactory() {
        return new AiAgentRuntimeFactory();
    }

    /** 根据配置和技能注册表创建智能体运行时描述。 */
    @Bean
    @ConditionalOnMissingBean
    public AiAgentRuntime aiAgentRuntime(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry,
            AiAgentRuntimeFactory runtimeFactory) {
        return runtimeFactory.create(properties, skillRegistry);
    }

    /** 创建 OpenAI 兼容聊天客户端。 */
    @Bean
    @ConditionalOnMissingBean
    public AiChatClient aiChatClient(AiAgentProperties properties, RestClient.Builder restClientBuilder) {
        return new OpenAiCompatibleChatClient(properties, restClientBuilder);
    }

    /** 智能体模式预设注册表（模式=能力+配置组合），支持业务注册自定义预设。 */
    @Bean
    @ConditionalOnMissingBean
    public AiAgentPresetRegistry aiAgentPresetRegistry() {
        return new AiAgentPresetRegistry();
    }

    /** 创建按配置构建独立 HarnessAgent 的工厂。 */
    @Bean
    @ConditionalOnMissingBean
    public AiHarnessAgentFactory aiHarnessAgentFactory(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry,
            ObjectProvider<FileStorageService> storageServiceProvider,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            // 记忆服务为可选依赖：由 module-agent-memory 提供。未引入该模块时 getIfAvailable() 返回 null，
            // 工厂内部会跳过记忆读写工具注册，不影响其余能力（见 AiHarnessAgentFactory 的 memoryService 为空分支）。
            ObjectProvider<AiMemoryService> memoryServiceProvider,
            java.util.List<ToolExecutionListener> toolListeners,
            java.util.List<AiCapabilityProvider> capabilities,
            org.springframework.beans.factory.ObjectProvider<com.zimo.framework.ai.plugin.DynamicPluginManager> pluginManagerProvider,
            AiAgentPresetRegistry presetRegistry,
            com.zimo.framework.ai.interop.ExternalHarnessSubagentProvider externalHarnessProvider,
            // 可观测中间件均为可选：trace 中间件按 ai.agent.trace-middleware-enabled 决定是否注册；
            // OTel 中间件按 ai.agent.otel-enabled 决定。两者缺失时对应通道静默关闭，不影响 agent 构建。
            org.springframework.beans.factory.ObjectProvider<com.zimo.framework.ai.observ.HarnessTraceMiddleware> traceMiddlewareProvider,
            org.springframework.beans.factory.ObjectProvider<io.agentscope.core.tracing.OtelTracingMiddleware> otelMiddlewareProvider) {
        FileStorageService storageService = storageServiceProvider.getIfAvailable();
        AiMemoryService memoryService = memoryServiceProvider.getIfAvailable();
        return new AiHarnessAgentFactory(properties, skillRegistry, storageService, objectMapper, memoryService,
                toolListeners, capabilities, pluginManagerProvider.getIfAvailable(), presetRegistry,
                externalHarnessProvider,
                traceMiddlewareProvider.getIfAvailable(),
                otelMiddlewareProvider.getIfAvailable());
    }

    /**
     * 自研链路桥接中间件：把 AgentScope 的 agent/modelCall/acting 三处 hook 写入 TraceCollector。
     *
     * <p>补齐现有链路缺失的推理、模型调用与工具执行节点。默认开启
     * （{@code ai.agent.trace-middleware-enabled=true}）；关闭时不注册该 Bean，
     * 工厂侧 {@code applyObservability} 自动跳过。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "ai.agent",
            name = "trace-middleware-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public com.zimo.framework.ai.observ.HarnessTraceMiddleware harnessTraceMiddleware() {
        return new com.zimo.framework.ai.observ.HarnessTraceMiddleware();
    }

    /**
     * 官方 OTel 追踪中间件：产出 {@code invoke_agent}/{@code chat}/{@code execute_tool} 三级 span 树。
     *
     * <p>默认关闭（{@code ai.agent.otel-enabled=false}）：不注册时中间件视为 no-op，
     * 零网络开销。开启后需配合 {@code OtelTracingInitializer} 注册 SDK 才能真正导出。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "ai.agent",
            name = "otel-enabled",
            havingValue = "true")
    public io.agentscope.core.tracing.OtelTracingMiddleware otelTracingMiddleware() {
        return new io.agentscope.core.tracing.OtelTracingMiddleware();
    }

    /**
     * OpenTelemetry SDK 初始化器：为 OTel 中间件注册全局 TracerProvider 与 OTLP 导出器。
     *
     * <p>必须与其他 Bean 同生命周期：注册全局实例后由 Spring 关闭钩子刷新待导出 span，
     * 否则进程退出时缓冲区里的 span 会全部丢失。</p>
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "ai.agent",
            name = "otel-enabled",
            havingValue = "true")
    public com.zimo.framework.ai.observ.OtelTracingInitializer otelTracingInitializer(
            AiAgentProperties properties) {
        com.zimo.framework.ai.observ.OtelTracingInitializer initializer =
                new com.zimo.framework.ai.observ.OtelTracingInitializer(properties);
        initializer.initialize();
        return initializer;
    }

    /** 动态插件管理器（data/plugins/*.jar，启动时自动扫描装载）。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public com.zimo.framework.ai.plugin.DynamicPluginManager dynamicPluginManager(
            AiAgentProperties properties) {
        com.zimo.framework.ai.plugin.DynamicPluginManager manager =
                new com.zimo.framework.ai.plugin.DynamicPluginManager(
                        properties.getPluginDir());
        // 启动时扫描已存在插件 jar
        java.io.File dir = new java.io.File(properties.getPluginDir());
        if (dir.isDirectory()) {
            java.io.File[] jars = dir.listFiles((d, n) -> n.endsWith(".jar"));
            if (jars != null) {
                for (java.io.File jar : jars) {
                    manager.loadJar(jar.toPath());
                }
            }
        }
        // 插件热重载：监听 jar 变更（新增/修改/删除自动 reload）
        if (properties.isPluginWatchEnabled()) {
            manager.startWatcher(properties.getPluginWatchIntervalMs());
        }
        return manager;
    }

    /** 轻量事件总线（Spring 事件桥，三类 AI 事件域）。 */
    @Bean
    @ConditionalOnMissingBean
    public com.zimo.framework.common.ai.event.AiEventPublisher aiEventPublisher(
            org.springframework.context.ApplicationEventPublisher publisher) {
        return new com.zimo.framework.ai.event.SpringAiEventPublisher(publisher);
    }

    /** 工具调用事件发布钩子（ToolCallEvent）。 */
    @Bean
    @ConditionalOnMissingBean(name = "eventPublishingToolExecutionListener")
    public ToolExecutionListener eventPublishingToolExecutionListener(
            com.zimo.framework.common.ai.event.AiEventPublisher eventPublisher) {
        return new com.zimo.framework.ai.agent.EventPublishingToolExecutionListener(eventPublisher);
    }

    /** 默认沙箱后端（本地直通）；外部可注册自定义 SandboxBackend 替换（对应 dsh ctx.sandbox）。 */
    @Bean
    @ConditionalOnMissingBean
    public com.zimo.framework.ai.sandbox.SandboxBackend localSandboxBackend() {
        return new com.zimo.framework.ai.sandbox.LocalSandboxBackend();
    }

    /** 默认工具执行钩子（日志审计）；外部可注册其他 ToolExecutionListener Bean 组合。 */
    @Bean
    @ConditionalOnMissingBean
    public ToolExecutionListener loggingToolExecutionListener() {
        return new LoggingToolExecutionListener();
    }

    /** 创建具备租户隔离、配置指纹和 LRU 回收能力的 HarnessAgent 注册表。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public AiHarnessAgentRegistry aiHarnessAgentRegistry(
            AiHarnessAgentFactory factory,
            AiAgentProperties properties) {
        return new AiHarnessAgentRegistry(factory, properties);
    }

    /** 创建五维会话隔离键工厂。 */
    @Bean
    @ConditionalOnMissingBean
    public AiHarnessSessionKeyFactory aiHarnessSessionKeyFactory() {
        return new AiHarnessSessionKeyFactory();
    }

    /** 创建支持渠道绑定和请求租户默认配置的分层路由器。 */
    @Bean
    @ConditionalOnMissingBean
    public AiHarnessAgentRouter aiHarnessAgentRouter(
            AiAgentProperties properties,
            ObjectProvider<AiAgentProfileResolver> profileResolver,
            org.springframework.beans.factory.ObjectProvider<com.zimo.framework.ai.interop.AgentInteropService> interopProvider) {
        return AiHarnessAgentRouter.withDefaultProvider(
                profileResolver.getIfAvailable(),
                request -> defaultProfile(properties, request, interopProvider.getIfAvailable()));
    }

    /** 创建智能体聊天服务。 */
    @Bean
    @ConditionalOnMissingBean
    public AiAgentService aiAgentService(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry,
            AiAgentRuntime runtime,
            AiChatClient chatClient,
            AiHarnessAgentRouter router,
            AiHarnessAgentRegistry registry,
            AiHarnessSessionKeyFactory sessionKeyFactory,
            org.springframework.beans.factory.ObjectProvider<com.zimo.framework.common.storage.FileStorageService> fileStorageProvider,
            org.springframework.beans.factory.ObjectProvider<com.zimo.module.agentmemory.storage.OltpMemoryRepository> oltpProvider,
            org.springframework.beans.factory.ObjectProvider<com.zimo.framework.common.ai.event.AiEventPublisher> eventPublisherProvider,
            java.util.List<com.zimo.framework.ai.agent.AiRequestInterceptor> requestInterceptors,
            java.util.List<com.zimo.framework.ai.agent.AiAgentMiddleware> middlewares,
            org.springframework.beans.factory.ObjectProvider<com.zimo.framework.ai.plugin.DynamicPluginManager> pluginManagerProvider) {
        List<com.zimo.framework.ai.agent.AiAgentMiddleware> allMiddlewares =
                new java.util.ArrayList<>(middlewares == null ? List.of() : middlewares);
        com.zimo.framework.ai.plugin.DynamicPluginManager pluginManager = pluginManagerProvider.getIfAvailable();
        if (pluginManager != null) {
            allMiddlewares.addAll(pluginManager.dynamicMiddlewares());
        }
        return new AiAgentService(
                properties,
                skillRegistry,
                runtime,
                chatClient,
                router,
                registry,
                sessionKeyFactory,
                fileStorageProvider.getIfAvailable(),
                oltpProvider.getIfAvailable(),
                eventPublisherProvider.getIfAvailable(),
                requestInterceptors,
                allMiddlewares,
                pluginManager == null ? null : pluginManager.eventBus());
    }

    /** 创建渠道消息处理器，并按需使用业务模块提供的默认智能体解析器。 */
    @Bean
    @ConditionalOnMissingBean
    public AiChannelHandler aiChannelHandler(
            AiAgentService aiAgentService,
            AiSkillRegistry skillRegistry,
            ObjectProvider<AiAgentProfileResolver> profileResolver,
            List<AiChannelIntentHandler> intentHandlers) {
        return new AiChannelHandler(
                aiAgentService, skillRegistry, profileResolver.getIfAvailable(), intentHandlers);
    }

    // 注：AiMemoryService / AiMemorySensitiveFilter Bean 已迁移至 module-agent-memory
    // （AgentMemoryAutoConfiguration），此处不再重复定义，避免双实例。

    private AiAgentProfile defaultProfile(
            AiAgentProperties properties,
            AiAgentRouteRequest request,
            com.zimo.framework.ai.interop.AgentInteropService interopService) {
        String tenantId = request == null || request.tenantId() == null
                        || request.tenantId().isBlank()
                ? "_"
                : request.tenantId().trim();
        String agentId = properties.getName() == null || properties.getName().isBlank()
                ? "ai-agent"
                : properties.getName().trim();
        String systemPrompt = properties.getSystemPrompt();
        if (properties.isInteropInstructionFilesEnabled() && interopService != null) {
            String block = interopService.instructionBlock(
                    properties.getInteropInstructionMaxChars());
            if (!block.isBlank()) {
                systemPrompt = (systemPrompt == null ? "" : systemPrompt)
                        + "\n\n【仓库规则（AGENTS.md/CLAUDE.md）】\n" + block;
            }
        }
        return new AiAgentProfile(
                agentId,
                tenantId,
                agentId,
                properties.getModelName(),
                systemPrompt,
                List.of(),
                true);
    }
}
