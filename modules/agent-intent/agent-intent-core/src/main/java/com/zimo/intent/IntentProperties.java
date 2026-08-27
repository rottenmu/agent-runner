package com.zimo.intent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 意图识别引擎配置（prefix = plugin.ai.intent）。
 *
 * <p>支持：总开关、规则文件位置（classpath:/file: 前缀）、置信度参数、
 * 判定跳过意图、导出强信号词、实体词表（槽位 → 词表）与槽位适用意图映射。
 * 实体词表为空时使用引擎内置默认词表。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-14
 */
@ConfigurationProperties(prefix = "plugin.intent")
public class IntentProperties {

    /** 总开关 */
    private boolean enabled = true;

    /** 规则文件位置：classpath:ai-intent/intent-rules.json 或 file:/path/rules.json */
    private String rulesLocation = "classpath:ai-intent/intent-rules.json";

    /** 命中 1 个触发词的基准置信度 */
    private double baseConfidence = 0.8;

    /** 每多命中一个触发词的置信度步进 */
    private double stepConfidence = 0.06;

    /** 命中必填槽位实体的置信度加成 */
    private double entityConfidence = 0.06;

    /** 置信度封顶 */
    private double maxConfidence = 0.99;

    /** 规则未配置置信度阈值时的默认值 */
    private double defaultConfidence = 0.8;

    /** 业务意图匹配时跳过的兜底意图编码 */
    private List<String> skipIntents = List.of(
            "PARAM_CLARIFY", "GENERAL_CHAT", "UNSUPPORTED", "RISK_REJECT");

    /** 导出强信号意图编码（命中强信号词即优先判定该意图） */
    private String exportIntentCode = "DATA_EXPORT";

    /** 导出强信号词（命中任一即优先导出意图） */
    private List<String> exportStrongSignal = List.of(
            "导出", "下载", "发我份", "导出来", "拉个表", "保存表格", "生成报表");

    /** 槽位适用意图映射：槽位名 → 适用意图编码列表 */
    private Map<String, List<String>> entityIntents = defaultEntityIntents();

    /** 实体词表：槽位名(小写) → 词表（逗号分隔，支持 词=值 映射）；为空使用内置默认词表 */
    private Map<String, String> entityWords = new HashMap<>();

    /** 空输入时归类意图（默认闲聊） */
    private String blankIntentCode = "GENERAL_CHAT";

    /** 模糊识别下限（含）：置信度落在 [ambiguousLow, high) 判定为模糊识别 */
    private double ambiguousLow = 0.5;

    /** 模糊识别上限（不含）：等于默认阈值 0.8 */
    private double ambiguousHigh = 0.8;

    /** 是否启用 LLM 精准识别层（混合架构第二阶段；false 时仅规则引擎） */
    private boolean llmEnabled = false;

    /** LLM 解析器实现类全限定名（空则使用内置启发式模拟实现） */
    private String llmParserClass = "";

    /** LLM 系统提示词模板（含意图枚举/槽位 Schema/判定规则，{rules} 注入规则 JSON） */
    private String llmPrompt = "";

    /** 上下文消解：最多回溯的历史消息条数 */
    private int historyWindow = 8;

    /** 指代词表（当前输入为这些词时用历史实体补全） */
    private List<String> referenceWords = List.of("它", "它呢", "这个", "这个呢", "那个", "那个呢",
            "上面", "刚才", "刚说", "上一条", "这些", "那些", "它俩");

    /** 单据号抽取正则（默认 6 位以上纯数字）；置空表示不抽取 orderId 槽位 */
    private String orderIdPattern = "\\d{6,}";

    /** 渠道链路拦截子配置（plugin.intent.channel.*） */
    private Channel channel = new Channel();

    /** 必填槽位豁免名单：名单内槽位缺失不触发追问（默认时间范围按缺省值查询） */
    private List<String> exemptSlots = List.of("timeRange");

    /** 风险拒绝结果的固定置信度 */
    private double riskConfidence = 0.99;

