package com.zimo.framework.common.intent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 统一置信度算法与档位语义测试（P1-4）。 */
class IntentConfidenceTest {

    @Test
    void ruleHitsMatchesLegacyFormula() {
        // 全局引擎/技能准入共同公式: base + (hits-1)*step，封顶
        assertThat(IntentConfidence.ruleHits(0.8, 0.06, 1)).isEqualTo(0.8);
        assertThat(IntentConfidence.ruleHits(0.8, 0.06, 2)).isEqualTo(0.86);
        assertThat(IntentConfidence.ruleHits(0.8, 0.06, 4)).isEqualTo(0.98);
        // 封顶
        assertThat(IntentConfidence.ruleHits(0.8, 0.06, 5)).isEqualTo(0.99);
        // 自定义参数（全局引擎可配置）
        assertThat(IntentConfidence.ruleHits(0.6, 0.1, 3, 0.95)).isEqualTo(0.8);
        assertThat(IntentConfidence.ruleHits(0.6, 0.1, 5, 0.95)).isEqualTo(0.95);
        // hits<=0 -> 0
        assertThat(IntentConfidence.ruleHits(0.8, 0.06, 0)).isEqualTo(0.0);
    }

    @Test
    void levelClassifiesThreeTiers() {
        assertThat(IntentConfidence.level(0.9)).isEqualTo(IntentConfidence.Level.CONFIRMED);
        assertThat(IntentConfidence.level(0.8)).isEqualTo(IntentConfidence.Level.CONFIRMED);
        assertThat(IntentConfidence.level(0.7)).isEqualTo(IntentConfidence.Level.AMBIGUOUS);
        assertThat(IntentConfidence.level(0.5)).isEqualTo(IntentConfidence.Level.AMBIGUOUS);
        assertThat(IntentConfidence.level(0.3)).isEqualTo(IntentConfidence.Level.BELOW_FLOOR);
        // 自定义阈值
        assertThat(IntentConfidence.level(0.75, 0.85)).isEqualTo(IntentConfidence.Level.AMBIGUOUS);
        assertThat(IntentConfidence.level(0.85, 0.85)).isEqualTo(IntentConfidence.Level.CONFIRMED);
    }

    @Test
    void constantsAlignWithEngineDefaults() {
        // 与 module-intent IntentProperties 默认值一致（ambiguousLow=0.5 / ambiguousHigh=0.8）
        assertThat(IntentConfidence.AMBIGUOUS_LOW).isEqualTo(0.5);
        assertThat(IntentConfidence.CONFIRMED_LEVEL).isEqualTo(0.8);
        assertThat(IntentConfidence.MAX).isEqualTo(0.99);
    }
}
