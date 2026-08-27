package com.zimo.starter.ai.intent.demo;

import com.zimo.starter.ai.intent.IntentCheckableSkill;
import com.zimo.starter.ai.intent.RuleIntentChecker;
import com.zimo.starter.ai.intent.SkillIntentCompositeChecker;
import com.zimo.starter.ai.skill.AiSkillResult;
import java.util.List;
import java.util.Map;

/**
 * 示例技能：库存查询（演示多技能冲突仲裁）。
 *
 * <p>与 {@link OrderQuerySkill} 同时命中"查询订单库存"类输入时，
 * 调度器返回 MULTI_CONFLICT 并按优先级排序。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-15
 */
public class StockQuerySkill implements IntentCheckableSkill {

    private final SkillIntentCompositeChecker checker = SkillIntentCompositeChecker.anyOf(
            new RuleIntentChecker());

    @Override
    public String name() {
        return "stock_query";
    }

    @Override
    public String description() {
        return "查询库存信息：库存数量、库位、可用量、预警";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public List<String> supportedIntents() {
        return List.of("STOCK_QUERY");
    }

    @Override
    public List<String> intentSamples() {
        return List.of(
                "查询库存",
                "库存有多少",
                "查询订单库存",
                "库位在哪",
                "可用量多少");
    }

    @Override
    public double confidenceThreshold() {
        return 0.8;
    }

    @Override
    public int priority() {
        return 30; // 介于 order(10) 与 invoice(50) 之间
    }

    @Override
    public SkillIntentCompositeChecker intentChecker() {
        return checker;
    }

    @Override
    public AiSkillResult call(Map<String, Object> arguments) {
        Object material = arguments == null ? null : arguments.get("materialCode");
        if (material == null || String.valueOf(material).isBlank()) {
            return AiSkillResult.fail("缺少参数 materialCode（物料编码）");
        }
        return AiSkillResult.ok("物料 " + material + "：库存=1,250，可用=1,180，库位=A-03");
    }
}
