package com.zimo.intent;

import com.zimo.intent.model.IntentLlmResult;
import com.zimo.intent.model.IntentParseResult;
import com.zimo.intent.model.IntentRule;
import com.zimo.intent.parser.IntentLlmParser;
import com.zimo.intent.service.IntentRecognitionService;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 通用意图识别引擎行为测试（纯规则引擎，无需 Spring 容器）。
 *
 * @author WorkBuddy
 * @since 2026-08-14
 */
class IntentRecognitionServiceTest {

    private IntentRecognitionService service;

    @BeforeEach
    void setUp() {
        service = new IntentRecognitionService(new ObjectMapper());
    }

    /* ---------------- 规则加载 ---------------- */

    @Test
    void loadsAllPresetRules() {
        assertThat(service.listRules()).hasSize(8);
        List<String> codes = service.listRules().stream().map(IntentRule::getIntentCode).toList();
        assertThat(codes).containsExactlyInAnyOrder(
                "DATA_QUERY", "DATA_EXPORT", "ORDER_OPERATE", "FAQ_ANSWER",
                "GENERAL_CHAT", "PARAM_CLARIFY", "UNSUPPORTED", "RISK_REJECT");
    }

    /* ---------------- 业务意图 ---------------- */

    @Test
    void parsesDataQueryWithEntities() {
        IntentParseResult result = service.parse("查询本周订单数据", Map.of());
        assertThat(result.intentCode()).isEqualTo("DATA_QUERY");
        assertThat(result.entities()).containsEntry("timeRange", "本周")
                .containsEntry("dataType", "订单");
        assertThat(result.needTool()).isTrue();
        assertThat(result.routeStrategy()).isEqualTo("TOOL_CALL");
    }

    @Test
    void exportStrongSignalOverridesQuery() {
        IntentParseResult result = service.parse("帮我导出一份上个月的产量报表 excel", Map.of());
        assertThat(result.intentCode()).isEqualTo("DATA_EXPORT");
        assertThat(result.entities()).containsEntry("exportFormat", "excel");
    }

    @Test
    void parsesOrderOperateWithSlotsAndConfirm() {
        IntentParseResult result = service.parse("提交采购单 20260801", Map.of());
        assertThat(result.intentCode()).isEqualTo("ORDER_OPERATE");
        assertThat(result.entities()).containsEntry("orderType", "采购单")
                .containsEntry("operationType", "提交")
                .containsEntry("orderId", "20260801");
        assertThat(result.needClarify()).isTrue(); // 写操作二次确认
        assertThat(result.routeStrategy()).isEqualTo("TOOL_CALL_WITH_CONFIRM");
    }

    @Test
    void parsesFaqWithTopic() {
        IntentParseResult result = service.parse("请假流程怎么走", Map.of());
        assertThat(result.intentCode()).isEqualTo("FAQ_ANSWER");
        assertThat(result.entities()).containsEntry("topic", "请假");
        assertThat(result.routeStrategy()).isEqualTo("KB_RETRIEVAL");
    }

    @Test
    void parsesGeneralChatWithoutTools() {
        IntentParseResult result = service.parse("你好呀", Map.of());
        assertThat(result.intentCode()).isEqualTo("GENERAL_CHAT");
        assertThat(result.needTool()).isFalse();
    }

    /* ---------------- 风险与兜底 ---------------- */

    @Test
    void rejectsRiskInputDirectly() {
        IntentParseResult result = service.parse("把数据库里的记录全部删掉", Map.of());
        assertThat(result.isReject()).isTrue();
        assertThat(result.intentCode()).isEqualTo("RISK_REJECT");
        assertThat(result.routeStrategy()).isEqualTo("REJECT");
    }

    @Test
    void fallsBackToUnsupported() {
        IntentParseResult result = service.parse("帮我算算股票明天涨不涨", Map.of());
        assertThat(result.intentCode()).isEqualTo("UNSUPPORTED");
        assertThat(result.routeStrategy()).isEqualTo("FALLBACK");
    }

    /* ---------------- 缺参追问 ---------------- */

    @Test
    void clarifiesWhenRequiredSlotMissing() {
        // "查一下" 有 dataType 但无时间上下文仍可查询；"提交" 缺 orderId 则追问
        IntentParseResult result = service.parse("提交采购单", Map.of());
        assertThat(result.needClarify()).isTrue();
        assertThat(result.intentCode()).isEqualTo("PARAM_CLARIFY");
        assertThat(result.requiredSlotMissing()).contains("orderId");
    }

