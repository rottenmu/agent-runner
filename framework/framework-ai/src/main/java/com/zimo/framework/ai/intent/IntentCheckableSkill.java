package com.zimo.framework.ai.intent;

import com.zimo.framework.ai.skill.AiSkill;
import java.util.List;

/**
 * 支持内置意图准入判断的 Skill（装饰器/扩展自 {@link AiSkill}）。
 *
 * <p>实现本接口的技能即拥有"意图准入"能力：Agent 收到用户消息后，
 * 调度层（{@link IntentAwareSkillRouter}）会调用 {@link #checkUserIntent}
 * 判断是否命中本技能，再决定执行 / 澄清 / 跳过。</p>
 *
 * <p>意图配置（意图名称、few-shot 样例、置信阈值、执行优先级）全部由技能声明，
 * 与业务执行逻辑解耦；不修改 AgentScope 底层。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-15
 */
public interface IntentCheckableSkill extends AiSkill {

    /** 本技能支持的意图名称列表（如 "ORDER_QUERY"、"ORDER_OPERATE"）。 */
    List<String> supportedIntents();

    /** 意图示例样本（few-shot）：典型用户问法，供规则 / 向量 / LLM 校验参考。 */
    List<String> intentSamples();

    /** 置信阈值（0-1）：checkUserIntent 的 confidence 达标才算命中；默认 0.8。 */
    default double confidenceThreshold() {
        return 0.8;
    }

    /** 执行优先级（数字越小越优先），多技能同时命中时用于仲裁；默认 100。 */
    default int priority() {
        return 100;
    }

    /** 组合校验器：本技能选用哪些 Checker、如何合并（与/或/加权）。 */
    SkillIntentCompositeChecker intentChecker();

    /**
     * 意图准入判断：调度层入口。
     *
     * @param userQuery 用户原始输入
     * @param context   会话上下文
     * @return 是否命中本技能意图（含置信度 / 澄清标记 / 理由）
     */
    default IntentCheckResult checkUserIntent(String userQuery, ConversationContext context) {
        SkillIntentCompositeChecker checker = intentChecker();
        return checker == null
                ? IntentCheckResult.unknown("技能未配置意图校验器: " + name())
                : checker.check(userQuery, context, this);
    }
}
