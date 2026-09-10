package com.zimo.framework.ai.intent;

/**
 * 意图检查器 SPI：可插拔的意图识别实现。
 *
 * <p>三种内置实现：{@link RuleIntentChecker}（规则关键词/正则）、
 * {@link LlmIntentChecker}（大模型 Prompt 少样本）、
 * {@link VectorIntentChecker}（向量召回样例匹配）。
 * 一个技能可组合多个 Checker，见 {@link SkillIntentCompositeChecker}。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-15
 */
public interface IntentChecker {

    /** 检查器名称（用于日志 / 审计标识）。 */
    String name();

    /**
     * 校验用户输入是否命中目标技能意图。
     *
     * @param userQuery 用户原始输入
     * @param context   会话上下文（租户 / 用户 / 历史）
     * @param skill     目标技能（提供意图声明、样例、阈值等配置）
     * @return 校验结果；实现应保证不抛异常（内部兜底为 unknown）
     */
    IntentCheckResult check(String userQuery, ConversationContext context, IntentCheckableSkill skill);
}
