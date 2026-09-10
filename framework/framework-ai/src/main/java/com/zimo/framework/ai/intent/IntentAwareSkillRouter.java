package com.zimo.framework.ai.intent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Agent 技能意图路由调度器（装饰器 / 包装模式，不修改 AgentScope 底层）。
 *
 * <p>Agent 收到用户消息后：遍历全部 {@link IntentCheckableSkill}，
 * 依次调用 {@link IntentCheckableSkill#checkUserIntent} 获取准入结果，按策略决策：
 * <ul>
 *   <li>任一技能 needClarify=true → 停止调度，NEED_CLARIFY（澄清优先）；</li>
 *   <li>单一高置信命中 → SINGLE，执行该技能；</li>
 *   <li>多个技能同时命中 → MULTI_CONFLICT，按优先级排序返回冲突提示；</li>
 *   <li>全部未命中 → NONE，进入兜底（闲聊 / 知识库 / 澄清用户意图）。</li>
 * </ul></p>
 *
 * @author WorkBuddy
 * @since 2026-08-15
 */
public class IntentAwareSkillRouter {

    private final List<IntentCheckableSkill> skills;

    public IntentAwareSkillRouter(List<? extends IntentCheckableSkill> skills) {
        this.skills = skills == null ? List.of() : List.copyOf(skills);
    }

    /**
     * 意图路由：对用户输入执行全部技能准入判断并产出决策。
     */
    public IntentRoutingDecision route(String userQuery, ConversationContext context) {
        if (userQuery == null || userQuery.isBlank()) {
            return IntentRoutingDecision.none(List.of(), "CHAT_REPLY");
        }
        List<IntentCheckResult> results = new ArrayList<>();
        for (IntentCheckableSkill skill : skills) {
            IntentCheckResult result = skill.checkUserIntent(userQuery, context);
            results.add(result);
        }

        // 1. 澄清优先：任一技能需要澄清 → 停止调度
        for (int i = 0; i < skills.size(); i++) {
            if (results.get(i).needClarify()) {
                return IntentRoutingDecision.needClarify(skills.get(i), results.get(i));
            }
        }

        // 2. 命中技能（按优先级升序 = 高优先在前）
        List<IntentCheckableSkill> matched = new ArrayList<>();
        for (int i = 0; i < skills.size(); i++) {
            if (results.get(i).isMatch()) {
                matched.add(skills.get(i));
            }
        }
        matched.sort(Comparator.comparingInt(IntentCheckableSkill::priority));

        // 3. 决策
        if (matched.isEmpty()) {
            return IntentRoutingDecision.none(results, "CHAT_OR_KB_OR_CLARIFY");
        }
        if (matched.size() == 1) {
            IntentCheckableSkill skill = matched.get(0);
            IntentCheckResult result = results.get(skills.indexOf(skill));
            return IntentRoutingDecision.single(skill, result);
        }
        return IntentRoutingDecision.multiConflict(matched, results);
    }

    /** 获取所有已注册的可意图校验技能。 */
    public List<IntentCheckableSkill> skills() {
        return skills;
    }
}
