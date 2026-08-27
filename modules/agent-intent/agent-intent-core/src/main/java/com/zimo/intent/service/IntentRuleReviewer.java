package com.zimo.intent.service;

import com.zimo.intent.model.IntentRule;
import com.zimo.intent.parser.IntentLlmParser;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 意图规则校验优化器（模式三）：对规则集执行静态校验并可选叠加 LLM 校验。
 *
 * <p>静态校验维度：字段完整性、编码合法性（大写英文下划线）、编码唯一性、
 * 触发词重叠冲突、槽位 label/desc 补全、置信阈值合法区间、路由策略合法枚举。
 * LLM 校验（llmEnabled 时）返回 issues/suggestions/optimizedRules 三段，
 * optimizedRules 经规则 JSON 解析后原样返回供人工确认入库。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-14
 */
public class IntentRuleReviewer {

    /** 合法路由策略枚举：与规则库 routeStrategy 字段取值范围一致 */
    public static final Set<String> ALLOWED_ROUTES = Set.of(
            "TOOL_CALL", "TOOL_CALL_WITH_CONFIRM", "KB_RETRIEVAL",
            "CHAT_REPLY", "CLARIFY", "FALLBACK", "REJECT");

    private final IntentRuleStore store;
    private final ObjectMapper objectMapper;

