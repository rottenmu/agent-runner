package com.zimo.starter.ai.intent;

import com.zimo.framework.common.intent.IntentConfidence;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 方式一：规则关键词 / 正则匹配意图校验器。
 *
 * <p>从技能声明的意图名称、few-shot 样例与自定义规则中提取匹配词，
 * 计算命中率作为置信度。高频简单意图零外部依赖、毫秒级响应，优先于 LLM。</p>
 *
 * <p>置信度公式与档位语义复用 {@link IntentConfidence}（与 module-intent 全局引擎一致，
 * P1-4 统一）：&ge;阈值 生效 / [0.5, 阈值) 部分命中需澄清 / &lt;0.5 不构成意图。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-15
 */
public class RuleIntentChecker implements IntentChecker {

    /** 首词命中基础置信度（与全局引擎默认 baseConfidence 一致）。 */
    private static final double BASE_CONFIDENCE = IntentConfidence.CONFIRMED_LEVEL;
    /** 每多命中一个词的上浮步长（与全局引擎默认 stepConfidence 一致）。 */
    private static final double STEP_CONFIDENCE = 0.06;

    @Override
    public String name() {
        return "rule";
    }

    @Override
    public IntentCheckResult check(String userQuery, ConversationContext context, IntentCheckableSkill skill) {
        if (userQuery == null || userQuery.isBlank()) {
            return IntentCheckResult.unknown("输入为空");
        }
        try {
            String normalized = userQuery.toLowerCase();
            List<String> words = collectWords(skill);
            if (words.isEmpty()) {
                return IntentCheckResult.unknown("技能未配置意图词/样例");
            }
            int hits = 0;
            for (String word : words) {
                if (normalized.contains(word)) {
                    hits++;
                }
            }
            if (hits == 0) {
                return IntentCheckResult.noMatch("未命中技能 [" + skill.name() + "] 的意图关键词");
            }
            double confidence = IntentConfidence.ruleHits(BASE_CONFIDENCE, STEP_CONFIDENCE, hits);
            IntentConfidence.Level level = IntentConfidence.level(confidence, skill.confidenceThreshold());
            if (level == IntentConfidence.Level.CONFIRMED) {
                return IntentCheckResult.match(firstIntent(skill), confidence,
                        "规则命中 " + hits + "/" + words.size() + " 个意图词");
            }
            if (level == IntentConfidence.Level.AMBIGUOUS) {
                // 命中但置信不足 [0.5, 阈值)：歧义，需要澄清
                return IntentCheckResult.matchWithClarify(firstIntent(skill), confidence,
                        "规则部分命中（" + hits + " 词，置信 " + String.format("%.2f", confidence)
                                + " 低于阈值 " + skill.confidenceThreshold() + "），需澄清确认");
            }
            // 低置信（< 0.5）：不构成意图，与全局引擎 UNSUPPORTED 语义一致
            return IntentCheckResult.noMatch("规则命中置信度 " + String.format("%.2f", confidence)
                    + " 低于下限 " + IntentConfidence.AMBIGUOUS_LOW + "，不构成技能 [" + skill.name() + "] 意图");
        } catch (Exception e) {
            return IntentCheckResult.unknown("规则校验异常: " + e.getMessage());
        }
    }

    /** 收集匹配词：意图名称 + 样例拆分 + 自定义词。 */
    private List<String> collectWords(IntentCheckableSkill skill) {
        List<String> words = new ArrayList<>();
        if (skill.supportedIntents() != null) {
            for (String intent : skill.supportedIntents()) {
                // 意图编码拆词：ORDER_QUERY -> order, query（英文小写全量 + 下划线分段）
                words.add(intent.toLowerCase());
                for (String part : intent.toLowerCase().split("_")) {
                    if (part.length() >= 3) {
                        words.add(part);
                    }
                }
            }
        }
        if (skill.intentSamples() != null) {
            for (String sample : skill.intentSamples()) {
                if (sample != null && sample.length() >= 2 && sample.length() <= 12 && !words.contains(sample)) {
                    words.add(sample.toLowerCase());
                }
            }
        }
        if (skill instanceof ExtraRules extra) {
            List<Pattern> patterns = extra.extraRegex();
            for (Pattern pattern : patterns) {
                words.add(pattern.pattern().toLowerCase());
            }
        }
        return words;
    }

    private String firstIntent(IntentCheckableSkill skill) {
        List<String> intents = skill.supportedIntents();
        return intents == null || intents.isEmpty() ? skill.name() : intents.get(0);
    }

    /** 可选扩展：技能可额外声明正则规则。 */
    public interface ExtraRules {
        List<Pattern> extraRegex();
    }
}
