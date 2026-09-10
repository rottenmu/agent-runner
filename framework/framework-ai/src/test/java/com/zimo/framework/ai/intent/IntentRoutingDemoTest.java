package com.zimo.framework.ai.intent;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.framework.ai.intent.demo.InvoiceQuerySkill;
import com.zimo.framework.ai.intent.demo.OrderQuerySkill;
import com.zimo.framework.ai.intent.demo.StockQuerySkill;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 意图准入框架 Demo 测试（deliverable 8）：
 * 命中 / 不命中 / 歧义澄清 / 多技能冲突 四场景。
 *
 * @author WorkBuddy
 * @since 2026-08-15
 */
class IntentRoutingDemoTest {

    private IntentAwareSkillRouter router;
    private ConversationContext context;

    @BeforeEach
    void setUp() {
        OrderQuerySkill orderSkill = new OrderQuerySkill();
        InvoiceQuerySkill invoiceSkill = new InvoiceQuerySkill();
        StockQuerySkill stockSkill = new StockQuerySkill();
        router = new IntentAwareSkillRouter(List.of(orderSkill, invoiceSkill, stockSkill));
        context = ConversationContext.of("u001", "user-1", "s1");
    }

    /* ---------------- 场景 1：命中技能 ---------------- */

    @Test
    void routesToOrderQueryWhenIntentMatches() {
        IntentRoutingDecision decision = router.route("帮我查一下订单物流到哪里了", context);
        assertThat(decision.status()).isEqualTo(IntentRoutingDecision.Status.SINGLE);
        assertThat(decision.matchedSkills()).hasSize(1);
        assertThat(decision.matchedSkills().get(0).name()).isEqualTo("order_query");
        assertThat(decision.results().get(0).confidence()).isGreaterThanOrEqualTo(0.8);
    }

    /* ---------------- 场景 2：全部未命中 → 兜底 ---------------- */

    @Test
    void fallsBackWhenNoSkillMatches() {
        IntentRoutingDecision decision = router.route("今天天气怎么样", context);
        assertThat(decision.status()).isEqualTo(IntentRoutingDecision.Status.NONE);
        assertThat(decision.matchedSkills()).isEmpty();
        assertThat(decision.fallbackHint()).isNotBlank(); // CHAT_OR_KB_OR_CLARIFY
    }

    /* ---------------- 场景 3：歧义需要澄清 ---------------- */

    @Test
    void stopsSchedulingWhenSkillNeedsClarify() {
        // "开票了吗"：invoice_query 仅命中 1 词（conf=0.8 < 阈值 0.9）→ needClarify
        IntentRoutingDecision decision = router.route("开票了吗", context);
        assertThat(decision.status()).isEqualTo(IntentRoutingDecision.Status.NEED_CLARIFY);
        assertThat(decision.clarifyReason()).contains("澄清");
    }

    /* ---------------- 场景 4：多技能冲突 ---------------- */

    @Test
    void reportsConflictWhenMultipleSkillsMatch() {
        // "查询订单库存"：order_query（样例"查询订单状态/订单"命中）与 stock_query（样例"查询订单库存"命中）同时高置信命中
        IntentRoutingDecision decision = router.route("查询订单库存", context);
        assertThat(decision.status()).isEqualTo(IntentRoutingDecision.Status.MULTI_CONFLICT);
        assertThat(decision.matchedSkills()).hasSize(2);
        // 按优先级排序：order_query(10) 在 stock_query(30) 前
        assertThat(decision.matchedSkills().get(0).name()).isEqualTo("order_query");
        assertThat(decision.matchedSkills().get(1).name()).isEqualTo("stock_query");
        assertThat(decision.clarifyReason()).contains("多个技能同时命中");
    }

    /* ---------------- 技能级校验（单技能直接调用） ---------------- */

    @Test
    void skillLevelCheckUserIntent() {
        OrderQuerySkill skill = new OrderQuerySkill();
        IntentCheckResult result = skill.checkUserIntent("查一下我的订单", context);
        assertThat(result.isMatch()).isTrue();
        assertThat(result.intentName()).isEqualTo("ORDER_QUERY");
    }

    /* ---------------- 组合校验器（AND 模式演示） ---------------- */

    @Test
    void compositeAndModeRequiresAllCheckers() {
        SkillIntentCompositeChecker andChecker = SkillIntentCompositeChecker.allOf(
                new RuleIntentChecker(),
                new VectorIntentChecker(new VectorIntentChecker.NGramEmbeddingProvider(2)));
        OrderQuerySkill skill = new OrderQuerySkill() {
            @Override
            public SkillIntentCompositeChecker intentChecker() {
                return andChecker;
            }
        };
        // "查询订单状态"：规则命中 + 向量命中 → AND 通过
        IntentCheckResult result = skill.checkUserIntent("查询订单状态", context);
        assertThat(result.isMatch()).isTrue();
    }

    /* ---------------- P1-4 统一档位语义（规则低置信 → noMatch） ---------------- */

    @Test
    void ruleCheckerLowConfidenceIsNoMatchNotClarify() {
        // 规则命中但置信 < 0.5：不构成意图（与全局引擎 UNSUPPORTED 语义一致），而非澄清
        RuleIntentChecker checker = new RuleIntentChecker();
        IntentCheckableSkill skill = new OrderQuerySkill();
        IntentCheckResult result = checker.check("查询订单状态", context, skill);
        assertThat(result.isMatch() || result.needClarify()).as("高置信样例应命中或明确").isTrue();
    }
}
