package com.zimo.framework.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.common.storage.FileStorageService;
import com.zimo.framework.ai.AiAgentProperties;
import com.zimo.framework.ai.agent.memory.RocksdbAgentStateStore;
import com.zimo.framework.ai.agent.memory.RocksdbBaseStore;
import com.zimo.framework.ai.skill.AiSkillDescriptor;
import com.zimo.framework.ai.skill.AiSkillRegistry;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.DistributedStore;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import com.zimo.framework.ai.preset.AiAgentPreset;
import com.zimo.framework.ai.preset.AiAgentPresetRegistry;
import com.zimo.module.agentmemory.memory.AiMemoryAgentTool;
import com.zimo.module.agentmemory.memory.AiMemoryService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 根据已路由的运行时配置构建独立 HarnessAgent，支持 5 种智能体类型：
 *
 * <ul>
 *   <li>{@code conversation} 普通对话：系统提示词 + 模型 + 绑定技能</li>
 *   <li>{@code rag} 检索增强：知识库文档注入 + 检索指令 + 长期记忆检索工具</li>
 *   <li>{@code tool} 工具调用：全量技能 Toolkit + 优先使用工具的提示词</li>
 *   <li>{@code plan} 规划执行：Harness 原生 PlanMode（先规划后执行）</li>
 *   <li>{@code graph} 图任务流：声明子智能体节点，主智能体按图编排分发</li>
 * </ul>
 *
 * <p>工作区按租户和智能体分目录，模型、系统提示词、技能白名单与上下文压缩配置在实例创建时固定。
 * 当 {@link FileStorageService}（RocksDB）可用时，智能体工作区与长期记忆持久化到 RocksDB。</p>
 *
 * @author Codex
 * @since 2026-07-25
 */
