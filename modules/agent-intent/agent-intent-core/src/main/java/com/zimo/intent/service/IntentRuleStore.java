package com.zimo.intent.service;

import com.zimo.intent.IntentProperties;
import com.zimo.intent.model.IntentRule;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * 意图规则库存储：负责规则加载、热更新、持久化、CRUD 与触发词冲突校验。
 *
 * <p>规则来源由 {@link IntentProperties#getRulesLocation()} 决定，支持 classpath:
 * 与 file: 前缀；仅 file: 来源支持写回持久化，classpath 来源只读（变更仅内存生效）。
 * 冲突判定规则：候选规则触发词与既有规则重叠占比超过 40% 即判冲突。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-14
 */
public class IntentRuleStore {

    private static final Logger log = LoggerFactory.getLogger(IntentRuleStore.class);

    private static final String ROOT_ALIAS = "routes";

    /** 触发词重叠冲突阈值：重叠词占候选规则触发词比例超过该值判定冲突 */
    public static final double CONFLICT_OVERLAP_RATIO = 0.4;

    private final ObjectMapper objectMapper;
    private final IntentProperties properties;
    private final List<IntentRule> rules = new ArrayList<>();
    private final Map<String, IntentRule> ruleByCode = new LinkedHashMap<>();

    public IntentRuleStore(ObjectMapper objectMapper, IntentProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties == null ? new IntentProperties() : properties;
        reload();
    }

    /** 重新加载规则集（支持规则热更新）；加载失败保留空集合并记录错误日志。 */
    public synchronized void reload() {
        rules.clear();
        ruleByCode.clear();
        String location = properties.getRulesLocation();
        try {
            Resource resource = resolveResource(location);
            if (resource == null || !resource.exists()) {
                log.warn("意图规则文件不存在: {}", location);
                return;
            }
            Map<String, Object> root = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {
            });
            Object raw = root.get(ROOT_ALIAS);
            if (raw == null) {
                raw = root.get("rules");
            }
            List<IntentRule> loaded = objectMapper.convertValue(raw, new TypeReference<List<IntentRule>>() {
            });
            loaded.forEach(rule -> {
                if (StrUtil.isNotBlank(rule.getIntentCode())) {
                    rules.add(rule);
                    ruleByCode.put(rule.getIntentCode(), rule);
                }
            });
            log.info("意图规则加载完成: {} 条（来源 {}）", rules.size(), location);
        } catch (Exception e) {
            log.error("意图规则加载失败: {}", location, e);
        }
    }

    public synchronized List<IntentRule> listRules() {
        return List.copyOf(rules);
    }

    public synchronized IntentRule get(String code) {
        return code == null ? null : ruleByCode.get(code);
    }

    public synchronized int size() {
        return rules.size();
    }

    /**
     * 新增意图规则：编码全局唯一 + 触发词冲突校验，随后持久化。
     *
     * @param rule 新规则（intentCode 必填，不允许为空）
     * @return 入库后的规则
     * @throws IllegalArgumentException 编码为空、编码重复或触发词与既有规则重叠度过高
     */
    public synchronized IntentRule addRule(IntentRule rule) {
        String code = rule == null ? null : rule.getIntentCode();
        if (StrUtil.isBlank(code)) {
            throw new IllegalArgumentException("intentCode 不能为空");
        }
        if (ruleByCode.containsKey(code)) {
            throw new IllegalArgumentException("意图编码已存在: " + code);
        }
        checkConflict(rule);
        rules.add(rule);
        ruleByCode.put(code, rule);
        persist();
        log.info("意图规则新增: {} ({})", code, rule.getIntentName());
        return rule;
    }

    /**
     * 编辑意图规则：按 intentCode 定位替换（编码不可变更）。
     *
     * @param code 原规则编码，不允许为空
     * @param rule 新规则内容，编码字段以 code 参数为准
     * @return 更新后的规则
     * @throws IllegalArgumentException 目标规则不存在或触发词冲突
     */
    public synchronized IntentRule updateRule(String code, IntentRule rule) {
        if (StrUtil.isBlank(code) || !ruleByCode.containsKey(code)) {
            throw new IllegalArgumentException("意图规则不存在: " + code);
        }
        IntentRule updated = new IntentRule();
        copy(rule, updated);
        updated.setIntentCode(code);
        checkConflict(updated, code);
        int index = rules.indexOf(ruleByCode.get(code));
        rules.set(index, updated);
        ruleByCode.put(code, updated);
        persist();
        log.info("意图规则更新: {}", code);
        return updated;
    }

    /**
     * 删除意图规则。
     *
     * @param code 规则编码
     * @return 删除成功返回 true，规则不存在返回 false
     */
    public synchronized boolean deleteRule(String code) {
        IntentRule existing = ruleByCode.remove(code);
        if (existing == null) {
            return false;
        }
        rules.remove(existing);
        persist();
        log.info("意图规则删除: {}", code);
        return true;
    }

    /** 将当前规则集序列化为 JSON 数组文本，供 LLM 提示词 {rules} 占位符注入。 */
    public synchronized String rulesJson() {
        try {
            return objectMapper.writeValueAsString(rules);
        } catch (Exception e) {
            log.warn("规则集序列化失败: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * 解析 LLM 生成的规则 JSON 数组（兼容代码块包裹）；解析失败返回空列表。
     *
     * @param json LLM 输出的规则 JSON 数组文本，允许为空
     * @return 解析出的规则列表，字段缺失项保留原样由上层归一化
     */
    public List<IntentRule> parseRulesFromJson(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {
            String body = extractJsonArray(json);
            if (body == null) {
                return List.of();
            }
            List<IntentRule> parsed = objectMapper.readValue(body, new TypeReference<List<IntentRule>>() {
            });
            return parsed == null ? List.of() : parsed;
        } catch (Exception e) {
            log.warn("LLM 规则 JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 冲突检测（非抛异常版本）：返回候选规则与既有规则的触发词重叠问题描述列表。
     *
     * @param candidate 候选规则，允许为空
     * @param selfCode  自身编码（编辑场景排除自身），允许为空
     * @return 冲突描述列表，无冲突返回空列表
     */
    public synchronized List<String> conflictIssues(IntentRule candidate, String selfCode) {
        List<String> issues = new ArrayList<>();
        List<String> keywords = candidate == null || candidate.getTriggerKeywords() == null
                ? List.of() : candidate.getTriggerKeywords();
        if (keywords.isEmpty()) {
            return issues;
        }
        for (IntentRule existing : rules) {
            if (selfCode != null && selfCode.equals(existing.getIntentCode())) {
                continue;
            }
            if (candidate != null && candidate.getIntentCode() != null
                    && candidate.getIntentCode().equals(existing.getIntentCode())) {
                continue;
            }
            if (existing.getTriggerKeywords() == null || existing.getTriggerKeywords().isEmpty()) {
                continue;
            }
            List<String> common = existing.getTriggerKeywords().stream()
                    .filter(keywords::contains)
                    .toList();
            if (!common.isEmpty() && (double) common.size() / keywords.size() > CONFLICT_OVERLAP_RATIO) {
                issues.add("与规则 [" + existing.getIntentCode() + "] 触发词重叠度过高: "
                        + String.join("、", common));
            }
        }
        return issues;
    }

    /* ---------------- 内部实现 ---------------- */

    /** 校验触发词与既有规则冲突（跳过自身），重叠词占比超 40% 判定冲突。 */
    private void checkConflict(IntentRule rule) {
        checkConflict(rule, null);
    }

    private void checkConflict(IntentRule rule, String selfCode) {
        List<String> issues = conflictIssues(rule, selfCode);
        if (!issues.isEmpty()) {
            throw new IllegalArgumentException("触发词冲突: " + String.join("；", issues));
        }
    }

    /** 写回规则文件：仅 rules-location 为 file: 时生效；classpath 只读。 */
    private void persist() {
        String location = properties.getRulesLocation();
        if (StrUtil.isBlank(location) || !location.startsWith("file:")) {
            log.warn("规则源为 classpath（只读），变更仅内存生效，重启后恢复：{}", location);
            return;
        }
        try {
            java.nio.file.Path path = java.nio.file.Path.of(location.substring("file:".length()));
            java.nio.file.Files.createDirectories(path.getParent());
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("version", "1.0.0");
            root.put("schema", "intent-rules-standard-v1");
            root.put("updatedAt", java.time.LocalDate.now().toString());
            root.put("rules", rules);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), root);
            log.info("意图规则已持久化: {}", location);
        } catch (Exception e) {
            throw new IllegalStateException("意图规则持久化失败: " + location, e);
        }
    }

    private Resource resolveResource(String location) {
        if (StrUtil.isBlank(location)) {
            return null;
        }
        if (location.startsWith("file:")) {
            return new FileSystemResource(location.substring("file:".length()));
        }
        String path = location.startsWith("classpath:") ? location.substring("classpath:".length()) : location;
        return new ClassPathResource(path);
    }

    /** 截取 JSON 数组主体（兼容 ``` 代码块包裹与前后缀噪声）。 */
    private static String extractJsonArray(String raw) {
        String text = raw.trim();
        if (text.startsWith("```")) {
            int first = text.indexOf('\n');
            int last = text.lastIndexOf("```");
            if (first > 0 && last > first) {
                text = text.substring(first + 1, last).trim();
            }
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private static void copy(IntentRule from, IntentRule to) {
        to.setIntentName(from.getIntentName());
        to.setIntentDesc(from.getIntentDesc());
        to.setTriggerKeywords(from.getTriggerKeywords());
        to.setRequiredSlots(from.getRequiredSlots());
        to.setOptionalSlots(from.getOptionalSlots());
        to.setSupportTool(from.getSupportTool());
        to.setConfidenceThreshold(from.getConfidenceThreshold());
        to.setRouteStrategy(from.getRouteStrategy());
        to.setRejectRule(from.getRejectRule());
    }
}