    /** 超出能力范围结果的固定置信度 */
    private double unsupportedConfidence = 0.7;

    /** 通用闲聊结果的固定置信度 */
    private double chatConfidence = 0.6;

    /** 参数澄清追问结果的固定置信度 */
    private double clarifyConfidence = 0.85;

    /** LLM HTTP 对话地址（OpenAI 兼容，默认 /v1/chat/completions） */
    private String llmBaseUrl = "";

    /** LLM API Key */
    private String llmApiKey = "";

    /** LLM 模型名称（默认 qwen-plus） */
    private String llmModel = "qwen-plus";

    /** LLM 调用超时（毫秒） */
    private int llmTimeoutMs = 30000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getAmbiguousLow() {
        return ambiguousLow;
    }

    public void setAmbiguousLow(double ambiguousLow) {
        this.ambiguousLow = ambiguousLow;
    }

    public double getAmbiguousHigh() {
        return ambiguousHigh;
    }

    public void setAmbiguousHigh(double ambiguousHigh) {
        this.ambiguousHigh = ambiguousHigh;
    }

    public boolean isLlmEnabled() {
        return llmEnabled;
    }

    public void setLlmEnabled(boolean llmEnabled) {
        this.llmEnabled = llmEnabled;
    }

    public String getLlmParserClass() {
        return llmParserClass;
    }

    public void setLlmParserClass(String llmParserClass) {
        this.llmParserClass = llmParserClass;
    }

    public String getLlmPrompt() {
        return llmPrompt;
    }

    public void setLlmPrompt(String llmPrompt) {
        this.llmPrompt = llmPrompt;
    }

    public int getHistoryWindow() {
        return historyWindow;
    }

    public void setHistoryWindow(int historyWindow) {
        this.historyWindow = historyWindow;
    }

    public List<String> getReferenceWords() {
        return referenceWords == null ? List.of() : referenceWords;
    }

    public void setReferenceWords(List<String> referenceWords) {
        this.referenceWords = referenceWords;
    }

    public String getLlmBaseUrl() {
        return llmBaseUrl;
    }

    public void setLlmBaseUrl(String llmBaseUrl) {
        this.llmBaseUrl = llmBaseUrl;
    }

    public String getLlmApiKey() {
        return llmApiKey;
    }

