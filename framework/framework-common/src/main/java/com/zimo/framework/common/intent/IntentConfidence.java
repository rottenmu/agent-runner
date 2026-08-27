package com.zimo.framework.common.intent;

/**
 * 意图置信度统一算法与档位语义（P1-4 治理：消除两套置信度实现）。
 *
 * <p>此前两套体系各写一套：
 * <ul>
 *   <li>module-intent 全局引擎：{@code base + (hits-1)*step}，参数可配（yml plugin.intent）</li>
 *   <li>starter 技能准入 {@code RuleIntentChecker}：硬编码 0.8 / 0.06</li>
 * </ul>
 * 两者公式相同但互不感知，档位语义（≥0.8 生效 / 0.5~0.8 模糊澄清 / &lt;0.5 不支持）
 * 也各写一份。本类统一公式与档位常量，两侧共用，保持各自配置化能力。</p>
 *
 * <p>档位语义（与 module-intent 三级置信度路由一致）：</p>
 * <ul>
 *   <li>{@link #CONFIRMED_LEVEL} 0.8：识别生效，正常路由</li>
 *   <li>{@link #AMBIGUOUS_LOW} 0.5：低于该值判定未知（UNSUPPORTED / noMatch）</li>
 *   <li>[AMBIGUOUS_LOW, 阈值) 区间：模糊识别，需二次确认 / 澄清</li>
 * </ul>
 *
 * @author WorkBuddy
 * @since 2026-08-17
 */
public final class IntentConfidence {

    /** 生效阈值（默认）：置信度 ≥ 该值识别生效。与全局引擎 ambiguous-high / 默认规则阈值一致。 */
    public static final double CONFIRMED_LEVEL = 0.8;

    /** 模糊档下限：置信度低于该值判定未知，不构成澄清/确认。 */
    public static final double AMBIGUOUS_LOW = 0.5;

    /** 置信度上限（默认封顶）。 */
    public static final double MAX = 0.99;

    /** 置信度档位。 */
    public enum Level {
        /** 生效：≥ 阈值（默认 0.8）。 */
        CONFIRMED,
        /** 模糊：阈值以下但 ≥ AMBIGUOUS_LOW，需澄清/二次确认。 */
        AMBIGUOUS,
        /** 未知：&lt; AMBIGUOUS_LOW。 */
        BELOW_FLOOR
    }

    private IntentConfidence() {
    }

    /**
     * 规则命中置信度统一公式：{@code min(max, base + (hits-1)*step)}，四舍五入保留 2 位。
     *
     * @param base 首词命中基础置信度（全局引擎可配，技能准入默认 0.8）
     * @param step 每多命中一个词的上浮步长（全局引擎可配，技能准入默认 0.06）
     * @param hits 命中词数（&gt;=1）
     * @param max  封顶值（默认 {@link #MAX}）
     * @return 置信度 [0, max]
     */
    public static double ruleHits(double base, double step, int hits, double max) {
        if (hits <= 0) {
            return 0;
        }
        double value = base + (hits - 1) * step;
        return Math.round(Math.min(max, value) * 100.0) / 100.0;
    }

    /** {@link #ruleHits(double, double, int, double)} 默认封顶 0.99。 */
    public static double ruleHits(double base, double step, int hits) {
        return ruleHits(base, step, hits, MAX);
    }

    /**
     * 档位判定：以阈值（默认 {@link #CONFIRMED_LEVEL}）与
     * {@link #AMBIGUOUS_LOW} 为界。
     *
     * @param confidence 置信度
     * @param threshold  生效阈值（技能可声明自己的置信阈值）
     */
    public static Level level(double confidence, double threshold) {
        if (confidence >= threshold) {
            return Level.CONFIRMED;
        }
        return confidence >= AMBIGUOUS_LOW ? Level.AMBIGUOUS : Level.BELOW_FLOOR;
    }

    /** {@link #level(double, double)} 默认阈值 0.8。 */
    public static Level level(double confidence) {
        return level(confidence, CONFIRMED_LEVEL);
    }
}