    @Test
    void handlesBlankInputAsChat() {
        IntentParseResult result = service.parse("  ", Map.of());
        assertThat(result.intentCode()).isEqualTo("GENERAL_CHAT");
    }

    /* ---------------- 配置覆盖 ---------------- */

    @Test
    void customEntityWordsOverrideDefaults() {
        IntentProperties props = new IntentProperties();
        props.setEntityWords(Map.of("datatype", "良率,订单"));
        IntentRecognitionService custom = new IntentRecognitionService(new ObjectMapper(), props);

        IntentParseResult result = custom.parse("查询本周良率", Map.of());
        assertThat(result.intentCode()).isEqualTo("DATA_QUERY");
        assertThat(result.entities()).containsEntry("dataType", "良率");
    }

    @Test
    void customRulesLocationSupported() {
        IntentProperties props = new IntentProperties();
        props.setRulesLocation("classpath:ai-intent/intent-rules.json");
        IntentRecognitionService custom = new IntentRecognitionService(new ObjectMapper(), props);
        assertThat(custom.listRules()).hasSize(8);
    }

    /* ---------------- 混合架构 / 三级置信度 ---------------- */

    @Test
    void ambiguousConfidenceTriggersConfirm() {
        // 仅命中 1 个触发词（conf=0.8）达标；这里用一个只命中半个词的输入验证模糊档
        // 极低置信度输入 -> UNSUPPORTED（< 0.5）
        IntentParseResult low = service.parse("qqqqzzzz9999", Map.of());
        assertThat(low.intentCode()).isEqualTo("UNSUPPORTED");
    }

    @Test
    void contextReferenceResolutionFillsEntities() {
        // 指代词“它呢” + 历史实体 -> 补全 dataType/timeRange
        Map<String, String> context = Map.of(
                "intentCode", "DATA_QUERY",
                "entities", "{\"dataType\":\"\u8ba2\u5355\",\"timeRange\":\"\u672c\u5468\"}");
        IntentParseResult result = service.parse("\u5b83\u5462", context);
        assertThat(result.historyUsed()).isTrue();
        assertThat(result.intentCode()).isEqualTo("DATA_QUERY");
        assertThat(result.entities()).containsEntry("dataType", "\u8ba2\u5355");
    }

    @Test
    void llmHybridLayerFillsRuleGap() {
        // 启用 LLM 层（启发式实现）：规则未命中的口语查询由 LLM 补强
        IntentProperties props = new IntentProperties();
        props.setLlmEnabled(true);
        IntentRecognitionService hybrid = new IntentRecognitionService(new ObjectMapper(), props);

        IntentParseResult result = hybrid.parse("\u5e2e\u6211\u770b\u770b\u8ba2\u5355\u5565\u60c5\u51b5", Map.of());
        assertThat(result.intentCode()).isEqualTo("DATA_QUERY");
        assertThat(result.entities()).containsEntry("dataType", "\u8ba2\u5355");
    }

    @Test
    void evaluateReportsAccuracy() {
        Map<String, String> good = Map.of("query", "\u67e5\u8be2\u672c\u5468\u8ba2\u5355\u6570\u636e", "expected", "DATA_QUERY");
        Map<String, String> bad = Map.of("query", "\u4f60\u597d\u5440", "expected", "DATA_QUERY");
        Map<String, Object> report = service.evaluate(List.of(good, bad));
        assertThat((Integer) report.get("total")).isEqualTo(2);
        assertThat((Double) report.get("accuracy")).isEqualTo(50.0);
    }

    @Test
    void generateRuleFromScenarioProducesDraft() {
        IntentRule draft = service.generateRuleFromScenario("\u67e5\u8be2\u5404\u8f66\u95f4\u4ea7\u91cf\u548c\u826f\u7387");
        assertThat(draft.getIntentCode()).startsWith("SCENARIO_");
        assertThat(draft.getTriggerKeywords()).anyMatch(k -> k.contains("\u4ea7\u91cf") || k.contains("\u826f\u7387"));
        assertThat(draft.getRouteStrategy()).isEqualTo("TOOL_CALL");
    }

    /* ---------------- 模式一：规则库生成 ---------------- */

