package com.zimo.intent.service;

import com.zimo.intent.IntentProperties;
import com.zimo.intent.model.IntentLlmResult;
import com.zimo.intent.model.IntentParseResult;
import com.zimo.intent.model.IntentRule;
import com.zimo.intent.parser.HeuristicIntentLlmParser;
import com.zimo.intent.parser.IntentLlmParser;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.common.intent.IntentConfidence;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 意图识别规则引擎（可配置化，混合架构编排层）。
 *
 * <p>职责：「前置规则过滤 + LLM 精准识别 + 置信度路由 + 实体校验 + 缺参追问」
 * 主流程编排，以及三种调用模式的入口：模式一（业务场景 → 规则库生成）、
 * 模式二（实时意图解析）、模式三（规则校验优化）。规则存储、实体抽取、
 * 批量评估、规则校验分别委托 {@link IntentRuleStore}、{@link IntentEntityExtractor}、
 * {@link IntentEvaluator}、{@link IntentRuleReviewer}。</p>
 *
 * <p>判定顺序遵循规则文档：RISK_REJECT 硬拦截 → 业务意图规则匹配 →
 * 规则置信度不足时 LLM 精准解析 → 上下文指代消解 → 三级置信度路由
 * （≥阈值生效 / [ambiguousLow, 阈值) 二次确认 / &lt;ambiguousLow 闲聊或不支持）
 * → 缺参追问。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-14
 */
