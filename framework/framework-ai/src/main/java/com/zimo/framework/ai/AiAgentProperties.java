package com.zimo.framework.ai;

import java.util.List;
import cn.hutool.core.util.StrUtil;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 智能体 starter 的统一配置属性。
 *
 * <p>配置前缀为 {@code ai.agent}，涵盖模型连接、旧版会话、HarnessAgent 注册表、
 * 工作区和上下文压缩。API Key 属于敏感配置，禁止写入日志或业务响应。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
@ConfigurationProperties(prefix = "ai.agent")
public class AiAgentProperties {
    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是 Production Studio 的智能体助理。用户通过系统页面、飞书机器人或其他通道与你对话。
            回答前先理解用户真实意图、结合会话上下文判断任务类型，再给出自然、明确、可执行的答复。
            如果信息不足，先提出一个简短澄清问题；如果可以直接处理，就先给结论，再给必要步骤或依据。
            不要机械复述用户问题，不要只给模板化回答，不要输出完整内部思维链。
            需要说明推理时，只输出简短的判断依据或操作计划。
            """;

    private boolean enabled = true;
    private String name = "ai-agent";
    private String modelName = "qwen-plus";
    private String modelType = "dashscope_chat";
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String apiKey;
    private double temperature = 0.7;
    private int maxTokens = 2000;
    private int maxIters = 5;
    private int chatHistoryLimit = 20;
    /** 进程内会话记忆最大会话数（AiConversationMemory 上限，超限淘汰最旧）。 */
    private int conversationMaxSessions = 512;
    /** 会话记忆共享存储开关：启用后 AiConversationMemory 走 RocksDB 持久化，多实例共享。 */
    private boolean conversationSharedStore = false;
    private boolean contextCompressionEnabled = true;
    /** 动态插件目录（jar 放入后自动装载）。 */
    private String pluginDir = "data/plugins";
    /** 插件热重载开关：启用后监听插件目录 jar 变更（新增/修改/删除自动 reload）。 */
    private boolean pluginWatchEnabled = false;
    /** 插件热重载轮询间隔（毫秒）。 */
    private long pluginWatchIntervalMs = 5000;
    private int contextCompressionTriggerMessages = 20;
    private int contextCompressionRecentMessages = 8;
    private int contextCompressionSummaryMaxCharacters = 4000;
    /** 摘要生成失败时的最大重试次数（压缩严格重试，对应 dsh compaction 重试语义）。 */
    private int contextCompressionMaxRetries = 2;
    /** 工具流水线执行失败时的最大重试次数（0 = 不重试）。 */
    private int toolPipelineMaxRetries = 1;
    private int harnessRegistryCapacity = 128;
    private String harnessWorkspaceRoot = ".agentscope/workspace";
    private String harnessWorkspaceVersion = "v1";
    private String systemPrompt = DEFAULT_SYSTEM_PROMPT;
    private boolean memoryEnabled = true;
    private int memoryFlushTriggerSeconds = 600;
    private int memoryConsolidationMinGapMinutes = 120;
    private int memoryConsolidationMaxTokens = 4000;
    private int memoryDailyRetentionDays = 90;
    private int memorySessionRetentionDays = 180;
    /** 用户长期记忆保留天数；超期记录在写入时惰性清理。 */
    private int memoryUserRetentionDays = 180;
    /** 用户长期记忆每类别最大条数；超限时淘汰最旧记录。 */
    private int memoryMaxRecordsPerCategory = 200;
    /** 长期记忆白名单类别，逗号分隔；为空表示允许所有类别（persona/preference/history/global 等）。 */
    private String memoryWhitelistCategories = "";
    /** 记忆敏感内容过滤开关，默认开启（手机号/身份证/银行卡/邮箱/密钥等自动脱敏）。 */
    private boolean memorySensitiveFiltering = true;
    /** 互操作规则文件启用开关（AGENTS.md/CLAUDE.md 读取，dsh A8）。 */
    private boolean interopInstructionFilesEnabled = true;
    /** 互操作规则文件名列表（逗号分隔，按顺序优先级发现）；默认 AGENTS.md,CLAUDE.md。 */
    private String interopInstructionFiles = "AGENTS.md,CLAUDE.md";
    /** 互操作规则文件最大注入字符数（超出截断，默认 8000）。 */
    private int interopInstructionMaxChars = 8000;
    /** 互操作规则文件是否通过专用内存技能（interop_instructions）提供（默认 true）。 */
    private boolean interopInstructionSkill = true;

    /**
     * 判断 AI 智能体运行能力是否启用。
     *
     * @return {@code true} 表示启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 AI 智能体运行能力开关。
     *
     * @param enabled {@code true} 启用，{@code false} 关闭
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取全局默认智能体名称。
     *
     * @return 默认智能体名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置全局默认智能体名称。
     *
     * @param name 智能体名称；空值由调用方回退
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取全局默认模型名称。
     *
     * @return 模型名称
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * 设置全局默认模型名称。
     *
     * @param modelName 模型服务支持的模型名称
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    /**
     * 获取模型接口类型。
     *
     * @return {@code dashscope_chat} 等 OpenAI 兼容类型，或 {@code dashscope_native} 原生类型
     */
    public String getModelType() {
        return modelType;
    }

    /**
     * 设置模型接口类型。
     *
     * @param modelType {@code dashscope_native} 使用原生 DashScope，其余类型使用 OpenAI 兼容接口
     */
    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    /**
     * 获取模型服务基础地址。
     *
     * @return OpenAI 兼容端点或原生 DashScope 根地址
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * 设置模型服务基础地址。
     *
     * @param baseUrl 模型服务地址，不应包含密钥
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 获取模型服务 API Key。
     *
     * @return 敏感 API Key；未配置时允许为空
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 设置模型服务 API Key。
     *
     * @param apiKey 敏感 API Key，禁止记录到日志
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 获取模型采样温度。
     *
     * @return 模型采样温度
     */
    public double getTemperature() {
        return temperature;
    }

    /**
     * 设置模型采样温度。
     *
     * @param temperature 模型供应商允许的采样温度
     */
    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    /**
     * 获取单次模型响应最大 token 数。
     *
     * @return 最大 token 数
     */
    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * 设置单次模型响应最大 token 数。
     *
     * @param maxTokens 正整数；具体上限由模型供应商决定
     */
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    /**
     * 获取 HarnessAgent 单次调用最大迭代次数。
     *
     * @return 最大迭代次数
     */
    public int getMaxIters() {
        return maxIters;
    }

    /**
     * 设置 HarnessAgent 单次调用最大迭代次数。
     *
     * @param maxIters 正整数，避免工具循环无限执行
     */
    public void setMaxIters(int maxIters) {
        this.maxIters = maxIters;
    }

    /**
     * 获取旧版 AiChatClient 会话保留条数。
     *
     * @return 会话历史条数上限
     */
    public int getChatHistoryLimit() {
        return chatHistoryLimit;
    }

    /**
     * 获取进程内会话记忆最大会话数。
     *
     * @return 正数配置值；非正数回退至默认值 {@code 512}
     */
    public int getConversationMaxSessions() {
        return conversationMaxSessions > 0 ? conversationMaxSessions : 512;
    }

    /**
     * 获取会话记忆共享存储开关。
     *
     * @return {@code true} 启用 RocksDB 共享存储（多实例共享会话记忆）
     */
    public boolean isConversationSharedStore() {
        return conversationSharedStore;
    }

    /**
     * 设置会话记忆共享存储开关。
     *
     * @param conversationSharedStore {@code true} 启用 RocksDB 共享存储
     */
    public void setConversationSharedStore(boolean conversationSharedStore) {
        this.conversationSharedStore = conversationSharedStore;
    }

    /**
     * 设置进程内会话记忆最大会话数。
     *
     * @param conversationMaxSessions 会话数；非正数由 getter 回退至默认值
     */
    public void setConversationMaxSessions(int conversationMaxSessions) {
        this.conversationMaxSessions = conversationMaxSessions;
    }

    /**
     * 设置旧版 AiChatClient 会话保留条数。
     *
     * @param chatHistoryLimit 非负历史条数上限
     */
    public void setChatHistoryLimit(int chatHistoryLimit) {
        this.chatHistoryLimit = chatHistoryLimit;
    }

    /**
     * 判断是否启用会话上下文摘要压缩。
     *
     * @return {@code true} 时主对话成功后可尝试压缩较早消息
     */
    public boolean isContextCompressionEnabled() {
        return contextCompressionEnabled;
    }

    /**
     * 设置是否启用会话上下文摘要压缩。
     *
     * @param contextCompressionEnabled {@code true} 启用压缩，{@code false} 保持原始消息截断行为
     */
    public void setContextCompressionEnabled(boolean contextCompressionEnabled) {
        this.contextCompressionEnabled = contextCompressionEnabled;
    }

    /**
     * 获取触发会话上下文压缩的原始消息数量阈值。
     *
     * @return 正数配置值；非正数时返回默认值 {@code 20}
     */
    public int getContextCompressionTriggerMessages() {
        return contextCompressionTriggerMessages > 0 ? contextCompressionTriggerMessages : 20;
    }

    /**
     * 设置触发会话上下文压缩的原始消息数量阈值。
     *
     * @param contextCompressionTriggerMessages 原始消息数量阈值；非正数由 getter 回退至默认值
     */
    public void setContextCompressionTriggerMessages(int contextCompressionTriggerMessages) {
        this.contextCompressionTriggerMessages = contextCompressionTriggerMessages;
    }

    /**
     * 获取压缩后需保留的最近原始消息数量。
     *
     * @return 正数配置值；非正数时返回默认值 {@code 8}
     */
    public int getContextCompressionRecentMessages() {
        return contextCompressionRecentMessages > 0 ? contextCompressionRecentMessages : 8;
    }

    /**
     * 设置压缩后需保留的最近原始消息数量。
     *
     * @param contextCompressionRecentMessages 需保留的最近原始消息数量；非正数由 getter 回退至默认值
     */
    public void setContextCompressionRecentMessages(int contextCompressionRecentMessages) {
        this.contextCompressionRecentMessages = contextCompressionRecentMessages;
    }

    /**
     * 获取归一化后的会话上下文压缩配置，确保触发阈值始终大于保留消息数。
     *
     * @return 可直接用于压缩流程的配置；冲突数值回退到默认阈值组合
     */
    public ContextCompressionSettings getEffectiveContextCompressionSettings() {
        int triggerMessages = getContextCompressionTriggerMessages();
        int recentMessages = getContextCompressionRecentMessages();
        int summaryMaxCharacters = getContextCompressionSummaryMaxCharacters();
        if (triggerMessages <= recentMessages) {
            return new ContextCompressionSettings(
                    contextCompressionEnabled, 20, 8, 4000, getContextCompressionMaxRetries());
        }
        return new ContextCompressionSettings(
                contextCompressionEnabled, triggerMessages, recentMessages, summaryMaxCharacters,
                getContextCompressionMaxRetries());
    }
    /**
     * 获取单次摘要允许保存的最大字符数。
     *
     * @return 正数配置值；非正数时返回默认值 {@code 4000}
     */
    public int getContextCompressionSummaryMaxCharacters() {
        return contextCompressionSummaryMaxCharacters > 0 ? contextCompressionSummaryMaxCharacters : 4000;
    }

    /**
     * 设置单次摘要允许保存的最大字符数。
     *
     * @param contextCompressionSummaryMaxCharacters 最大字符数；非正数由 getter 回退至默认值
     */
    public void setContextCompressionSummaryMaxCharacters(int contextCompressionSummaryMaxCharacters) {
        this.contextCompressionSummaryMaxCharacters = contextCompressionSummaryMaxCharacters;
    }

    /** 获取摘要失败重试次数。 */
    public int getContextCompressionMaxRetries() {
        return contextCompressionMaxRetries >= 0 ? contextCompressionMaxRetries : 0;
    }

    /** 设置摘要失败重试次数。 */
    public void setContextCompressionMaxRetries(int contextCompressionMaxRetries) {
        this.contextCompressionMaxRetries = contextCompressionMaxRetries;
    }

    /** 获取工具流水线执行失败最大重试次数。 */
    public int getToolPipelineMaxRetries() {
        return toolPipelineMaxRetries >= 0 ? toolPipelineMaxRetries : 0;
    }

    /** 设置工具流水线执行失败最大重试次数。 */
    public void setToolPipelineMaxRetries(int toolPipelineMaxRetries) {
        this.toolPipelineMaxRetries = toolPipelineMaxRetries;
    }

    /**
     * 获取 HarnessAgent 注册表最大缓存实例数。
     *
     * @return 正数配置值；非正数时返回默认值 {@code 128}
     */
    public int getHarnessRegistryCapacity() {
        return harnessRegistryCapacity > 0 ? harnessRegistryCapacity : 128;
    }

    /**
     * 设置 HarnessAgent 注册表最大缓存实例数。
     *
     * @param harnessRegistryCapacity 最大实例数；非正数由 getter 回退至默认值
     */
    public void setHarnessRegistryCapacity(int harnessRegistryCapacity) {
        this.harnessRegistryCapacity = harnessRegistryCapacity;
    }

    /**
     * 获取 HarnessAgent 工作区根目录。
     *
     * @return 非空工作区根目录；空配置回退至 {@code .agentscope/workspace}
     */
    public String getPluginDir() {
        return StrUtil.isBlank(pluginDir) ? "data/plugins" : pluginDir.trim();
    }

    public void setPluginDir(String pluginDir) {
        this.pluginDir = pluginDir;
    }

    /** 获取插件热重载开关。 */
    public boolean isPluginWatchEnabled() {
        return pluginWatchEnabled;
    }

    /** 设置插件热重载开关。 */
    public void setPluginWatchEnabled(boolean pluginWatchEnabled) {
        this.pluginWatchEnabled = pluginWatchEnabled;
    }

    /** 获取插件热重载轮询间隔（毫秒）；非正数回退默认 {@code 5000}。 */
    public long getPluginWatchIntervalMs() {
        return pluginWatchIntervalMs > 0 ? pluginWatchIntervalMs : 5000;
    }

    /** 设置插件热重载轮询间隔（毫秒）。 */
    public void setPluginWatchIntervalMs(long pluginWatchIntervalMs) {
        this.pluginWatchIntervalMs = pluginWatchIntervalMs;
    }

    public String getHarnessWorkspaceRoot() {
        if (StrUtil.isBlank(harnessWorkspaceRoot)) {
            return ".agentscope/workspace";
        }
        return harnessWorkspaceRoot.trim();
    }

    /**
     * 设置 HarnessAgent 工作区根目录。
     *
     * @param harnessWorkspaceRoot 工作区根目录；允许使用相对应用工作目录的路径
     */
    public void setHarnessWorkspaceRoot(String harnessWorkspaceRoot) {
        this.harnessWorkspaceRoot = harnessWorkspaceRoot;
    }

    /**
     * 获取参与实例指纹计算的工作区配置版本。
     *
     * @return 非空版本标识；空配置回退至 {@code v1}
     */
    public String getHarnessWorkspaceVersion() {
        if (StrUtil.isBlank(harnessWorkspaceVersion)) {
            return "v1";
        }
        return harnessWorkspaceVersion.trim();
    }

    /**
     * 设置工作区配置版本，版本变化会触发 HarnessAgent 重建。
     *
     * @param harnessWorkspaceVersion 工作区配置版本
     */
    public void setHarnessWorkspaceVersion(String harnessWorkspaceVersion) {
        this.harnessWorkspaceVersion = harnessWorkspaceVersion;
    }
    /**
     * 获取全局系统提示词。
     *
     * @return 非空系统提示词；空配置回退到内置提示词
     */
    public String getSystemPrompt() {
        if (systemPrompt == null || systemPrompt.trim().isEmpty()) {
            return DEFAULT_SYSTEM_PROMPT;
        }
        return systemPrompt;
    }

    /**
     * 设置全局系统提示词。
     *
     * @param systemPrompt 系统提示词；空值使用内置默认值
     */
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    /**
     * 判断是否启用长期记忆（两层记忆 + RocksDB 持久化）。
     *
     * @return {@code true} 表示启用
     */
    public boolean isMemoryEnabled() {
        return memoryEnabled;
    }

    /**
     * 设置是否启用长期记忆。
     *
     * @param memoryEnabled {@code true} 启用，{@code false} 关闭
     */
    public void setMemoryEnabled(boolean memoryEnabled) {
        this.memoryEnabled = memoryEnabled;
    }

    /**
     * 获取 per-call flush 节流间隔（秒）。
     *
     * @return 正数配置值；非正数回退至默认值 {@code 600}
     */
    public int getMemoryFlushTriggerSeconds() {
        return memoryFlushTriggerSeconds > 0 ? memoryFlushTriggerSeconds : 600;
    }

    /**
     * 设置 per-call flush 节流间隔（秒）。
     *
     * @param memoryFlushTriggerSeconds 秒数；非正数由 getter 回退至默认值
     */
    public void setMemoryFlushTriggerSeconds(int memoryFlushTriggerSeconds) {
        this.memoryFlushTriggerSeconds = memoryFlushTriggerSeconds;
    }

    /**
     * 获取后台 MEMORY.md 合并最小间隔（分钟）。
     *
     * @return 正数配置值；非正数回退至默认值 {@code 120}
     */
    public int getMemoryConsolidationMinGapMinutes() {
        return memoryConsolidationMinGapMinutes > 0 ? memoryConsolidationMinGapMinutes : 120;
    }

    /**
     * 设置后台 MEMORY.md 合并最小间隔（分钟）。
     *
     * @param memoryConsolidationMinGapMinutes 分钟数；非正数由 getter 回退至默认值
     */
    public void setMemoryConsolidationMinGapMinutes(int memoryConsolidationMinGapMinutes) {
        this.memoryConsolidationMinGapMinutes = memoryConsolidationMinGapMinutes;
    }

    /**
     * 获取 MEMORY.md token 上限。
     *
     * @return 正数配置值；非正数回退至默认值 {@code 4000}
     */
    public int getMemoryConsolidationMaxTokens() {
        return memoryConsolidationMaxTokens > 0 ? memoryConsolidationMaxTokens : 4000;
    }

    /**
     * 设置 MEMORY.md token 上限。
     *
     * @param memoryConsolidationMaxTokens token 数；非正数由 getter 回退至默认值
     */
    public void setMemoryConsolidationMaxTokens(int memoryConsolidationMaxTokens) {
        this.memoryConsolidationMaxTokens = memoryConsolidationMaxTokens;
    }

    /**
     * 获取日流水账归档天数。
     *
     * @return 正数配置值；非正数回退至默认值 {@code 90}
     */
    public int getMemoryDailyRetentionDays() {
        return memoryDailyRetentionDays > 0 ? memoryDailyRetentionDays : 90;
    }

    /**
     * 设置日流水账归档天数。
     *
     * @param memoryDailyRetentionDays 天数；非正数由 getter 回退至默认值
     */
    public void setMemoryDailyRetentionDays(int memoryDailyRetentionDays) {
        this.memoryDailyRetentionDays = memoryDailyRetentionDays;
    }

    /**
     * 获取会话日志保留天数。
     *
     * @return 正数配置值；非正数回退至默认值 {@code 180}
     */
    public int getMemorySessionRetentionDays() {
        return memorySessionRetentionDays > 0 ? memorySessionRetentionDays : 180;
    }

    /**
     * 设置会话日志保留天数。
     *
     * @param memorySessionRetentionDays 天数；非正数由 getter 回退至默认值
     */
    public void setMemorySessionRetentionDays(int memorySessionRetentionDays) {
        this.memorySessionRetentionDays = memorySessionRetentionDays;
    }

    /**
     * 获取用户长期记忆保留天数。
     *
     * @return 正数配置值；非正数回退至默认值 {@code 180}
     */
    public int getMemoryUserRetentionDays() {
        return memoryUserRetentionDays > 0 ? memoryUserRetentionDays : 180;
    }

    /**
     * 设置用户长期记忆保留天数。
     *
     * @param memoryUserRetentionDays 天数；非正数由 getter 回退至默认值
     */
    public void setMemoryUserRetentionDays(int memoryUserRetentionDays) {
        this.memoryUserRetentionDays = memoryUserRetentionDays;
    }

    /**
     * 获取用户长期记忆每类别最大条数。
     *
     * @return 正数配置值；非正数回退至默认值 {@code 200}
     */
    public int getMemoryMaxRecordsPerCategory() {
        return memoryMaxRecordsPerCategory > 0 ? memoryMaxRecordsPerCategory : 200;
    }

    /**
     * 设置用户长期记忆每类别最大条数。
     *
     * @param memoryMaxRecordsPerCategory 条数；非正数由 getter 回退至默认值
     */
    public void setMemoryMaxRecordsPerCategory(int memoryMaxRecordsPerCategory) {
        this.memoryMaxRecordsPerCategory = memoryMaxRecordsPerCategory;
    }

    /**
     * 获取长期记忆白名单类别列表；为空表示允许所有类别。
     *
     * @return 白名单类别列表
     */
    public List<String> getMemoryWhitelistCategories() {
        if (StrUtil.isBlank(memoryWhitelistCategories)) {
            return List.of();
        }
        return java.util.Arrays.stream(memoryWhitelistCategories.split(","))
                .map(String::trim)
                .filter(category -> !category.isEmpty())
                .distinct()
                .toList();
    }

    /**
     * 设置长期记忆白名单类别（逗号分隔）。
     *
     * @param memoryWhitelistCategories 类别列表；为空表示允许所有类别
     */
    public void setMemoryWhitelistCategories(String memoryWhitelistCategories) {
        this.memoryWhitelistCategories = memoryWhitelistCategories;
    }

    /**
     * 获取敏感内容过滤开关。
     *
     * @return {@code true} 表示写入记忆前自动脱敏
     */
    public boolean isMemorySensitiveFiltering() {
        return memorySensitiveFiltering;
    }

    /**
     * 设置敏感内容过滤开关。
     *
     * @param memorySensitiveFiltering {@code true} 开启
     */
    public void setMemorySensitiveFiltering(boolean memorySensitiveFiltering) {
        this.memorySensitiveFiltering = memorySensitiveFiltering;
    }

    /** 获取互操作规则文件启用开关。 */
    public boolean isInteropInstructionFilesEnabled() {
        return interopInstructionFilesEnabled;
    }

    /** 设置互操作规则文件启用开关。 */
    public void setInteropInstructionFilesEnabled(boolean interopInstructionFilesEnabled) {
        this.interopInstructionFilesEnabled = interopInstructionFilesEnabled;
    }

    /** 获取互操作规则文件名列表（按优先级顺序）。 */
    public List<String> getInteropInstructionFiles() {
        if (StrUtil.isBlank(interopInstructionFiles)) {
            return List.of("AGENTS.md", "CLAUDE.md");
        }
        return java.util.Arrays.stream(interopInstructionFiles.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();
    }

    /** 设置互操作规则文件名列表（逗号分隔）。 */
    public void setInteropInstructionFiles(String interopInstructionFiles) {
        this.interopInstructionFiles = interopInstructionFiles;
    }

    /** 获取互操作规则文件最大注入字符数。 */
    public int getInteropInstructionMaxChars() {
        return interopInstructionMaxChars > 0 ? interopInstructionMaxChars : 8000;
    }

    /** 设置互操作规则文件最大注入字符数。 */
    public void setInteropInstructionMaxChars(int interopInstructionMaxChars) {
        this.interopInstructionMaxChars = interopInstructionMaxChars;
    }

    /** 获取互操作规则文件是否通过专用内存技能提供。 */
    public boolean isInteropInstructionSkill() {
        return interopInstructionSkill;
    }

    /** 设置互操作规则文件是否通过专用内存技能提供。 */
    public void setInteropInstructionSkill(boolean interopInstructionSkill) {
        this.interopInstructionSkill = interopInstructionSkill;
    }

    /**
     * 获取归一化后的长期记忆配置快照。
     *
     * @return 可直接用于 MemoryConfig 构建的配置
     */
    public MemorySettings getEffectiveMemorySettings() {
        return new MemorySettings(
                memoryEnabled,
                getMemoryFlushTriggerSeconds(),
                getMemoryConsolidationMinGapMinutes(),
                getMemoryConsolidationMaxTokens(),
                getMemoryDailyRetentionDays(),
                getMemorySessionRetentionDays());
    }

    /**
     * 长期记忆的有效配置快照。
     *
     * @param enabled 是否启用长期记忆
     * @param flushTriggerSeconds per-call flush 节流间隔（秒）
     * @param consolidationMinGapMinutes 后台合并最小间隔（分钟）
     * @param consolidationMaxTokens MEMORY.md token 上限
     * @param dailyRetentionDays 日流水账归档天数
     * @param sessionRetentionDays 会话日志保留天数
     */
    public record MemorySettings(
            boolean enabled,
            int flushTriggerSeconds,
            int consolidationMinGapMinutes,
            int consolidationMaxTokens,
            int dailyRetentionDays,
            int sessionRetentionDays) {
    }

    /**
     * 会话上下文压缩的有效配置快照，供单次对话流程读取。
     *
     * @param enabled 是否启用压缩
     * @param triggerMessages 触发压缩的原始消息数，始终大于 recentMessages
     * @param recentMessages 压缩后保留的最近原始消息数，始终为正数
     * @param summaryMaxCharacters 单条摘要最大字符数，始终为正数
     */
    public record ContextCompressionSettings(
            boolean enabled,
            int triggerMessages,
            int recentMessages,
            int summaryMaxCharacters,
            int maxRetries) {
    }
}