    public IntentRuleReviewer(IntentRuleStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /**
     * 校验规则集。
     *
     * @param rules     待校验规则；为空时校验当前已加载规则
     * @param llmParser LLM 解析器（可为 null，仅执行静态校验）
     * @param llmEnabled 是否启用 LLM 校验层
     * @return 校验报告：rulesChecked/issues/suggestions/optimizedRules/llmUsed
     */
    public Map<String, Object> review(List<IntentRule> rules, IntentLlmParser llmParser, boolean llmEnabled) {
        List<IntentRule> target = rules == null || rules.isEmpty() ? store.listRules() : rules;
        List<Map<String, String>> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        checkBasics(target, issues, suggestions);
        checkConflicts(target, issues);
        List<IntentRule> optimizedRules = List.of();
        boolean llmUsed = false;
        if (llmEnabled && llmParser != null) {
            LlmReview llm = llmReview(target, llmParser);
            if (llm != null) {
                llmUsed = true;
                issues.addAll(llm.issues);
                suggestions.addAll(llm.suggestions);
                optimizedRules = llm.optimizedRules;
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("rulesChecked", target.size());
        report.put("issues", issues);
        report.put("suggestions", suggestions);
        report.put("optimizedRules", optimizedRules);
        report.put("llmUsed", llmUsed);
        return report;
    }

    /* ---------------- 静态校验 ---------------- */

    /** 字段完整性 / 编码合法性 / 编码唯一性 / 槽位补全 / 阈值与路由合法性。 */
    private void checkBasics(List<IntentRule> rules, List<Map<String, String>> issues, List<String> suggestions) {
        Set<String> seenCodes = new LinkedHashSet<>();
        for (IntentRule rule : rules) {
            String code = StrUtil.blankToDefault(rule.getIntentCode(), "(未命名)");
            checkCodeAndFields(rule, code, seenCodes, issues);
            checkSlots(rule, code, issues);
            checkThresholdAndRoute(rule, code, issues, suggestions);
        }
    }

    /** 编码与基础字段校验：空编码、非法格式、重复编码、空触发词。 */
    private void checkCodeAndFields(IntentRule rule, String code, Set<String> seenCodes,
                                    List<Map<String, String>> issues) {
        if (StrUtil.isBlank(rule.getIntentCode())) {
            issues.add(issue(code, "缺失", "intentCode 为空"));
            return;
        }
        if (!rule.getIntentCode().matches("^[A-Z][A-Z0-9_]*$")) {
            issues.add(issue(code, "非法", "intentCode 必须为大写英文下划线编码"));
        }
        if (!seenCodes.add(rule.getIntentCode())) {
            issues.add(issue(code, "冲突", "intentCode 重复"));
        }
        if (StrUtil.isBlank(rule.getIntentName())) {
            issues.add(issue(code, "缺失", "intentName 为空"));
        }
        boolean fallbackIntent = "PARAM_CLARIFY".equals(code);
        if (!fallbackIntent && (rule.getTriggerKeywords() == null || rule.getTriggerKeywords().isEmpty())) {
            issues.add(issue(code, "缺失", "triggerKeywords 为空，规则引擎无法命中该意图"));
        }
    }

    /** 槽位校验：必填槽位缺少 label/desc 时提示补全。 */
    private void checkSlots(IntentRule rule, String code, List<Map<String, String>> issues) {
        for (Map<String, String> slot : rule.getRequiredSlots() == null ? List.<Map<String, String>>of()
                : rule.getRequiredSlots()) {
            if (StrUtil.isBlank(slot.get("name"))) {
                issues.add(issue(code, "缺失", "requiredSlots 存在无 name 的槽位"));
            } else if (StrUtil.isBlank(slot.get("label")) || StrUtil.isBlank(slot.get("desc"))) {
                issues.add(issue(code, "缺失", "槽位 " + slot.get("name") + " 缺少 label/desc，追问话术不可读"));
            }
        }
    }

    /** 阈值与路由校验：阈值超出 (0,1]、写操作低阈值、路由非法或与语义不匹配。 */
    private void checkThresholdAndRoute(IntentRule rule, String code,
                                        List<Map<String, String>> issues, List<String> suggestions) {
        double threshold = rule.getConfidenceThreshold();
        if (threshold <= 0 || threshold > 1) {
            if (!"PARAM_CLARIFY".equals(code)) {
                issues.add(issue(code, "非法", "confidenceThreshold 超出 (0,1] 区间: " + threshold));
            }
        } else if ("TOOL_CALL_WITH_CONFIRM".equals(rule.getRouteStrategy()) && threshold < 0.9) {
            suggestions.add("规则 [" + code + "] 为写操作确认路由，建议置信阈值不低于 0.9");
        }
        if (StrUtil.isBlank(rule.getRouteStrategy())) {
            issues.add(issue(code, "缺失", "routeStrategy 为空"));
        } else if (!ALLOWED_ROUTES.contains(rule.getRouteStrategy())) {
            issues.add(issue(code, "非法", "routeStrategy 不在合法枚举内: " + rule.getRouteStrategy()));
        }
    }

    /** 触发词两两冲突校验：复用规则库重叠度判定（>40% 判冲突）。 */
    private void checkConflicts(List<IntentRule> rules, List<Map<String, String>> issues) {
        for (int i = 0; i < rules.size(); i++) {
            IntentRule candidate = rules.get(i);
            for (int j = 0; j < rules.size(); j++) {
                if (i == j) {
                    continue;
                }
                IntentRule other = rules.get(j);
                double ratio = overlapRatio(candidate, other);
                if (ratio > IntentRuleStore.CONFLICT_OVERLAP_RATIO) {
                    issues.add(issue(candidate.getIntentCode(), "冲突",
                            "与规则 [" + other.getIntentCode() + "] 触发词重叠度 "
                                    + Math.round(ratio * 100) + "%，超过 40% 阈值"));
                }
            }
        }
    }

    /** 计算两条规则触发词重叠占比（以候选规则词数为分母）；无效输入返回 0。 */
    private double overlapRatio(IntentRule candidate, IntentRule other) {
        List<String> keywords = candidate.getTriggerKeywords();
        List<String> otherKeywords = other.getTriggerKeywords();
        if (keywords == null || keywords.isEmpty() || otherKeywords == null || otherKeywords.isEmpty()) {
            return 0;
        }
        long common = keywords.stream().filter(otherKeywords::contains).count();
        if (common == 0) {
            return 0;
        }
        return (double) common / keywords.size();
    }

    /* ---------------- LLM 校验 ---------------- */

    /** 调用 LLM 执行模式三校验并解析 issues/suggestions/optimizedRules；失败返回 null。 */
    private LlmReview llmReview(List<IntentRule> rules, IntentLlmParser llmParser) {
        try {
            String rulesJson = objectMapper.writeValueAsString(rules);
            String content = llmParser.reviewRules(rulesJson);
            if (StrUtil.isBlank(content)) {
                return null;
            }
            return parseLlmReview(content);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 LLM 校验响应 JSON（兼容代码块包裹与字段缺失）。 */
    private LlmReview parseLlmReview(String content) {
        String json = content.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            Map<String, Object> root = objectMapper.readValue(json.substring(start, end + 1),
                    new TypeReference<>() {
                    });
            LlmReview review = new LlmReview();
            Object issues = root.get("issues");
            if (issues instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        review.issues.add(issue(stringOf(map.get("intentCode")),
                                StrUtil.blankToDefault(stringOf(map.get("type")), "LLM"),
                                stringOf(map.get("message"))));
                    }
                }
            }
            Object suggestions = root.get("suggestions");
            if (suggestions instanceof List<?> list) {
                list.forEach(item -> {
                    if (item != null && StrUtil.isNotBlank(String.valueOf(item))) {
                        review.suggestions.add(String.valueOf(item));
                    }
                });
            }
            Object optimized = root.get("optimizedRules");
            if (optimized instanceof List<?> list) {
                review.optimizedRules = objectMapper.convertValue(list, new TypeReference<List<IntentRule>>() {
                });
            }
            return review;
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, String> issue(String code, String type, String message) {
        Map<String, String> issue = new LinkedHashMap<>();
        issue.put("intentCode", StrUtil.blankToDefault(code, "(未命名)"));
        issue.put("type", type);
        issue.put("message", message);
        return issue;
    }

    private static String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** LLM 校验结果容器。 */
    private static final class LlmReview {
        final List<Map<String, String>> issues = new ArrayList<>();
        final List<String> suggestions = new ArrayList<>();
        List<IntentRule> optimizedRules = List.of();
    }
}
