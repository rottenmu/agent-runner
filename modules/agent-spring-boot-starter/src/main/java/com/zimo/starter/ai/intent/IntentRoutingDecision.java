package com.zimo.starter.ai.intent;

import java.util.List;

/**
 * 意图路由决策（Agent 调度层输出）。
 *
 * @param status       决策状态：SINGLE 单技能命中 / MULTI_CONFLICT 多技能冲突 /
 *                     NEED_CLARIFY 需澄清 / NONE 全部未命中
 * @param matchedSkills 命中的技能（按优先级排序，SINGLE 时仅一个）
 * @param results      各技能校验结果（供审计）
 * @param clarifyReason 需澄清时的原因 / 话术
 * @param fallbackHint 兜底建议（NONE 时：CHAT / KB / CLARIFY）
 * @author WorkBuddy
 * @since 2026-08-15
 */
public record IntentRoutingDecision(
        Status status,
        List<IntentCheckableSkill> matchedSkills,
        List<IntentCheckResult> results,
        String clarifyReason,
        String fallbackHint) {

    public enum Status { SINGLE, MULTI_CONFLICT, NEED_CLARIFY, NONE }

    public static IntentRoutingDecision single(IntentCheckableSkill skill, IntentCheckResult result) {
        return new IntentRoutingDecision(Status.SINGLE, List.of(skill), List.of(result), "", "");
    }

    public static IntentRoutingDecision multiConflict(List<IntentCheckableSkill> skills, List<IntentCheckResult> results) {
        return new IntentRoutingDecision(Status.MULTI_CONFLICT, skills, results,
                "多个技能同时命中：" + skills.stream().map(IntentCheckableSkill::name).toList(), "");
    }

    public static IntentRoutingDecision needClarify(IntentCheckableSkill skill, IntentCheckResult result) {
        return new IntentRoutingDecision(Status.NEED_CLARIFY, List.of(skill), List.of(result),
                result.reason(), "向用户发起澄清提问");
    }

    public static IntentRoutingDecision none(List<IntentCheckResult> results, String fallbackHint) {
        return new IntentRoutingDecision(Status.NONE, List.of(), results, "", fallbackHint);
    }
}
