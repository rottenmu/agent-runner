package com.zimo.intent.parser;

import com.zimo.intent.model.IntentLlmResult;
import com.zimo.intent.model.IntentParseResult;
import java.util.List;
import java.util.Map;

/**
 * 启发式 LLM 模拟实现：不依赖外部模型，面向常见口语化/模糊句式做兜底识别。
 *
 * <p>用于无 LLM 环境的默认装配与测试；生产环境应替换为真实 LLM 实现
 * （HTTP/SpringAI），并按 {@link IntentProperties#getLlmPrompt()} 构造系统提示词。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-14
 */
public class HeuristicIntentLlmParser implements IntentLlmParser {

    private static final double AMBIGUOUS_CONFIDENCE = 0.6;

    @Override
    public IntentLlmResult parse(String query, List<String> history, IntentParseResult ruleHint) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String text = query.trim();
        String lower = text.toLowerCase();

        // 口语化查询句式 → 数据查询
        if (isQueryPattern(lower)) {
            Map<String, String> entities = Map.of();
            if (containsAny(lower, "订单", "单子")) {
                entities = Map.of("dataType", "订单");
            } else if (containsAny(lower, "产量", "生产")) {
                entities = Map.of("dataType", "产量");
            } else if (containsAny(lower, "库存")) {
                entities = Map.of("dataType", "库存");
            } else if (containsAny(lower, "设备")) {
                entities = Map.of("dataType", "设备");
            }
            return IntentLlmResult.of("DATA_QUERY", "业务数据查询", 0.7, entities, "启发式：口语化查询句式");
        }

        // 口语化导出 → 数据导出
        if (containsAny(lower, "发个", "拉一下", "整份", "要份", "导出表格")) {
            return IntentLlmResult.of("DATA_EXPORT", "数据导出", 0.7, Map.of(), "启发式：口语化导出");
        }

        // 口语化单据操作 → ORDER_OPERATE（模糊档，交二次确认）
        if (containsAny(lower, "帮我加", "补个单", "弄个", "退掉", "撤了", "批一下")) {
            return IntentLlmResult.of("ORDER_OPERATE", "单据操作", 0.65, Map.of(), "启发式：口语化单据操作");
        }

        // 模糊咨询 → 知识库问答（0.6 模糊档）
        if (containsAny(lower, "咋办", "啥流程", "怎么做", "给讲讲", "了解下")) {
            return IntentLlmResult.of("FAQ_ANSWER", "业务知识问答", 0.6, Map.of(), "启发式：模糊咨询");
        }

        // 无任何强信号 → 交由规则层兜底（返回 null）
        return null;
    }

    private boolean isQueryPattern(String lower) {
        return containsAny(lower, "看看", "多少", "给我查", "查查", "啥情况", "什么情况", "咋样", "几个", "几台");
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