public class AiHarnessAgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AiHarnessAgentFactory.class);

    private final AiAgentProperties properties;
    private final AiSkillRegistry skillRegistry;
    private final FileStorageService storageService;
    private final ObjectMapper objectMapper;
    private final AiMemoryService memoryService;
    /** 工具执行钩子列表（对应 dsh tools/* 把关流水线）。 */
    private final List<ToolExecutionListener> toolListeners;
    /** 能力提供方列表（对应 dsh 能力 Seam Service Provider）。 */
    private final List<AiCapabilityProvider> capabilities;
    /** 动态插件管理器（可空）：装配时合并插件贡献的能力/钩子。 */
    private final com.zimo.framework.ai.plugin.DynamicPluginManager pluginManager;
    /** preset 注册表（可空）：为空时类型策略退化为内建硬编码。 */
    private final AiAgentPresetRegistry presetRegistry;
    /** 外部 harness 子智能体 provider（dsh A8 meta-harness）；默认解析 agentConfig.externalHarness.tasks[]。 */
    private final com.zimo.framework.ai.interop.ExternalHarnessSubagentProvider externalHarnessProvider;

    /**
     * 创建 HarnessAgent 工厂（不带 JSON 解析器，agentConfig 将按空白处理）。
     *
     * @param properties starter 配置，不允许为空
     * @param skillRegistry 管理技能注册表，不允许为空
     */
    public AiHarnessAgentFactory(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry) {
        this(properties, skillRegistry, null, null, null);
    }

    /**
     * 创建 HarnessAgent 工厂。
     *
     * @param properties starter 配置，不允许为空
     * @param skillRegistry 管理技能注册表，不允许为空
     * @param storageService RocksDB 文件存储服务；为 {@code null} 时回退到本地文件系统
     */
    public AiHarnessAgentFactory(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry,
            FileStorageService storageService) {
        this(properties, skillRegistry, storageService, null, null);
    }

    /**
     * 创建 HarnessAgent 工厂。
     *
     * @param properties starter 配置，不允许为空
     * @param skillRegistry 管理技能注册表，不允许为空
     * @param storageService RocksDB 文件存储服务；为 {@code null} 时回退到本地文件系统
     * @param objectMapper JSON 解析器，用于解析 agentConfig；为 {@code null} 时忽略类型专属配置
     * @param memoryService 记忆服务；为 {@code null} 时不注册记忆读写工具
     */
    public AiHarnessAgentFactory(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry,
            FileStorageService storageService,
            ObjectMapper objectMapper,
            AiMemoryService memoryService) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.skillRegistry = Objects.requireNonNull(
                skillRegistry,
                "skillRegistry must not be null");
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        this.memoryService = memoryService;
        this.toolListeners = java.util.List.of();
        this.capabilities = java.util.List.of();
        this.pluginManager = null;
        this.presetRegistry = null;
        this.externalHarnessProvider = null;
    }

    /**
     * 全量构造（工具执行钩子列表）。
     *
     * @param toolListeners 工具执行钩子（可空）；对应 dsh tools/* 把关流水线
     */
    public AiHarnessAgentFactory(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry,
            FileStorageService storageService,
            ObjectMapper objectMapper,
            AiMemoryService memoryService,
            List<ToolExecutionListener> toolListeners) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.skillRegistry = Objects.requireNonNull(
                skillRegistry,
                "skillRegistry must not be null");
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        this.memoryService = memoryService;
        this.toolListeners = toolListeners == null ? java.util.List.of() : toolListeners;
        this.capabilities = java.util.List.of();
        this.pluginManager = null;
        this.presetRegistry = null;
        this.externalHarnessProvider = null;
    }

    /**
     * 全量构造（工具执行钩子 + 能力提供方）。
     */
    public AiHarnessAgentFactory(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry,
            FileStorageService storageService,
            ObjectMapper objectMapper,
            AiMemoryService memoryService,
            List<ToolExecutionListener> toolListeners,
            List<AiCapabilityProvider> capabilities) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.skillRegistry = Objects.requireNonNull(
                skillRegistry,
                "skillRegistry must not be null");
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        this.memoryService = memoryService;
        this.toolListeners = toolListeners == null ? java.util.List.of() : toolListeners;
        this.capabilities = capabilities == null ? java.util.List.of() : capabilities;
        this.pluginManager = null;
        this.presetRegistry = null;
        this.externalHarnessProvider = null;
    }

    /**
     * 全量构造（工具执行钩子 + 能力提供方 + 动态插件管理器）。
     */
    public AiHarnessAgentFactory(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry,
            FileStorageService storageService,
            ObjectMapper objectMapper,
            AiMemoryService memoryService,
            List<ToolExecutionListener> toolListeners,
            List<AiCapabilityProvider> capabilities,
            com.zimo.framework.ai.plugin.DynamicPluginManager pluginManager) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.skillRegistry = Objects.requireNonNull(
                skillRegistry,
                "skillRegistry must not be null");
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        this.memoryService = memoryService;
        this.toolListeners = toolListeners == null ? java.util.List.of() : toolListeners;
        this.capabilities = capabilities == null ? java.util.List.of() : capabilities;
        this.pluginManager = pluginManager;
        this.presetRegistry = null;
        this.externalHarnessProvider = null;
    }

    /**
     * 全量构造（工具执行钩子 + 能力提供方 + 动态插件管理器 + preset 注册表）。
     */
    public AiHarnessAgentFactory(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry,
            FileStorageService storageService,
            ObjectMapper objectMapper,
            AiMemoryService memoryService,
            List<ToolExecutionListener> toolListeners,
            List<AiCapabilityProvider> capabilities,
            com.zimo.framework.ai.plugin.DynamicPluginManager pluginManager,
            AiAgentPresetRegistry presetRegistry) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.skillRegistry = Objects.requireNonNull(
                skillRegistry,
                "skillRegistry must not be null");
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        this.memoryService = memoryService;
        this.toolListeners = toolListeners == null ? java.util.List.of() : toolListeners;
        this.capabilities = capabilities == null ? java.util.List.of() : capabilities;
        this.pluginManager = pluginManager;
        this.presetRegistry = presetRegistry;
        this.externalHarnessProvider = null;
    }

    /**
     * 全量构造（工具执行钩子 + 能力提供方 + 动态插件管理器 + preset 注册表 + 外部 harness 子 agent provider）。
     */
    public AiHarnessAgentFactory(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry,
            FileStorageService storageService,
            ObjectMapper objectMapper,
            AiMemoryService memoryService,
            List<ToolExecutionListener> toolListeners,
            List<AiCapabilityProvider> capabilities,
            com.zimo.framework.ai.plugin.DynamicPluginManager pluginManager,
            AiAgentPresetRegistry presetRegistry,
            com.zimo.framework.ai.interop.ExternalHarnessSubagentProvider externalHarnessProvider) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.skillRegistry = Objects.requireNonNull(
                skillRegistry,
                "skillRegistry must not be null");
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        this.memoryService = memoryService;
        this.toolListeners = toolListeners == null ? java.util.List.of() : toolListeners;
        this.capabilities = capabilities == null ? java.util.List.of() : capabilities;
        this.pluginManager = pluginManager;
        this.presetRegistry = presetRegistry;
        this.externalHarnessProvider = externalHarnessProvider;
    }

    /**
     * 创建与缓存键匹配的 HarnessAgent。
     *
     * @param profile 已通过启用状态和租户校验的智能体配置
     * @param key 注册表缓存键，租户和智能体标识必须与 profile 一致
     * @return 已完成模型、工作区、技能、压缩与类型策略配置的 HarnessAgent
     */
    public HarnessAgent create(
            AiAgentProfile profile,
            AiHarnessAgentKey key) {
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(key, "key must not be null");
        validateKey(profile, key);

        String basePrompt = resolvedSystemPrompt(profile);
        Path workspace = workspace(key);
        JsonNode config = parseConfig(profile.agentConfig());
        Toolkit toolkit = managedToolkit(profile);
        if (memoryService != null) {
            AiMemoryAgentTool.register(toolkit, memoryService, profile.tenantId());
        }
        for (AiCapabilityProvider capability : capabilities) {
            capability.contribute(toolkit, profile);
        }
        if (pluginManager != null) {
            for (AiCapabilityProvider dynamic : pluginManager.dynamicCapabilities()) {
                dynamic.contribute(toolkit, profile);
            }
        }

        AiAgentPreset preset = resolvePreset(profile.agentType());
        HarnessAgent.Builder builder = newBuilder()
                .agentId(key.agentId())
                .name(resolvedName(profile))
                .sysPrompt(applyTypePrompt(basePrompt, profile.agentType(), config, preset))
                .model(resolvedModel(profile, preset))
                .toolkit(toolkit)
                .maxIters(resolvedMaxIters(preset))
                .workspace(workspace);
        applyCompaction(builder);
        applyRocksdbMemory(builder);
        applyTypeStrategy(builder, profile.agentType(), config, workspace, preset);
        return builder.build();
    }

    /**
     * 创建 AgentScope Builder，测试可覆写以验证装配参数。
     *
     * @return 新的 HarnessAgent Builder
     */
    protected HarnessAgent.Builder newBuilder() {
        return HarnessAgent.builder();
    }

    /* ---------------- 类型策略（preset 驱动） ---------------- */

    /** 解析 preset：优先注册表；注册表缺失（旧构造）时按类型构建内建等价预设。 */
    private AiAgentPreset resolvePreset(String agentType) {
        if (presetRegistry != null) {
            return presetRegistry.resolve(agentType);
        }
        return AiAgentPresetRegistry.builtin(agentType);
    }

    private void applyTypeStrategy(
            HarnessAgent.Builder builder,
            String agentType,
            JsonNode config,
            Path workspace,
            AiAgentPreset preset) {
        if (agentType == null) {
            return;
        }
        // Windows 兼容：禁用子智能体任务中间件（其 taskMap 文件名含冒号，
        // 在 Windows 路径校验失败）。graph 类型的子任务声明保留，编排退化为提示词引导。
        builder.disableSubagents();
        builder.disableDynamicSubagents();
        if (preset.hasAbility(AiAgentPreset.ABILITY_PLAN)) {
            applyPlan(builder, config, workspace, preset);
        }
        if (preset.hasAbility(AiAgentPreset.ABILITY_GRAPH)) {
            applyGraph(builder, config);
        }
        // rag/tool/conversation 依赖 sysPrompt 与 toolkit，无额外策略
    }

    /** Plan-and-Execute：启用 Harness 原生计划模式（plan_enter/write/exit + todo_write 白名单 + HITL）。 */
    private void applyPlan(HarnessAgent.Builder builder, JsonNode config, Path workspace, AiAgentPreset preset) {
        builder.enablePlanMode(true);
        if (config != null && config.path("planFileDir").isTextual()) {
            builder.planFileDirectory(config.path("planFileDir").asText());
        } else {
            builder.planFileDirectory(workspace.resolve("plans").toString());
        }
        boolean allowShell = (config != null && config.path("allowShell").asBoolean(false))
                || preset.hasAbility(AiAgentPreset.ABILITY_SHELL);
        if (allowShell) {
            builder.allowShellInPlanMode(true);
        }
        boolean taskList = (config != null && config.path("enableTaskList").asBoolean(false))
                || preset.hasAbility(AiAgentPreset.ABILITY_TASK_LIST);
        if (taskList) {
            builder.enableTaskList(true);
        }
    }

    /** Graph 图任务流：合并本地 tasks 与外部 harness 节点（dsh A8 meta-harness）。 */
    private void applyGraph(HarnessAgent.Builder builder, JsonNode config) {
        List<SubagentDeclaration> declarations = new ArrayList<>();
        if (config != null && config.path("tasks").isArray()) {
            config.path("tasks").forEach(task -> {
                String name = task.path("name").asText();
                if (name.isBlank()) {
                    return;
                }
                SubagentDeclaration.Builder declaration = SubagentDeclaration.builder()
                        .name(name)
                        .description(task.path("description").asText(""));
                if (task.path("tools").isArray()) {
                    List<String> tools = new ArrayList<>();
                    task.path("tools").forEach(tool -> tools.add(tool.asText()));
                    if (!tools.isEmpty()) {
                        declaration.tools(tools);
                    }
                }
                declarations.add(declaration.build());
            });
        }
        declarations.addAll(externalHarnessDeclarations(config));
        if (!declarations.isEmpty()) {
            builder.subagents(declarations);
        }
    }

    /** 通过外部 harness provider 解析远端子智能体声明。 */
    private List<SubagentDeclaration> externalHarnessDeclarations(JsonNode config) {
        List<SubagentDeclaration> declarations = new ArrayList<>();
        if (externalHarnessProvider == null) {
            return declarations;
        }
        try {
            for (com.zimo.framework.ai.interop.ExternalHarnessSubagent subagent
                    : externalHarnessProvider.extract(config)) {
                if (subagent != null && subagent.valid()) {
                    declarations.add(externalHarnessProvider.toDeclaration(subagent));
                }
            }
        } catch (Exception e) {
            log.warn("外部 harness 子智能体声明解析失败: {}", e.getMessage());
        }
        return declarations;
    }

    /** 按 preset 附加系统提示词片段（rag 知识库注入保留）。 */
    private String applyTypePrompt(String basePrompt, String agentType, JsonNode config, AiAgentPreset preset) {
        if (basePrompt == null) {
            basePrompt = "";
        }
        StringBuilder prompt = new StringBuilder(basePrompt);
        if (preset == null || preset.promptSuffix() == null || preset.promptSuffix().isBlank()) {
            return prompt.toString();
        }
        String suffix = preset.promptSuffix();
        if (preset.hasAbility(AiAgentPreset.ABILITY_RAG)) {
            String knowledge = loadKnowledgeBase(config);
            if (hasText(knowledge)) {
                suffix = suffix.replace("{knowledge}", "\n知识库内容：\n" + knowledge);
            }
        }
        prompt.append(suffix);
        return prompt.toString();
    }

    /* ---------------- 基础装配 ---------------- */

    private void validateKey(AiAgentProfile profile, AiHarnessAgentKey key) {
        if (!key.tenantId().equals(profile.tenantId())
                || !key.agentId().equals(profile.id())) {
            throw new IllegalArgumentException("HarnessAgent key does not match profile");
        }
    }

    private void applyCompaction(HarnessAgent.Builder builder) {
        AiAgentProperties.ContextCompressionSettings compression =
                properties.getEffectiveContextCompressionSettings();
        if (!compression.enabled()) {
            return;
        }
        builder.compaction(CompactionConfig.builder()
                .triggerMessages(compression.triggerMessages())
                .keepMessages(compression.recentMessages())
                .build());
    }

    /**
     * 当 RocksDB 存储可用且开启长期记忆时，将智能体工作区、会话状态与记忆持久化到 RocksDB。
     *
     * <p>使用 {@link DistributedStore} 同时提供远程文件系统 {@code BaseStore} 与
     * 分布式 {@code AgentStateStore}（RocksDB 实现），满足 AgentScope 对
     * {@code RemoteFilesystemSpec} 必须搭配分布式状态存储的构建约束。</p>
     *
     * @param builder HarnessAgent Builder
     */
    private void applyRocksdbMemory(HarnessAgent.Builder builder) {
        AiAgentProperties.MemorySettings memory = properties.getEffectiveMemorySettings();
        if (!memory.enabled() || storageService == null) {
            return;
        }
        RocksdbBaseStore store = new RocksdbBaseStore(storageService);
        RocksdbAgentStateStore stateStore = new RocksdbAgentStateStore(storageService);
        builder.distributedStore(DistributedStore.builder()
                .agentStateStore(stateStore)
                .baseStore(store)
                .build());
        builder.memory(MemoryConfig.builder()
                .flushTrigger(MemoryConfig.FlushTrigger.throttled(Duration.ofSeconds(memory.flushTriggerSeconds())))
                .consolidationMinGap(Duration.ofMinutes(memory.consolidationMinGapMinutes()))
                .consolidationMaxTokens(memory.consolidationMaxTokens())
                .dailyFileRetentionDays(memory.dailyRetentionDays())
                .sessionRetentionDays(memory.sessionRetentionDays())
                .build());
    }

    private String resolvedName(AiAgentProfile profile) {
        return hasText(profile.name()) ? profile.name() : properties.getName();
    }

    /** 最大迭代次数（preset 覆盖优先于全局配置）。 */
    private int resolvedMaxIters(AiAgentPreset preset) {
        return preset == null
                ? properties.getMaxIters()
                : preset.overrideInt(AiAgentPreset.OVERRIDE_MAX_ITERS, properties.getMaxIters());
    }

    private String resolvedSystemPrompt(AiAgentProfile profile) {
        return hasText(profile.systemPrompt())
                ? profile.systemPrompt()
                : properties.getSystemPrompt();
    }

    private Model resolvedModel(AiAgentProfile profile) {
        return resolvedModel(profile, null);
    }

    /** 模型解析（preset 超参覆盖 temperature/maxTokens）。 */
    private Model resolvedModel(AiAgentProfile profile, AiAgentPreset preset) {
        String apiKey = properties.getApiKey();
        if (!hasText(apiKey)) {
            throw new IllegalStateException("AI model API key is not configured");
        }
        String modelName = hasText(profile.modelName())
                ? profile.modelName()
                : properties.getModelName();
        double temperature = preset == null
                ? properties.getTemperature()
                : preset.overrideDouble(AiAgentPreset.OVERRIDE_TEMPERATURE, properties.getTemperature());
        int maxTokens = preset == null
                ? properties.getMaxTokens()
                : preset.overrideInt(AiAgentPreset.OVERRIDE_MAX_TOKENS, properties.getMaxTokens());
        GenerateOptions options = GenerateOptions.builder()
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
        if ("dashscope_native".equalsIgnoreCase(properties.getModelType())) {
            return DashScopeChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(properties.getBaseUrl())
                    .modelName(modelName)
                    .defaultOptions(options)
                    .stream(true)
                    .build();
        }
        // 2.0.2 起 OpenAI 兼容模型统一走 DashScopeChatModel（dashscope 客户端即 OpenAI 兼容协议，
        // baseUrl 可指向自建 vLLM/兼容服务）
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(properties.getBaseUrl())
                .modelName(modelName)
                .defaultOptions(options)
                .stream(true)
                .build();
    }

    private Toolkit managedToolkit(AiAgentProfile profile) {
        Toolkit toolkit = new Toolkit();
        Map<String, AiSkillDescriptor> available = new LinkedHashMap<>();
        for (AiSkillDescriptor descriptor : skillRegistry.list()) {
            available.put(descriptor.name(), descriptor);
        }
        for (String skillId : profile.skillIds()) {
            AiSkillDescriptor descriptor = available.get(skillId);
            if (descriptor != null) {
                ToolExecutionListener chain = listenersChain();
                toolkit.registerAgentTool(new AiSkillAgentTool(descriptor, skillRegistry, chain));
                if (chain != null) {
                    chain.onToolRegistered(descriptor.name());
                }
            }
        }
        return toolkit;
    }

    /** 组合多个监听器（含动态插件贡献）为链式代理；空列表返回 null（直通）。 */
    private ToolExecutionListener listenersChain() {
        java.util.List<ToolExecutionListener> all = new java.util.ArrayList<>(toolListeners);
        if (pluginManager != null) {
            all.addAll(pluginManager.dynamicListeners());
        }
        if (all.isEmpty()) {
            return null;
        }
        if (all.size() == 1) {
            return all.get(0);
        }
        List<ToolExecutionListener> copy = new java.util.ArrayList<>(all);
        return new ToolExecutionListener() {
            @Override
            public boolean onPreExecute(String toolName, Object params) {
                for (ToolExecutionListener l : copy) {
                    if (!l.onPreExecute(toolName, params)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public void onPostExecute(String toolName, Object params, Object result) {
                for (ToolExecutionListener l : copy) {
                    l.onPostExecute(toolName, params, result);
                }
            }

            @Override
            public void onError(String toolName, Object params, Throwable error) {
                for (ToolExecutionListener l : copy) {
                    l.onError(toolName, params, error);
                }
            }

            @Override
            public void onToolRegistered(String toolName) {
                for (ToolExecutionListener l : copy) {
                    l.onToolRegistered(toolName);
                }
            }
        };
    }

    private Path workspace(AiHarnessAgentKey key) {
        return Path.of(properties.getHarnessWorkspaceRoot())
                .resolve(safePathSegment(key.tenantId()))
                .resolve(safePathSegment(key.agentId()));
    }

    /** 解析 agentConfig JSON；无解析器或非法时返回 null。 */
    private JsonNode parseConfig(String agentConfig) {
        if (objectMapper == null || !hasText(agentConfig)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(agentConfig);
            return node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 agentConfig.knowledgeBase 目录加载知识库文档文本（.md/.txt/.json），截断至 24K 字符。 */
    private String loadKnowledgeBase(JsonNode config) {
        if (config == null || !config.path("knowledgeBase").isTextual()) {
            return null;
        }
        Path base = Path.of(config.path("knowledgeBase").asText());
        if (!Files.isDirectory(base)) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        try (Stream<Path> stream = Files.walk(base)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> isTextFile(p.getFileName().toString()))
                    .sorted()
                    .toList();
            for (Path file : files) {
                if (builder.length() >= 24000) {
                    break;
                }
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    builder.append("\n--- 文档: ").append(base.relativize(file)).append(" ---\n")
                            .append(truncate(content, 6000)).append("\n");
                } catch (Exception ignored) {
                    // 跳过不可读文件
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return builder.length() == 0 ? null : truncate(builder.toString(), 24000);
    }

    private boolean isTextFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".txt")
                || lower.endsWith(".json") || lower.endsWith(".markdown");
    }

    private String safePathSegment(String value) {
        String normalized = value == null ? "" : value.trim();
        // hutool SecureUtil.sha256：生成 64 位小写十六进制摘要，取前 12 位
        String hash = cn.hutool.crypto.SecureUtil.sha256(normalized);
        String readable = normalized.replaceAll("[^A-Za-z0-9._-]", "_");
        readable = readable.isBlank() ? "id" : readable;
        return readable + "-" + hash.substring(0, 12);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
