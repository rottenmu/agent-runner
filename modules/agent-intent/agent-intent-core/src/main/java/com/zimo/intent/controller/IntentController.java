package com.zimo.intent.controller;

import com.zimo.framework.common.ApiResponse;
import com.zimo.framework.common.validation.ValidationUtil;
import com.zimo.intent.model.IntentParseResult;
import com.zimo.intent.IntentProperties;
import com.zimo.intent.service.IntentRecognitionService;
import com.zimo.intent.model.IntentRule;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用意图识别接口：实时解析用户输入 + 规则集查询 + 规则热更新。
 *
 * @author WorkBuddy
 * @since 2026-08-14
 */
@RestController
@RequestMapping("/api/biz/intent")
public class IntentController {

    private final IntentRecognitionService intentService;

    public IntentController(IntentRecognitionService intentService) {
        this.intentService = intentService;
    }

    /**
     * 实时意图解析：输出标准 JSON 结构（意图编码/置信度/实体/缺参/工具/追问/拒绝/路由）。
     * context 支持 JSON 对象：{"intentCode":"DATA_QUERY","entities":{...},"history":[...]}，
     * 用于多轮上下文指代消解。
     */
    @PostMapping("/parse")
    public ApiResponse<IntentParseResult> parse(@RequestBody Map<String, String> body) {
        String query = body == null ? null : body.get("query");
        ValidationUtil.requireText(query, "query 不能为空");
        Map<String, String> context = new java.util.LinkedHashMap<>();
        String contextJson = body.get("context");
        if (contextJson != null && !contextJson.isBlank()) {
            try {
                cn.hutool.json.JSONObject parsed = cn.hutool.json.JSONUtil.parseObj(contextJson);
                parsed.forEach((key, value) -> context.put(key, value == null ? null : String.valueOf(value)));
            } catch (Exception e) {
                context.put("rawContext", contextJson);
            }
        }
        return ApiResponse.ok(intentService.parse(query, context));
    }

    /**
     * 规则集查询：返回全部已加载的意图规则。
     */
    @GetMapping("/rules")
    public ApiResponse<List<IntentRule>> rules() {
        return ApiResponse.ok(intentService.listRules());
    }

    /**
     * 规则集重载（热更新）。
     */
    @PostMapping("/rules/reload")
    public ApiResponse<Void> reload() {
        intentService.reload();
        return ApiResponse.ok(null);
    }

    /**
     * 新增意图规则（编码全局唯一 + 触发词冲突校验）。
     */
    @PostMapping("/rules")
    public ApiResponse<IntentRule> addRule(@RequestBody IntentRule rule) {
        return ApiResponse.ok(intentService.addRule(rule));
    }

    /**
     * 编辑意图规则（按编码定位，编码不可变更）。
     */
    @PutMapping("/rules/{code}")
    public ApiResponse<IntentRule> updateRule(@PathVariable String code, @RequestBody IntentRule rule) {
        return ApiResponse.ok(intentService.updateRule(code, rule));
    }

    /**
     * 删除意图规则。
     */
    @DeleteMapping("/rules/{code}")
    public ApiResponse<Void> deleteRule(@PathVariable String code) {
        intentService.deleteRule(code);
        return ApiResponse.ok(null);
    }

    /**
     * 模式一：根据业务场景描述生成意图规则草稿（可直接入库或人工编辑后入库）。
     */
    @PostMapping("/rules/generate")
    public ApiResponse<IntentRule> generateRule(@RequestBody Map<String, String> body) {
        String scenario = body == null ? null : body.get("scenario");
        ValidationUtil.requireText(scenario, "scenario 不能为空");
        return ApiResponse.ok(intentService.generateRuleFromScenario(scenario));
    }

    /**
     * 模式一（完整规则库）：根据业务场景描述生成可入库的意图规则列表。
     * llm-enabled=true 且 LLM 可用时由大模型生成完整规则库，否则降级为启发式单条草稿。
     * 入参：{"scenario": "业务场景描述"}；返回生成的规则列表。
     */
    @PostMapping("/rules/generate/library")
    public ApiResponse<List<IntentRule>> generateRuleLibrary(@RequestBody Map<String, String> body) {
        String scenario = body == null ? null : body.get("scenario");
        ValidationUtil.requireText(scenario, "scenario 不能为空");
        return ApiResponse.ok(intentService.generateRulesFromScenario(scenario));
    }

    /**
     * 模式三：对规则集执行冲突校验、补全与优化迭代。
     * 入参：{} 校验当前已加载规则；{"rules":[...]} 校验传入规则集。
     * 返回：rulesChecked/issues（含 intentCode/type/message）/suggestions/optimizedRules/llmUsed。
     */
    @PostMapping("/rules/review")
    public ApiResponse<Map<String, Object>> reviewRules(@RequestBody(required = false) Map<String, Object> body) {
        List<IntentRule> rules = parseReviewRules(body == null ? null : body.get("rules"));
        return ApiResponse.ok(intentService.reviewRules(rules));
    }

    /**
     * 批量评估：输入 [{query, expected, expectedEntities}] 用例集，输出每例结果与整体指标
     * （意图准确率 / 风险拦截率 / 缺参召回率 / 实体抽取 F1）。
     * expectedEntities 支持 JSON 对象或对象字符串，按槽位名+槽位值全等匹配。
     */
    @PostMapping("/evaluate")
    public ApiResponse<Map<String, Object>> evaluate(@RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("cases");
        List<Map<String, String>> cases = new java.util.ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, String> entry = new java.util.LinkedHashMap<>();
                    map.forEach((k, v) -> entry.put(String.valueOf(k), toPlainValue(v)));
                    cases.add(entry);
                }
            }
        }
        return ApiResponse.ok(intentService.evaluate(cases));
    }

    /** 调试：返回当前配置快照（entityWords / entityIntents / 置信度参数）。 */
    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        IntentProperties props = intentService.properties();
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("enabled", props.isEnabled());
        snapshot.put("rulesLocation", props.getRulesLocation());
        snapshot.put("baseConfidence", props.getBaseConfidence());
        snapshot.put("entityWords", props.getEntityWords());
        snapshot.put("entityIntents", props.getEntityIntents());
        return ApiResponse.ok(snapshot);
    }

    /**
     * 路由统计快照：解析量 / 意图分布 / 拒绝·追问·确认次数 / LLM 调用量。
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(intentService.getStats());
    }

    /**
     * 重置路由统计。
     */
    @PostMapping("/stats/reset")
    public ApiResponse<Void> resetStats() {
        intentService.resetStats();
        return ApiResponse.ok(null);
    }

    /* ---------------- 内部辅助 ---------------- */

    /** 嵌套对象/数组值序列化为 JSON 字符串，标量值直接转字符串（供评估器解析 expectedEntities）。 */
    private String toPlainValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return cn.hutool.json.JSONUtil.toJsonStr(value);
        }
        return String.valueOf(value);
    }

    /** 解析校验接口的 rules 入参：支持 JSON 数组，解析失败或为空时返回空列表（按已加载规则校验）。 */
    private List<IntentRule> parseReviewRules(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        try {
            return cn.hutool.json.JSONUtil.toList(cn.hutool.json.JSONUtil.parseArray(list), IntentRule.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}
