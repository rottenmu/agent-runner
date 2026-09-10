package com.zimo.framework.ai.intent.demo;

import com.zimo.framework.ai.intent.ConversationContext;
import com.zimo.framework.ai.intent.IntentCheckableSkill;
import com.zimo.framework.ai.intent.RuleIntentChecker;
import com.zimo.framework.ai.intent.SkillIntentCompositeChecker;
import com.zimo.framework.ai.skill.AiSkillResult;
import java.util.List;
import java.util.Map;

/**
 * 示例技能：开票查询（演示多技能冲突与澄清场景）。
 *
 * <p>阈值 0.9（高要求）：仅命中 1 个意图词时置信不足 → needClarify，
 * 演示"歧义澄清"；与 {@link OrderQuerySkill} 样例"订单开票了吗"重叠，
 * 演示"多技能冲突"。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-15
 */
public class InvoiceQuerySkill implements IntentCheckableSkill {

    private final SkillIntentCompositeChecker checker = SkillIntentCompositeChecker.anyOf(
            new RuleIntentChecker());

    @Override
    public String name() {
        return "invoice_query";
    }

    @Override
    public String description() {
        return "查询发票信息：开票状态、发票抬头、税率、电子发票下载";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public List<String> supportedIntents() {
        return List.of("INVOICE_QUERY");
    }

    @Override
    public List<String> intentSamples() {
        return List.of(
                "查询发票开票状态",
                "发票抬头是什么",
                "帮我下载电子发票",
                "税率是多少",
                "开票了吗");
    }

    @Override
    public double confidenceThreshold() {
        return 0.9; // 高阈值：单次命中即触发澄清
    }

    @Override
    public int priority() {
        return 50; // 中等优先级
    }

    @Override
    public SkillIntentCompositeChecker intentChecker() {
        return checker;
    }

    @Override
    public AiSkillResult call(Map<String, Object> arguments) {
        Object invoiceId = arguments == null ? null : arguments.get("invoiceId");
        if (invoiceId == null || String.valueOf(invoiceId).isBlank()) {
            return AiSkillResult.fail("缺少参数 invoiceId（发票号）");
        }
        return AiSkillResult.ok("发票 " + invoiceId + "：状态=已开具，税率=13%，可下载电子发票");
    }
}