    public void setLlmApiKey(String llmApiKey) {
        this.llmApiKey = llmApiKey;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public int getLlmTimeoutMs() {
        return llmTimeoutMs;
    }

    public void setLlmTimeoutMs(int llmTimeoutMs) {
        this.llmTimeoutMs = llmTimeoutMs;
    }

    public String getRulesLocation() {
        return rulesLocation;
    }

    public void setRulesLocation(String rulesLocation) {
        this.rulesLocation = rulesLocation;
    }

    public double getBaseConfidence() {
        return baseConfidence;
    }

    public void setBaseConfidence(double baseConfidence) {
        this.baseConfidence = baseConfidence;
    }

    public double getStepConfidence() {
        return stepConfidence;
    }

    public void setStepConfidence(double stepConfidence) {
        this.stepConfidence = stepConfidence;
    }

    public double getEntityConfidence() {
        return entityConfidence;
    }

    public void setEntityConfidence(double entityConfidence) {
        this.entityConfidence = entityConfidence;
    }

    public double getMaxConfidence() {
        return maxConfidence;
    }

    public void setMaxConfidence(double maxConfidence) {
        this.maxConfidence = maxConfidence;
    }

    public double getDefaultConfidence() {
        return defaultConfidence;
    }

    public void setDefaultConfidence(double defaultConfidence) {
        this.defaultConfidence = defaultConfidence;
    }

    public List<String> getSkipIntents() {
        return skipIntents == null ? new ArrayList<>() : skipIntents;
    }

    public void setSkipIntents(List<String> skipIntents) {
        this.skipIntents = skipIntents;
    }

    public String getExportIntentCode() {
        return exportIntentCode;
    }

    public void setExportIntentCode(String exportIntentCode) {
        this.exportIntentCode = exportIntentCode;
    }

    public List<String> getExportStrongSignal() {
        return exportStrongSignal == null ? new ArrayList<>() : exportStrongSignal;
    }

    public void setExportStrongSignal(List<String> exportStrongSignal) {
        this.exportStrongSignal = exportStrongSignal;
    }

    public Map<String, List<String>> getEntityIntents() {
        return entityIntents == null ? defaultEntityIntents() : entityIntents;
    }

    public void setEntityIntents(Map<String, List<String>> entityIntents) {
        this.entityIntents = entityIntents;
    }

    public Map<String, String> getEntityWords() {
        return entityWords == null ? new HashMap<>() : entityWords;
    }

    public void setEntityWords(Map<String, String> entityWords) {
        this.entityWords = entityWords;
    }

    public String getBlankIntentCode() {
        return blankIntentCode;
    }

    public void setBlankIntentCode(String blankIntentCode) {
        this.blankIntentCode = blankIntentCode;
    }

    public Channel getChannel() {
        return channel == null ? new Channel() : channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getOrderIdPattern() {
        return orderIdPattern;
    }

    public void setOrderIdPattern(String orderIdPattern) {
        this.orderIdPattern = orderIdPattern;
    }

    public List<String> getExemptSlots() {
        return exemptSlots == null ? List.of() : exemptSlots;
    }

    public void setExemptSlots(List<String> exemptSlots) {
        this.exemptSlots = exemptSlots;
    }

    public double getRiskConfidence() {
        return riskConfidence;
    }

    public void setRiskConfidence(double riskConfidence) {
        this.riskConfidence = riskConfidence;
    }

    public double getUnsupportedConfidence() {
        return unsupportedConfidence;
    }

    public void setUnsupportedConfidence(double unsupportedConfidence) {
        this.unsupportedConfidence = unsupportedConfidence;
    }

    public double getChatConfidence() {
        return chatConfidence;
    }

    public void setChatConfidence(double chatConfidence) {
        this.chatConfidence = chatConfidence;
    }

    public double getClarifyConfidence() {
        return clarifyConfidence;
    }

    public void setClarifyConfidence(double clarifyConfidence) {
        this.clarifyConfidence = clarifyConfidence;
    }

    /** 默认槽位适用意图映射 */
    private static Map<String, List<String>> defaultEntityIntents() {
        Map<String, List<String>> map = new HashMap<>();
        map.put("timeRange", List.of("DATA_QUERY", "DATA_EXPORT"));
        map.put("dataType", List.of("DATA_QUERY", "DATA_EXPORT"));
        map.put("exportFormat", List.of("DATA_EXPORT"));
        map.put("orderType", List.of("ORDER_OPERATE"));
        map.put("operationType", List.of("ORDER_OPERATE"));
        map.put("orderId", List.of("ORDER_OPERATE"));
        map.put("topic", List.of("FAQ_ANSWER"));
        return map;
    }

    /**
     * 渠道链路拦截子配置（plugin.intent.channel.*）：
     * 支持在综合意图识别之外额外配置渠道级词表与拦截规则。
     */
    public static class Channel {
        /** 渠道级拦截总开关 */
        private boolean enabled = true;

        /** 渠道级额外风险词（命中即拒绝） */
        private List<String> extraRiskWords = new java.util.ArrayList<>();

        /** 渠道级追问配置（栏位名 -> 追问话术模板） */
        private Map<String, String> clarifyTemplates = new HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getExtraRiskWords() {
            return extraRiskWords == null ? new java.util.ArrayList<>() : extraRiskWords;
        }

        public void setExtraRiskWords(List<String> extraRiskWords) {
            this.extraRiskWords = extraRiskWords;
        }

        public Map<String, String> getClarifyTemplates() {
            return clarifyTemplates == null ? new HashMap<>() : clarifyTemplates;
        }

        public void setClarifyTemplates(Map<String, String> clarifyTemplates) {
            this.clarifyTemplates = clarifyTemplates;
        }
    }
}


