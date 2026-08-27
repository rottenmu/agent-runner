package com.zimo.starter.ai.intent;

/**
 * 技能意图准入判断结果（DTO）。
 *
 * <p>由 {@link IntentChecker} 或 {@link IntentCheckableSkill#checkUserIntent} 产生，
 * 描述"用户当前输入是否命中本技能意图"，供 Agent 调度层做路由决策。</p>
 *
 * @param isMatch     是否匹配本技能意图
 * @param confidence  置信度 0-1
 * @param reason      判断理由（供审计 / BadCase 分析）
 * @param needClarify 是否需要向用户澄清（歧义 / 缺参时 true）
 * @param intentName  识别出的意图名称（未识别可为空）
 * @author WorkBuddy
 * @since 2026-08-15
 */
public record IntentCheckResult(
        boolean isMatch,
        double confidence,
        String reason,
        boolean needClarify,
        String intentName) {

    /** 命中：置信度达标，无需澄清。 */
    public static IntentCheckResult match(String intentName, double confidence, String reason) {
        return new IntentCheckResult(true, confidence, reason, false, intentName);
    }

    /** 未命中。 */
    public static IntentCheckResult noMatch(String reason) {
        return new IntentCheckResult(false, 0, reason, false, null);
    }

    /** 命中但需要澄清（歧义 / 参数不足，先向用户确认再执行）。 */
    public static IntentCheckResult matchWithClarify(String intentName, double confidence, String reason) {
        return new IntentCheckResult(true, confidence, reason, true, intentName);
    }

    /** 无法判断（如 LLM 未配置），视为未命中且不阻断其他技能。 */
    public static IntentCheckResult unknown(String reason) {
        return new IntentCheckResult(false, 0, reason, false, null);
    }

    /** 边界检查：置信度归一化到 [0,1]。 */
    public static double normalize(double confidence) {
        if (Double.isNaN(confidence)) {
            return 0;
        }
        return Math.max(0, Math.min(1, confidence));
    }
}
