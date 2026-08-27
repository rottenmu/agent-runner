package com.zimo.starter.ai.intent;

import java.util.ArrayList;
import java.util.List;

/**
 * 组合校验器：将多个 {@link IntentChecker} 按与 / 或 / 加权模式合并。
 *
 * <p>一个技能可自选多个 Checker（如"规则 + LLM"），本类负责：
 * <ul>
 *   <li>AND（与）：全部 Checker 命中才算命中，适合高风险场景（规则 + LLM 双确认）；</li>
 *   <li>OR（或）：任一命中即命中，取最高置信度，适合覆盖不同表达方式；</li>
 *   <li>WEIGHTED（加权）：按权重加权平均置信度，权重之和归一化。</li>
 * </ul>
 * 任一 Checker 返回 needClarify=true 时整体标记需澄清。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-15
 */
public class SkillIntentCompositeChecker {

    /** 组合模式。 */
    public enum CombineMode { AND, OR, WEIGHTED }

    private final List<IntentChecker> checkers;
    private final CombineMode mode;
    private final double[] weights;

    public SkillIntentCompositeChecker(List<IntentChecker> checkers, CombineMode mode, double... weights) {
        this.checkers = checkers == null ? new ArrayList<>() : List.copyOf(checkers);
        this.mode = mode == null ? CombineMode.OR : mode;
        this.weights = weights;
    }

    /** OR 快捷构造。 */
    public static SkillIntentCompositeChecker anyOf(IntentChecker... checkers) {
        return new SkillIntentCompositeChecker(List.of(checkers), CombineMode.OR);
    }

    /** AND 快捷构造。 */
    public static SkillIntentCompositeChecker allOf(IntentChecker... checkers) {
        return new SkillIntentCompositeChecker(List.of(checkers), CombineMode.AND);
    }

    /** 加权快捷构造。 */
    public static SkillIntentCompositeChecker weighted(double[] weights, IntentChecker... checkers) {
        return new SkillIntentCompositeChecker(List.of(checkers), CombineMode.WEIGHTED, weights);
    }

    /**
     * 执行全部 Checker 并合并结果。
     */
    public IntentCheckResult check(String userQuery, ConversationContext context, IntentCheckableSkill skill) {
        if (checkers.isEmpty()) {
            return IntentCheckResult.unknown("未配置任何 Checker: " + skill.name());
        }
        List<IntentCheckResult> results = new ArrayList<>();
        for (IntentChecker checker : checkers) {
            results.add(checker.check(userQuery, context, skill));
        }
        // 任一需要澄清 → 整体需澄清（歧义优先暴露给用户）
        IntentCheckResult firstClarify = results.stream()
                .filter(IntentCheckResult::needClarify)
                .findFirst().orElse(null);
        // 任一未知（如 LLM 未配置）→ 剔除，避免阻断
        List<IntentCheckResult> usable = results.stream()
                .filter(r -> !(r.reason() != null && r.reason().startsWith("LLM 未配置")))
                .toList();

        switch (mode) {
            case AND: {
                boolean allMatch = !usable.isEmpty() && usable.stream().allMatch(IntentCheckResult::isMatch);
                if (allMatch) {
                    double confidence = usable.stream().mapToDouble(IntentCheckResult::confidence).min().orElse(0);
                    IntentCheckResult result = IntentCheckResult.match(firstIntent(skill), confidence,
                            "AND 组合全部命中，取最低置信 " + String.format("%.2f", confidence));
                    return firstClarify == null ? result
                            : IntentCheckResult.matchWithClarify(result.intentName(), result.confidence(),
                                    result.reason() + "；且需澄清");
                }
                return IntentCheckResult.noMatch("AND 组合未全部命中");
            }
            case WEIGHTED: {
                double total = 0;
                double weightSum = 0;
                for (int i = 0; i < usable.size(); i++) {
                    double weight = weights != null && i < weights.length ? weights[i] : 1.0 / usable.size();
                    IntentCheckResult r = usable.get(i);
                    total += (r.isMatch() ? r.confidence() : 0) * weight;
                    weightSum += weight;
                }
                double confidence = weightSum == 0 ? 0 : IntentCheckResult.normalize(total / weightSum);
                boolean match = confidence >= skill.confidenceThreshold();
                if (match) {
                    IntentCheckResult result = IntentCheckResult.match(firstIntent(skill), confidence,
                            "加权组合置信 " + String.format("%.2f", confidence));
                    return firstClarify == null ? result
                            : IntentCheckResult.matchWithClarify(result.intentName(), result.confidence(), result.reason());
                }
                return IntentCheckResult.matchWithClarify(firstIntent(skill), confidence,
                        "加权组合置信 " + String.format("%.2f", confidence) + " 低于阈值，需澄清");
            }
            case OR:
            default: {
                // 取最高置信的命中结果
                IntentCheckResult best = null;
                for (IntentCheckResult r : usable) {
                    if (r.isMatch() && (best == null || r.confidence() > best.confidence())) {
                        best = r;
                    }
                }
                if (best == null) {
                    return IntentCheckResult.noMatch("OR 组合均未命中");
                }
                if (firstClarify != null) {
                    return IntentCheckResult.matchWithClarify(best.intentName(), best.confidence(),
                            best.reason() + "；另一 Checker 需澄清");
                }
                return best;
            }
        }
    }

    private String firstIntent(IntentCheckableSkill skill) {
        List<String> intents = skill.supportedIntents();
        return intents == null || intents.isEmpty() ? skill.name() : intents.get(0);
    }
}
