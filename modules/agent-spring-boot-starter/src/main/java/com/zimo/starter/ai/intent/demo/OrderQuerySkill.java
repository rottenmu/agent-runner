package com.zimo.starter.ai.intent.demo;

import com.zimo.starter.ai.intent.ConversationContext;
import com.zimo.starter.ai.intent.IntentCheckableSkill;
import com.zimo.starter.ai.intent.RuleIntentChecker;
import com.zimo.starter.ai.intent.SkillIntentCompositeChecker;
import com.zimo.starter.ai.intent.VectorIntentChecker;
import com.zimo.starter.ai.skill.AiSkillResult;
import java.util.List;
import java.util.Map;

/**
 * 示例技能：订单查询（演示如何为 Skill 内置意图准入校验）。
 *
 * <p>采用"规则 + 向量"OR 组合：规则命中高频问法（毫秒级），
 * 向量召回覆盖口语化变体；两者任一命中即准入。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-15
 */
public class OrderQuerySkill implements IntentCheckableSkill {

    /** 组合校验器：规则 OR 向量。 */
    private final SkillIntentCompositeChecker checker = SkillIntentCompositeChecker.anyOf(
            new RuleIntentChecker(),
            new VectorIntentChecker(new VectorIntentChecker.NGramEmbeddingProvider(2)));

    @Override
    public String name() {
        return "order_query";
    }

    @Override
    public String description() {
        return "查询订单信息：订单状态、金额、物流、开票等";
    }

    @Override
    public boolean readOnly() {
        return true; // 只读技能
    }

    @Override
    public List<String> supportedIntents() {
        return List.of("ORDER_QUERY");
    }

    @Override
    public List<String> intentSamples() {
        return List.of(
                "查询订单状态",
                "订单到哪里了",
                "帮我查一下订单物流",
                "这笔订单多少钱",
                "订单开票了吗",
                "查订单",
                "看看我的订单",
                "订单");
    }

    @Override
    public double confidenceThreshold() {
        return 0.8;
    }

    @Override
    public int priority() {
        return 10; // 高优先级
    }

    @Override
    public SkillIntentCompositeChecker intentChecker() {
        return checker;
    }

    @Override
    public AiSkillResult call(Map<String, Object> arguments) {
        Object orderId = arguments == null ? null : arguments.get("orderId");
        if (orderId == null || String.valueOf(orderId).isBlank()) {
            return AiSkillResult.fail("缺少参数 orderId（订单号）");
        }
        // 演示：命中后返回模拟订单数据
        return AiSkillResult.ok("订单 " + orderId + "：状态=已发货，金额=¥1,280.00，物流=顺丰 SF1234567890");
    }
}
