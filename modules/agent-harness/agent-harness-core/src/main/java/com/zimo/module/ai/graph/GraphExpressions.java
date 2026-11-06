package com.zimo.module.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

/**
 * 图节点的表达式与模板支持（条件求值 / {@code {{var}}} 渲染 / JSON 参数解析）。
 *
 * <p>为什么要从节点执行器里抽出来：这些是<b>纯计算</b>，不依赖任何节点类型，
 * 也不该随节点类型增加而膨胀。混在 {@link GraphNodeExecutor} 里会让后者同时承担
 * "节点能力" 与 "表达式求值"两种变化原因，最终长成难以维护的大类。</p>
 *
 * <p>求值上下文对外暴露 {@code #ctx}/{@code #input} 两个变量，与既有
 * {@code WfWorkflowEngine} 的表达式口径保持一致 —— 存量图配置不需要改写。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public final class GraphExpressions {

    /** SpEL 解析器，线程安全可复用。 */
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    private final ObjectMapper objectMapper;

    /**
     * 构造表达式支持。
     *
     * @param objectMapper JSON 映射器，用于解析参数 JSON
     */
    public GraphExpressions(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 求值条件表达式。
     *
     * @param expression SpEL 表达式；为空视为 {@code true}
     * @param state 运行状态
     * @return 布尔结果
     */
    public boolean test(String expression, GraphState state) {
        if (!StringUtils.hasText(expression)) {
            return true;
        }
        Object value = evaluate(expression, state);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return value != null;
    }

    /**
     * 渲染 {@code {{var}}} / {@code {{ctx.key}}} / {@code {{input.key}}} 模板。
     *
     * @param template 模板文本
     * @param state 运行状态
     * @return 渲染结果；变量解析失败时保留原变量名，便于发现配置错误
     */
    public String render(String template, GraphState state) {
        if (!StringUtils.hasText(template)) {
            return "";
        }
        String result = template;
        int index = result.indexOf("{{");
        while (index >= 0) {
            int end = result.indexOf("}}", index);
            if (end < 0) {
                break;
            }
            String expression = result.substring(index + 2, end).trim();
            String value = String.valueOf(resolve(expression, state));
            result = result.substring(0, index) + value + result.substring(end + 2);
            index = result.indexOf("{{");
        }
        return result;
    }

    /**
     * 解析 JSON 对象字符串。
     *
     * @param json JSON 文本
     * @return 解析结果；非法或非对象时返回空 Map（交由节点逻辑决定行为）
     */
    public Map<String, Object> parseObject(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node != null && node.isObject()) {
                return objectMapper.convertValue(node,
                        new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
            }
        } catch (Exception ignored) {
            // 非 JSON 输入按空参数处理
        }
        return Map.of();
    }

    /** 执行 SpEL 表达式，注入 ctx 与 input 变量。 */
    private Object evaluate(String expression, GraphState state) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.addPropertyAccessor(new MapAccessor());
        context.setVariable("ctx", stateContext(state));
        context.setVariable("input", state.input());
        return PARSER.parseExpression(expression).getValue(context);
    }

    /** 解析模板变量，表达式异常时原样返回变量名。 */
    private Object resolve(String expression, GraphState state) {
        try {
            if (expression.startsWith("ctx.")) {
                return evaluate("#" + expression, state);
            }
            if (expression.startsWith("input.")) {
                return evaluate("#input." + expression.substring("input.".length()), state);
            }
            Object direct = evaluate(expression, state);
            return direct != null ? direct : "";
        } catch (Exception e) {
            return expression;
        }
    }

    /** 构造表达式可访问的上下文快照。 */
    private Map<String, Object> stateContext(GraphState state) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("input", state.input());
        map.put("nodeOutputs", state.outputs());
        map.put("loopIndex", state.loopIndex());
        map.put("lastError", state.lastError());
        map.put("executedNodes", state.completedNodes());
        return map;
    }
}
