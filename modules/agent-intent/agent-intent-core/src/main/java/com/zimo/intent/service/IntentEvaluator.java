package com.zimo.intent.service;

import com.zimo.intent.model.IntentParseResult;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图识别批量评估器：对用例集执行解析并输出整体指标。
 *
 * <p>指标口径：意图准确率（intentCode 精确匹配）、风险拦截率（期望 RISK_REJECT
 * 的用例被拒绝比例）、缺参召回率（期望追问的用例触发追问比例）、实体抽取
 * 精确率/召回率/F1（按槽位名+槽位值全等匹配，micro 平均，仅统计声明了
 * expectedEntities 的用例）。用例字段：query、expected、expectedEntities（JSON 对象字符串）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-14
 */
public class IntentEvaluator {

    /** 意图解析函数签名：供评估器回调解耦引擎实现。 */
    @FunctionalInterface
    public interface ParseFn {
        IntentParseResult parse(String query, Map<String, String> context);
    }

    private final ParseFn parseFn;
    private final ObjectMapper objectMapper;

    public IntentEvaluator(ParseFn parseFn, ObjectMapper objectMapper) {
        this.parseFn = parseFn;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /**
     * 批量评估。
     *
     * @param cases 用例列表，每条含 query、expected、可选 expectedEntities；非法用例跳过
     * @return 指标报告：total/hit/accuracy/riskRecall/clarifyRecall/entityPrecision/entityRecall/entityF1/details
     */
    public Map<String, Object> evaluate(List<Map<String, String>> cases) {
        Metrics metrics = new Metrics();
        List<Map<String, Object>> details = new ArrayList<>();
        for (Map<String, String> testCase : cases == null ? List.<Map<String, String>>of() : cases) {
            String query = testCase.get("query");
            String expected = testCase.get("expected");
            if (StrUtil.isBlank(query) || StrUtil.isBlank(expected)) {
                continue;
            }
            IntentParseResult result = parseFn.parse(query, Map.of());
            accumulate(metrics, expected, result, expectedEntities(testCase), details, query);
        }
        return report(metrics, details);
    }

    /* ---------------- 内部实现 ---------------- */

    /** 单用例指标累计：意图命中 / 风险 / 追问 / 实体匹配。 */
    private void accumulate(Metrics m, String expected, IntentParseResult result,
                            Map<String, String> expectedEntities, List<Map<String, Object>> details, String query) {
        boolean isHit = expected.equals(result.intentCode());
        m.total++;
        if (isHit) {
            m.hit++;
        }
        if ("RISK_REJECT".equals(expected)) {
            m.riskTotal++;
            if (result.isReject()) {
                m.riskHit++;
            }
        }
        if ("PARAM_CLARIFY".equals(expected) || expected.endsWith("_CLARIFY")) {
            m.clarifyTotal++;
            if (result.needClarify()) {
                m.clarifyHit++;
            }
        }
        Double caseF1 = null;
        if (!expectedEntities.isEmpty()) {
            caseF1 = accumulateEntities(m, expectedEntities, result.entities());
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("query", query);
        detail.put("expected", expected);
        detail.put("actual", result.intentCode());
        detail.put("confidence", result.confidence());
        detail.put("hit", isHit);
        if (caseF1 != null) {
            detail.put("entityF1", caseF1);
        }
        details.add(detail);
    }

    /** 实体 micro 指标累计，返回单用例 F1（分母为零返回 null）。 */
    private Double accumulateEntities(Metrics m, Map<String, String> expected, Map<String, String> actual) {
        int tp = 0;
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            String actualValue = actual.get(entry.getKey());
            if (actualValue != null && actualValue.equals(entry.getValue())) {
                tp++;
            }
        }
        m.entityTp += tp;
        m.entityExpected += expected.size();
        m.entityActual += actual.size();
        if (expected.isEmpty() && actual.isEmpty()) {
            return null;
        }
        double precision = actual.isEmpty() ? 0 : (double) tp / actual.size();
        double recall = expected.isEmpty() ? 0 : (double) tp / expected.size();
        if (precision + recall == 0) {
            return 0.0;
        }
        return Math.round(2 * precision * recall / (precision + recall) * 1000.0) / 1000.0;
    }

    /** 组装指标报告；无实体标注用例时实体指标返回 null。 */
    private Map<String, Object> report(Metrics m, List<Map<String, Object>> details) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("total", m.total);
        report.put("hit", m.hit);
        report.put("accuracy", m.total == 0 ? 0 : Math.round(m.hit * 1000.0 / m.total) / 10.0);
        report.put("riskRecall", m.riskTotal == 0 ? null : Math.round(m.riskHit * 1000.0 / m.riskTotal) / 10.0);
        report.put("clarifyRecall", m.clarifyTotal == 0 ? null
                : Math.round(m.clarifyHit * 1000.0 / m.clarifyTotal) / 10.0);
        if (m.entityExpected > 0 || m.entityActual > 0) {
            double precision = m.entityActual == 0 ? 0 : (double) m.entityTp / m.entityActual;
            double recall = m.entityExpected == 0 ? 0 : (double) m.entityTp / m.entityExpected;
            report.put("entityPrecision", Math.round(precision * 1000.0) / 1000.0);
            report.put("entityRecall", Math.round(recall * 1000.0) / 1000.0);
            report.put("entityF1", precision + recall == 0 ? 0
                    : Math.round(2 * precision * recall / (precision + recall) * 1000.0) / 1000.0);
        } else {
            report.put("entityPrecision", null);
            report.put("entityRecall", null);
            report.put("entityF1", null);
        }
        report.put("details", details);
        return report;
    }

    /** 解析用例中的 expectedEntities（JSON 对象字符串），解析失败返回空 Map。 */
    private Map<String, String> expectedEntities(Map<String, String> testCase) {
        String raw = testCase.get("expectedEntities");
        if (StrUtil.isBlank(raw)) {
            return Map.of();
        }
        try {
            Map<String, String> parsed = objectMapper.readValue(raw, new TypeReference<>() {
            });
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 指标累计容器。 */
    private static final class Metrics {
        int total;
        int hit;
        int riskHit;
        int riskTotal;
        int clarifyHit;
        int clarifyTotal;
        int entityTp;
        int entityExpected;
        int entityActual;
    }
}
