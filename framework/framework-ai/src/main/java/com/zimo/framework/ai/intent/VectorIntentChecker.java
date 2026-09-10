package com.zimo.framework.ai.intent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 方式三：向量召回样例匹配意图校验器。
 *
 * <p>将技能 few-shot 样例字符 n-gram 化建库，用户输入同样 n-gram 化后
 * 计算 Dice 集合相似度，取最高值作为置信度。零外部依赖（中文友好），
 * 可通过 {@link EmbeddingProvider} 替换为真实 embedding 服务（返回语义向量集合）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-15
 */
public class VectorIntentChecker implements IntentChecker {

    /** 向量化提供者：默认 n-gram 词法向量，可替换为真实 embedding。 */
    public interface EmbeddingProvider {
        Set<String> embed(String text);
    }

    /** 字符 n-gram 词法向量（中文友好，无需分词）。 */
    public static class NGramEmbeddingProvider implements EmbeddingProvider {
        private final int n;

        public NGramEmbeddingProvider(int n) {
            this.n = Math.max(1, n);
        }

        @Override
        public Set<String> embed(String text) {
            Set<String> grams = new HashSet<>();
            if (text == null) {
                return grams;
            }
            for (int i = 0; i <= text.length() - n; i++) {
                grams.add(text.substring(i, i + n));
            }
            return grams;
        }
    }

    private final EmbeddingProvider provider;

    public VectorIntentChecker(EmbeddingProvider provider) {
        this.provider = provider == null ? new NGramEmbeddingProvider(2) : provider;
    }

    @Override
    public String name() {
        return "vector";
    }

    @Override
    public IntentCheckResult check(String userQuery, ConversationContext context, IntentCheckableSkill skill) {
        if (userQuery == null || userQuery.isBlank()) {
            return IntentCheckResult.unknown("输入为空");
        }
        try {
            List<String> samples = skill.intentSamples();
            if (samples == null || samples.isEmpty()) {
                return IntentCheckResult.unknown("技能未配置意图示例样本");
            }
            Set<String> queryGrams = provider.embed(userQuery);
            if (queryGrams.isEmpty()) {
                return IntentCheckResult.noMatch("输入过短，无法向量化");
            }
            double best = 0;
            for (String sample : samples) {
                Set<String> sampleGrams = provider.embed(sample);
                best = Math.max(best, dice(queryGrams, sampleGrams));
            }
            if (best <= 0.1) {
                return IntentCheckResult.noMatch("向量相似度过低（" + String.format("%.2f", best) + "），未命中 [" + skill.name() + "] 样例");
            }
            boolean aboveThreshold = best >= skill.confidenceThreshold();
            if (aboveThreshold) {
                return IntentCheckResult.match(firstIntent(skill), best,
                        "向量召回命中，最高相似度 " + String.format("%.2f", best));
            }
            return IntentCheckResult.noMatch("向量相似度 " + String.format("%.2f", best)
                    + " 低于阈值 " + skill.confidenceThreshold() + "，不构成澄清区间");
        } catch (Exception e) {
            return IntentCheckResult.unknown("向量校验异常: " + e.getMessage());
        }
    }

    private String firstIntent(IntentCheckableSkill skill) {
        List<String> intents = skill.supportedIntents();
        return intents == null || intents.isEmpty() ? skill.name() : intents.get(0);
    }

    /** Dice 系数：2|A∩B| / (|A|+|B|)，对 n-gram 集合无需按位置对齐。 */
    private static double dice(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int intersection = 0;
        for (String gram : a) {
            if (b.contains(gram)) {
                intersection++;
            }
        }
        return 2.0 * intersection / (a.size() + b.size());
    }
}