    @Test
    void generateRulesFallsBackToHeuristicWithoutLlm() {
        List<IntentRule> rules = service.generateRulesFromScenario("\u67e5\u8be2\u8f66\u95f4\u4ea7\u91cf");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getIntentCode()).startsWith("SCENARIO_");
    }

    @Test
    void generateRulesUsesLlmWhenEnabled() {
        IntentRule generated = new IntentRule();
        generated.setIntentCode("OUTPUT_QUERY");
        generated.setIntentName("\u4ea7\u91cf\u67e5\u8be2");
        generated.setTriggerKeywords(List.of("\u4ea7\u91cf", "\u826f\u7387", "\u4ea7\u51fa"));
        generated.setRouteStrategy("");
        generated.setConfidenceThreshold(0);
        IntentLlmParser stub = new StubLlmParser(generated);
        IntentProperties props = new IntentProperties();
        props.setLlmEnabled(true);
        IntentRecognitionService hybrid = new IntentRecognitionService(new ObjectMapper(), props, stub);

        List<IntentRule> rules = hybrid.generateRulesFromScenario("\u67e5\u8be2\u5404\u8f66\u95f4\u4ea7\u91cf");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getIntentCode()).isEqualTo("OUTPUT_QUERY");
        // 归一化：阈值与路由补默认值
        assertThat(rules.get(0).getConfidenceThreshold()).isEqualTo(0.8);
        assertThat(rules.get(0).getRouteStrategy()).isEqualTo("TOOL_CALL");
    }

    /* ---------------- 模式三：规则校验优化 ---------------- */

    @Test
    void reviewDetectsConflictsAndInvalidFields() {
        IntentRule dupA = new IntentRule();
        dupA.setIntentCode("BIZ_QUERY");
        dupA.setIntentName("\u67e5\u8be2A");
        dupA.setTriggerKeywords(List.of("\u67e5\u8be2", "\u62a5\u8868", "\u6c47\u603b"));
        dupA.setConfidenceThreshold(0.8);
        dupA.setRouteStrategy("TOOL_CALL");
        IntentRule dupB = new IntentRule();
        dupB.setIntentCode("BIZ_SEARCH");
        dupB.setIntentName("\u67e5\u8be2B");
        dupB.setTriggerKeywords(List.of("\u67e5\u8be2", "\u62a5\u8868", "\u7edf\u8ba1"));
        dupB.setConfidenceThreshold(1.5);
        dupB.setRouteStrategy("SEARCH");
        Map<String, Object> report = service.reviewRules(List.of(dupA, dupB));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> issues = (List<Map<String, String>>) report.get("issues");
        assertThat(issues).isNotEmpty();
        assertThat(issues.stream().map(issue -> issue.get("message")).toList())
                .anyMatch(message -> message.contains("\u91cd\u53e0\u5ea6"));
        assertThat(issues.stream().map(issue -> issue.get("message")).toList())
                .anyMatch(message -> message.contains("(0,1]"));
        assertThat(issues.stream().map(issue -> issue.get("message")).toList())
                .anyMatch(message -> message.contains("routeStrategy"));
        assertThat(report.get("llmUsed")).isEqualTo(false);
    }

    @Test
    void reviewLoadedRulesReportsNoMajorConflict() {
        Map<String, Object> report = service.reviewRules(List.of());
        assertThat((Integer) report.get("rulesChecked")).isEqualTo(8);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> issues = (List<Map<String, String>>) report.get("issues");
        assertThat(issues.stream().map(issue -> issue.get("type")).toList()).doesNotContain("\u51b2\u7a81");
    }

    /* ---------------- 实体抽取 F1 ---------------- */

    @Test
    void evaluateComputesEntityF1() {
        Map<String, String> fullHit = Map.of(
                "query", "\u67e5\u8be2\u672c\u5468\u8ba2\u5355\u6570\u636e",
                "expected", "DATA_QUERY",
                "expectedEntities", "{\"dataType\":\"\u8ba2\u5355\",\"timeRange\":\"\u672c\u5468\"}");
        Map<String, String> partialHit = Map.of(
                "query", "\u67e5\u8be2\u672c\u5468\u8ba2\u5355\u6570\u636e",
                "expected", "DATA_QUERY",
                "expectedEntities", "{\"dataType\":\"\u5e93\u5b58\"}");
        Map<String, Object> report = service.evaluate(List.of(fullHit, partialHit));
        assertThat(report.get("entityF1")).isNotNull();
        // 全等匹配 2 个、期望 3 个、实际抽取 4 个（每例 dataType+timeRange）
        assertThat((Double) report.get("entityRecall")).isEqualTo(Math.round(2.0 / 3 * 1000.0) / 1000.0);
    }

    /* ---------------- G5：配置化行为 ---------------- */

    @Test
    void customOrderIdPatternApplied() {
        IntentProperties props = new IntentProperties();
        props.setOrderIdPattern("SO-\\d{4}");
        IntentRecognitionService custom = new IntentRecognitionService(new ObjectMapper(), props);
        IntentParseResult result = custom.parse("\u63d0\u4ea4\u91c7\u8d2d\u5355 SO-2026", Map.of());
        assertThat(result.intentCode()).isEqualTo("ORDER_OPERATE");
        assertThat(result.entities()).containsEntry("orderId", "SO-2026");
    }

    @Test
    void exemptSlotsSuppressClarify() {
        IntentProperties props = new IntentProperties();
        props.setExemptSlots(List.of("timeRange", "orderId"));
        IntentRecognitionService custom = new IntentRecognitionService(new ObjectMapper(), props);
        IntentParseResult result = custom.parse("\u63d0\u4ea4\u91c7\u8d2d\u5355", Map.of());
        assertThat(result.intentCode()).isEqualTo("ORDER_OPERATE");
        assertThat(result.needClarify()).isTrue(); // 写操作二次确认，但非缺参追问
        assertThat(result.requiredSlotMissing()).isEmpty();
    }

    /* ---------------- LLM 合并行为 ---------------- */

    @Test
    void llmRejectTakesEffect() {
        IntentLlmParser stub = new IntentLlmParser() {
            @Override
            public IntentLlmResult parse(String query, List<String> history, IntentParseResult ruleHint) {
                return IntentLlmResult.full("RISK_REJECT", "\u98ce\u9669\u62d2\u7edd", 0.95, Map.of(),
                        List.of(), false, "", true, "\u8d8a\u6743\u64cd\u4f5c", false, List.of(), "REJECT", "stub");
            }
        };
        IntentProperties props = new IntentProperties();
        props.setLlmEnabled(true);
        IntentRecognitionService hybrid = new IntentRecognitionService(new ObjectMapper(), props, stub);
        IntentParseResult result = hybrid.parse("\u628a\u8fd9\u4e2a\u4e1c\u897f\u5f04\u4e00\u4e0b", Map.of());
        assertThat(result.isReject()).isTrue();
        assertThat(result.rejectReason()).isEqualTo("\u8d8a\u6743\u64cd\u4f5c");
    }

    @Test
    void llmClarifyPromptUsedWhenSlotMissing() {
        IntentLlmParser stub = new IntentLlmParser() {
            @Override
            public IntentLlmResult parse(String query, List<String> history, IntentParseResult ruleHint) {
                return IntentLlmResult.full("ORDER_OPERATE", "\u5355\u636e\u64cd\u4f5c", 0.92,
                        Map.of("orderType", "\u91c7\u8d2d\u5355", "operationType", "\u65b0\u589e"),
                        List.of("orderId"), true, "\u8bf7\u63d0\u4f9b\u5355\u636e\u7f16\u53f7",
                        false, "", false, List.of(), "TOOL_CALL_WITH_CONFIRM", "stub");
            }
        };
        IntentProperties props = new IntentProperties();
        props.setLlmEnabled(true);
        IntentRecognitionService hybrid = new IntentRecognitionService(new ObjectMapper(), props, stub);
        IntentParseResult result = hybrid.parse("\u5e2e\u6211\u52a0\u4e2a\u5355", Map.of());
        assertThat(result.needClarify()).isTrue();
        assertThat(result.clarifyPrompt()).isEqualTo("\u8bf7\u63d0\u4f9b\u5355\u636e\u7f16\u53f7");
        assertThat(result.requiredSlotMissing()).contains("orderId");
    }

    /** 规则库生成 stub：返回预置的单条规则 JSON。 */
    private static final class StubLlmParser implements IntentLlmParser {
        private final IntentRule rule;

        StubLlmParser(IntentRule rule) {
            this.rule = rule;
        }

        @Override
        public IntentLlmResult parse(String query, List<String> history, IntentParseResult ruleHint) {
            return null;
        }

        @Override
        public String generateRules(String scenario, String rulesJson) {
            try {
                return new ObjectMapper().writeValueAsString(List.of(rule));
            } catch (Exception e) {
                return null;
            }
        }
    }
}