public class IntentRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(IntentRecognitionService.class);

    private static final String RISK_REJECT = "RISK_REJECT";
    private static final String GENERAL_CHAT = "GENERAL_CHAT";
    private static final String TOOL_CALL_WITH_CONFIRM = "TOOL_CALL_WITH_CONFIRM";

    private final ObjectMapper objectMapper;
    private final IntentProperties properties;
    private final IntentLlmParser llmParser;
    private final IntentRuleStore store;
    private final IntentEntityExtractor extractor;
    private final IntentEvaluator evaluator;
    private final IntentRuleReviewer reviewer;

    /* ---------------- 路由统计（可观测） ---------------- */
    private final java.util.concurrent.atomic.AtomicLong parseCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong rejectCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong clarifyCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong confirmCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong llmCallCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong> intentCounts
            = new java.util.concurrent.ConcurrentHashMap<>();

    /** 兼容构造：无配置时使用默认参数。 */
    public IntentRecognitionService(ObjectMapper objectMapper) {
        this(objectMapper, new IntentProperties(), null);
    }

    public IntentRecognitionService(ObjectMapper objectMapper, IntentProperties properties) {
        this(objectMapper, properties, null);
    }

    /**
     * 完整构造：可注入自定义 LLM 解析器。
     *
     * @param llmParser LLM 解析器；为 null 时按 properties.llmParserClass 反射加载，
     *                  失败则使用内置 {@link HeuristicIntentLlmParser}
     */
    public IntentRecognitionService(ObjectMapper objectMapper, IntentProperties properties, IntentLlmParser llmParser) {
        this.objectMapper = objectMapper;
        this.properties = properties == null ? new IntentProperties() : properties;
        this.store = new IntentRuleStore(objectMapper, this.properties);
        this.extractor = new IntentEntityExtractor(this.properties);
        this.evaluator = new IntentEvaluator(this::parse, objectMapper);
        this.reviewer = new IntentRuleReviewer(store, objectMapper);
        this.llmParser = llmParser != null ? llmParser : resolveLlmParser();
    }

    /**
     * 模式二：对用户输入执行意图解析（规则+LLM 混合架构）。
     *
     * <p>流程：风险硬拦截 → 规则引擎识别 → 置信度不足时 LLM 精准解析（含上下文指代消解）
     * → 三级置信度路由 → 实体缺参追问。LLM 返回风险拒绝时直接拦截；LLM 追问话术
     * 在缺参时优先采用。context 支持 history（历史消息数组）与 entities（历史已确认实体）。</p>
     *
     * @param query   用户原始输入
     * @param context 对话上下文：{"history": "['用户:...','助手:...']", "entities": "{\"dataType\":\"订单\"}"}
     * @return 标准解析结果
     */
    public IntentParseResult parse(String query, Map<String, String> context) {
        parseCount.incrementAndGet();
        String text = query == null ? "" : query.trim();
        if (StrUtil.isBlank(text)) {
            return blankResult(text);
        }
        ParseState state = newState(text, context);
        IntentRule risk = store.get(RISK_REJECT);
        if (risk != null && extractor.hitAny(risk, state.normalized)) {
            rejectCount.incrementAndGet();
            return IntentParseResult.reject(text, "指令含违规、越权或敏感内容，已拒绝执行",
                    properties.getRiskConfidence());
        }
        matchByRules(state);
        IntentParseResult llmReject = refineByLlm(state);
        if (llmReject != null) {
            return llmReject;
        }
        resolveContext(state, context);
        recordStats(state);
        return routeDecision(state);
    }

    /* ---------------- 解析主流程子步骤 ---------------- */

    /** 空输入归类：默认闲聊（置信度取配置 chatConfidence），可配置为不支持。 */
    private IntentParseResult blankResult(String text) {
        return GENERAL_CHAT.equals(properties.getBlankIntentCode())
                ? IntentParseResult.chat(text, properties.getChatConfidence())
                : IntentParseResult.fallback(text, "输入为空", properties.getUnsupportedConfidence());
    }

    /** 初始化解析状态：上下文历史与历史实体用于后续指代消解。 */
    private ParseState newState(String text, Map<String, String> context) {
        ParseState state = new ParseState();
        state.text = text;
        state.normalized = text.toLowerCase();
        state.history = parseHistory(context);
        state.historyEntities = parseHistoryEntities(context, state.history);
        state.reference = isReferenceQuery(text);
        return state;
    }

    /** 规则引擎业务意图匹配（跳过兜底意图）；平票取后，导出强信号优先。 */
    private void matchByRules(ParseState state) {
        IntentRule best = null;
        int bestHits = 0;
        for (IntentRule rule : store.listRules()) {
            if (properties.getSkipIntents().contains(rule.getIntentCode())) {
                continue;
            }
            int hits = extractor.hitCount(rule, state.normalized);
            if (hits >= bestHits) {
                bestHits = hits;
                best = rule;
            }
        }
        best = applyExportStrongSignal(best, state.normalized);
        if (best == null || extractor.hitCount(best, state.normalized) == 0) {
            return;
        }
        state.entities = extractor.extractEntities(best, state.text);
        int entityHits = entitySlotHits(best, state.entities);
        state.confidence = roundConfidence(confidence(extractor.hitCount(best, state.normalized))
                + entityHits * properties.getEntityConfidence());
        applyRuleToState(state, best);
    }

    /** 导出强信号优先：非导出意图命中强信号词时切换为导出规则。 */
    private IntentRule applyExportStrongSignal(IntentRule best, String normalized) {
        String exportCode = properties.getExportIntentCode();
        if (best != null && !exportCode.equals(best.getIntentCode()) && extractor.hitStrongSignal(normalized)) {
            IntentRule exportRule = store.get(exportCode);
            if (exportRule != null) {
                return exportRule;
            }
        }
        return best;
    }

    /** 规则置信度不足或未命中时启用 LLM 精准解析；LLM 风险拒绝时返回拦截结果。 */
    private IntentParseResult refineByLlm(ParseState state) {
        if (!properties.isLlmEnabled()
                || (state.intentCode != null && state.confidence >= state.threshold)) {
            return null;
        }
        IntentParseResult ruleHint = state.intentCode == null ? null
                : IntentParseResult.business(state.text, state.intentCode, state.intentName, state.confidence,
                        state.entities, List.of(), state.tools, false, "", state.route);
        IntentLlmResult llm = llmParser.parse(state.text, state.history, ruleHint, store.rulesJson());
        if (llm == null) {
            return null;
        }
        state.llmUsed = true;
        if (llm.isReject()) {
            rejectCount.incrementAndGet();
            String reason = StrUtil.blankToDefault(llm.rejectReason(), "LLM 判定含违规、越权或敏感内容");
            return IntentParseResult.reject(state.text, reason, properties.getRiskConfidence());
        }
        mergeLlm(state, llm);
        return null;
    }

    /** 合并 LLM 结论：意图编码必须在规则库中注册，实体按 Schema 白名单过滤。 */
    private void mergeLlm(ParseState state, IntentLlmResult llm) {
        IntentRule llmRule = store.get(llm.intentCode());
        if (llmRule == null) {
            log.debug("LLM 返回未知意图编码: {}", llm.intentCode());
            return;
        }
        applyRuleToState(state, llmRule);
        if (StrUtil.isNotBlank(llm.intentName())) {
            state.intentName = llm.intentName();
        }
        state.confidence = llm.confidence();
        state.entities = extractor.filterBySchema(llm.entities());
        state.llmClarifyPrompt = llm.clarifyPrompt();
    }

    /** 上下文指代消解：历史实体补全缺失槽位，指代词继承上一轮意图。 */
    private void resolveContext(ParseState state, Map<String, String> context) {
        IntentRule currentRule = store.get(state.intentCode);
        boolean slotIncomplete = !extractor.missingSlots(currentRule, state.entities).isEmpty();
        if ((state.reference || state.entities.isEmpty() || slotIncomplete) && !state.historyEntities.isEmpty()) {
            Map<String, String> merged = new LinkedHashMap<>(state.historyEntities);
            merged.putAll(state.entities);
            state.entities = merged;
            state.historyUsed = true;
        }
        if (state.reference && state.intentCode == null && context != null
                && StrUtil.isNotBlank(context.get("intentCode"))) {
            IntentRule inherited = store.get(context.get("intentCode"));
            if (inherited != null) {
                applyRuleToState(state, inherited);
                state.confidence = properties.getDefaultConfidence();
                state.historyUsed = true;
            }
        }
    }

    /** 统计：命中意图分布与 LLM 调用量。 */
    private void recordStats(ParseState state) {
        if (state.intentCode != null) {
            intentCounts.computeIfAbsent(state.intentCode, k -> new java.util.concurrent.atomic.AtomicLong())
                    .incrementAndGet();
        }
        if (state.llmUsed) {
            llmCallCount.incrementAndGet();
        }
    }

    /** 三级置信度路由：低置信闲聊/兜底 → 缺参追问 → 模糊二次确认 → 正常/写操作确认。 */
    private IntentParseResult routeDecision(ParseState state) {
        if (state.intentCode == null || state.confidence < properties.getAmbiguousLow()) {
            IntentRule chat = store.get(GENERAL_CHAT);
            if (chat != null && extractor.hitAny(chat, state.normalized)) {
                return IntentParseResult.chat(state.text, properties.getChatConfidence());
            }
            return IntentParseResult.fallback(state.text,
                    "未识别到明确业务意图，置信度过低（" + String.format("%.2f", state.confidence) + "）",
                    properties.getUnsupportedConfidence());
        }
        if (state.confidence < state.threshold) {
            state.ambiguous = true;
        }
        List<String> missing = extractor.missingSlots(store.get(state.intentCode), state.entities);
        if (!missing.isEmpty()) {
            clarifyCount.incrementAndGet();
            String prompt = StrUtil.blankToDefault(state.llmClarifyPrompt, defaultClarifyPrompt(missing));
            return IntentParseResult.clarify(state.text, state.intentCode, state.intentName, missing, prompt,
                    properties.getClarifyConfidence());
        }
        return businessDecision(state);
    }

    /** 正常路由决策：模糊档输出二次确认，写操作确认路由输出执行前确认提示。 */
    private IntentParseResult businessDecision(ParseState state) {
        if (state.ambiguous) {
            String confirmPrompt = "识别为【" + state.intentName + "】（置信度 "
                    + String.format("%.2f", state.confidence) + "），请确认是否为您的意图？";
            return IntentParseResult.business(state.text, state.intentCode, state.intentName, state.confidence,
                    state.entities, List.of(), state.tools, true, confirmPrompt, state.route, true,
                    state.historyUsed);
        }
        boolean needClarify = TOOL_CALL_WITH_CONFIRM.equals(state.route);
        if (needClarify) {
            confirmCount.incrementAndGet();
        }
        String clarifyPrompt = needClarify ? "即将执行【" + state.intentName + "】操作，请确认后继续" : "";
        return IntentParseResult.business(state.text, state.intentCode, state.intentName, state.confidence,
                state.entities, List.of(), state.tools, needClarify, clarifyPrompt, state.route, false,
                state.historyUsed);
    }

    /* ---------------- 模式一 / 模式三 ---------------- */

    /**
     * 模式一（启发式）：根据业务场景描述生成单条意图规则草稿。
     *
     * @param scenario 业务场景描述，不允许为空
     * @return 可直接入库或人工编辑后入库的规则草稿
     * @throws IllegalArgumentException 场景描述为空
     */
    public IntentRule generateRuleFromScenario(String scenario) {
        if (StrUtil.isBlank(scenario)) {
            throw new IllegalArgumentException("业务场景描述不能为空");
        }
        List<String> keywords = collectScenarioKeywords(scenario);
        String code = "SCENARIO_" + Math.abs(scenario.hashCode() % 9000 + 1000);
        IntentRule rule = new IntentRule();
        rule.setIntentCode(code);
        rule.setIntentName("业务场景规则");
        rule.setIntentDesc(scenario.trim());
        rule.setTriggerKeywords(keywords);
        rule.setRequiredSlots(List.of(Map.of("name", "dataType", "label", "查询对象", "desc", "业务场景查询的对象")));
        rule.setOptionalSlots(List.of(Map.of("name", "timeRange", "label", "时间范围", "desc", "默认近7天")));
        rule.setSupportTool(List.of("report_query"));
        rule.setConfidenceThreshold(0.8);
        rule.setRouteStrategy("TOOL_CALL");
        rule.setRejectRule("超出该业务场景范围的操作一律拒绝");
        return rule;
    }

    /**
     * 模式一（完整规则库）：根据业务场景描述生成可入库的意图规则列表。
     *
     * <p>llmEnabled 且 LLM 可用时调用大模型生成完整规则库并归一化字段；
     * LLM 关闭或调用失败时降级为启发式单条草稿。</p>
     *
     * @param scenario 业务场景描述，不允许为空
     * @return 生成的规则列表（至少 1 条）
     * @throws IllegalArgumentException 场景描述为空
     */
    public List<IntentRule> generateRulesFromScenario(String scenario) {
        if (StrUtil.isBlank(scenario)) {
            throw new IllegalArgumentException("业务场景描述不能为空");
        }
        if (properties.isLlmEnabled()) {
            String json = llmParser.generateRules(scenario, store.rulesJson());
            List<IntentRule> generated = store.parseRulesFromJson(json);
            if (!generated.isEmpty()) {
                log.info("LLM 规则库生成完成: {} 条", generated.size());
                return normalizeGenerated(generated);
            }
            log.warn("LLM 规则生成失败或为空，降级启发式草稿");
        }
        return List.of(generateRuleFromScenario(scenario));
    }

    /**
     * 模式三：对规则集执行冲突校验、补全与优化迭代。
     *
     * @param rules 待校验规则列表；为空时校验当前已加载规则
     * @return 校验报告：rulesChecked/issues/suggestions/optimizedRules/llmUsed
     */
    public Map<String, Object> reviewRules(List<IntentRule> rules) {
        return reviewer.review(rules, llmParser, properties.isLlmEnabled());
    }

    /** LLM 生成规则归一化：补齐阈值/路由默认值，剔除编码为空的无效项。 */
    private List<IntentRule> normalizeGenerated(List<IntentRule> generated) {
        List<IntentRule> result = new ArrayList<>();
        for (IntentRule rule : generated) {
            if (rule == null || StrUtil.isBlank(rule.getIntentCode())) {
                continue;
            }
            if (rule.getConfidenceThreshold() <= 0) {
                rule.setConfidenceThreshold(properties.getDefaultConfidence());
            }
            if (StrUtil.isBlank(rule.getRouteStrategy())) {
                rule.setRouteStrategy("TOOL_CALL");
            }
            result.add(rule);
        }
        return result;
    }

    /** 场景关键词收集：切分场景短语 + 业务实体词表扫描。 */
    private List<String> collectScenarioKeywords(String scenario) {
        List<String> keywords = new ArrayList<>();
        for (String part : scenario.split("[,，;；。\s]+")) {
            String phrase = part.trim();
            if (StrUtil.isNotBlank(phrase) && phrase.length() >= 2 && phrase.length() <= 8
                    && !keywords.contains(phrase)) {
                keywords.add(phrase);
            }
        }
        for (String words : properties.getEntityWords().values()) {
            if (StrUtil.isBlank(words)) {
                continue;
            }
            for (String item : words.split(",")) {
                String word = item.contains("=") ? item.substring(0, item.indexOf('=')).trim() : item.trim();
                if (StrUtil.isNotBlank(word) && scenario.contains(word) && !keywords.contains(word)) {
                    keywords.add(word);
                }
            }
        }
        if (keywords.isEmpty()) {
            keywords = List.of(scenario.trim());
        }
        return keywords;
    }

    /* ---------------- 批量评估与统计 ---------------- */

    /**
     * 批量评估：输入 [{query, expected, expectedEntities}] 用例集，输出每例结果与整体指标
     * （意图准确率 / 缺参召回率 / 风险拦截率 / 实体抽取 F1）。
     */
    public Map<String, Object> evaluate(List<Map<String, String>> cases) {
        return evaluator.evaluate(cases);
    }

    /** 路由统计快照：解析量 / 意图分布 / 拦截次数 / LLM 调用量。 */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("parseCount", parseCount.get());
        stats.put("rejectCount", rejectCount.get());
        stats.put("clarifyCount", clarifyCount.get());
        stats.put("confirmCount", confirmCount.get());
        stats.put("llmCallCount", llmCallCount.get());
        Map<String, Long> distribution = new LinkedHashMap<>();
        intentCounts.forEach((code, count) -> distribution.put(code, count.get()));
        stats.put("intentDistribution", distribution);
        stats.put("rulesLoaded", store.size());
        return stats;
    }

    /** 重置路由统计。 */
    public void resetStats() {
        parseCount.set(0);
        rejectCount.set(0);
        clarifyCount.set(0);
        confirmCount.set(0);
        llmCallCount.set(0);
        intentCounts.clear();
    }

    /* ---------------- 规则管理（委托 IntentRuleStore） ---------------- */

    /** 重新加载规则集（支持规则热更新）。 */
    public void reload() {
        store.reload();
    }

    public List<IntentRule> listRules() {
        return store.listRules();
    }

    /** 新增意图规则（编码全局唯一 + 触发词冲突校验）。 */
    public IntentRule addRule(IntentRule rule) {
        return store.addRule(rule);
    }

    /** 编辑意图规则（按编码定位，编码不可变更）。 */
    public IntentRule updateRule(String code, IntentRule rule) {
        return store.updateRule(code, rule);
    }

    /** 删除意图规则；规则不存在返回 false。 */
    public boolean deleteRule(String code) {
        return store.deleteRule(code);
    }

    /** 暴露当前配置快照（供调试/管理端使用）。 */
    public IntentProperties properties() {
        return properties;
    }

    /* ---------------- 内部辅助 ---------------- */

    private IntentLlmParser resolveLlmParser() {
        String clazz = properties.getLlmParserClass();
        if (StrUtil.isNotBlank(clazz)) {
            try {
                Object instance = Class.forName(clazz).getDeclaredConstructor().newInstance();
                if (instance instanceof IntentLlmParser parser) {
                    log.info("LLM 解析器加载: {}", clazz);
                    return parser;
                }
            } catch (Exception e) {
                log.warn("LLM 解析器加载失败({})，回退启发式实现", clazz, e);
            }
        }
        return new HeuristicIntentLlmParser();
    }

    /** 从 context 解析历史消息列表（JSON 数组字符串）。 */
    private List<String> parseHistory(Map<String, String> context) {
        if (context == null) {
            return List.of();
        }
        String raw = context.get("history");
        if (StrUtil.isBlank(raw)) {
            return List.of();
        }
        try {
            List<String> history = objectMapper.readValue(raw, new TypeReference<List<String>>() {
            });
            int window = Math.max(1, properties.getHistoryWindow());
            return history.size() > window ? history.subList(history.size() - window, history.size()) : history;
        } catch (Exception e) {
            log.debug("历史消息解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 从 context 直接读取历史实体；缺失时从历史消息中反向抽取最近实体。 */
    private Map<String, String> parseHistoryEntities(Map<String, String> context, List<String> history) {
        Map<String, String> result = new LinkedHashMap<>();
        if (context != null) {
            String raw = context.get("entities");
            if (StrUtil.isNotBlank(raw)) {
                try {
                    Map<String, String> provided = objectMapper.readValue(raw, new TypeReference<>() {
                    });
                    if (provided != null) {
                        result.putAll(provided);
                    }
                } catch (Exception e) {
                    log.debug("历史实体解析失败: {}", e.getMessage());
                }
            }
        }
        if (result.isEmpty() && history != null) {
            fillEntitiesFromHistory(history, result);
        }
        return result;
    }

    /** 从历史消息倒序反向抽取实体（最多 6 个槽位），用于指代消解补全。 */
    private void fillEntitiesFromHistory(List<String> history, Map<String, String> result) {
        for (int i = history.size() - 1; i >= 0 && result.size() < 6; i--) {
            String message = history.get(i);
            String userPart = message;
            int colon = message.indexOf(':');
            if (colon > 0) {
                userPart = message.substring(colon + 1);
            }
            for (IntentRule rule : store.listRules()) {
                if (properties.getSkipIntents().contains(rule.getIntentCode())) {
                    continue;
                }
                extractor.extractEntities(rule, userPart).forEach(result::putIfAbsent);
            }
        }
    }

    private boolean isReferenceQuery(String text) {
        String trimmed = text.trim();
        for (String word : properties.getReferenceWords()) {
            if (trimmed.equals(word) || trimmed.startsWith(word)) {
                return true;
            }
        }
        return false;
    }

    /** 规则命中置信度：命中 1 个触发词达基准，每多命中 +步进，封顶 maxConfidence（复用统一算法）。 */
    private double confidence(int hits) {
        return IntentConfidence.ruleHits(
                properties.getBaseConfidence(), properties.getStepConfidence(), hits,
                properties.getMaxConfidence());
    }

    private double roundConfidence(double value) {
        return Math.round(Math.min(properties.getMaxConfidence(), value) * 100.0) / 100.0;
    }

    /** 必填槽位命中实体数（用于置信度加成）。 */
    private int entitySlotHits(IntentRule rule, Map<String, String> entities) {
        if (rule.getRequiredSlots() == null || entities.isEmpty()) {
            return 0;
        }
        int hits = 0;
        for (Map<String, String> slot : rule.getRequiredSlots()) {
            String name = slot.get("name");
            if (StrUtil.isNotBlank(name) && entities.containsKey(name)) {
                hits++;
            }
        }
        return hits;
    }

    /** 默认追问话术：列出缺失槽位；LLM 提供话术时优先使用 LLM 版本。 */
    private String defaultClarifyPrompt(List<String> missing) {
        return "请补充以下信息：" + String.join("、", missing) + "（例如格式见提示）";
    }

    /** 将规则配置应用到解析状态：编码/名称/路由/工具/阈值。 */
    private void applyRuleToState(ParseState state, IntentRule rule) {
        state.intentCode = rule.getIntentCode();
        state.intentName = rule.getIntentName();
        state.route = rule.getRouteStrategy();
        state.tools = rule.getSupportTool() == null ? List.of() : List.copyOf(rule.getSupportTool());
        state.threshold = rule.getConfidenceThreshold() > 0
                ? rule.getConfidenceThreshold() : properties.getDefaultConfidence();
    }

    /** 单次解析的中间状态容器（仅 parse 主流程内传递）。 */
    private static final class ParseState {
        String text;
        String normalized;
        List<String> history = List.of();
        Map<String, String> historyEntities = Map.of();
        boolean reference;
        Map<String, String> entities = new LinkedHashMap<>();
        double confidence;
        double threshold;
        String intentCode;
        String intentName;
        String route;
        List<String> tools = List.of();
        boolean ambiguous;
        boolean historyUsed;
        boolean llmUsed;
        String llmClarifyPrompt = "";
    }
}
