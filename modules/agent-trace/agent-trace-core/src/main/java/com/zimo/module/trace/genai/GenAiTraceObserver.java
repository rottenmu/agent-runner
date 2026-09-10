package com.zimo.module.trace.genai;

import com.zimo.framework.ai.observ.TraceCollector;
import com.zimo.framework.ai.observ.TraceObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GenAI 链路观测实现（agent-trace 核心）。
 *
 * <p>实现 {@link TraceObserver} 并注册到 {@link TraceCollector}：
 * <ul>
 *   <li>onBegin → 建根 span {@code invoke_agent <agentName>}（AGENT kind，附 session/agent/intent 属性）</li>
 *   <li>onStep → 按 stepType 建子 span（tool→{@code execute_tool <name>} TOOL；agent/plan→STEP；
 *       输入/输出/耗时写 gen_ai.* 属性）</li>
 *   <li>onEnd → 收尾根 span（status/prompt/response/tokens），整条链路交给 {@link GenAiSpanExporter} 导出</li>
 * </ul>
 * span 树按 traceId 聚合，exported 后清理。</p>
 */
public class GenAiTraceObserver implements TraceObserver {

    private final GenAiSpanExporter exporter;
    private final Map<String, List<GenAiSpan>> traceSpans = new ConcurrentHashMap<>();
    private final Map<String, GenAiSpan> rootSpans = new ConcurrentHashMap<>();

    public GenAiTraceObserver(GenAiSpanExporter exporter) {
        this.exporter = exporter;
    }

    @Override
    public void onBegin(String traceId, String sessionId, String agentId, String agentName,
                        String intent, String triggerType) {
        if (traceId == null) {
            return;
        }
        String spanId = spanId(traceId, "root");
        GenAiSpan root = new GenAiSpan(traceId, spanId,
                "invoke_agent " + nullSafe(agentName), GenAiSpanKind.AGENT)
                .attribute(GenAiAttributeNames.SESSION_ID, sessionId)
                .attribute(GenAiAttributeNames.AGENT_NAME, agentName)
                .attribute(GenAiAttributeNames.INTENT, intent)
                .attribute(GenAiAttributeNames.TRIGGER_TYPE, triggerType)
                .attribute(GenAiAttributeNames.TRACE_ID, traceId)
                .attribute("agent.id", agentId);
        rootSpans.put(traceId, root);
        traceSpans.computeIfAbsent(traceId, k -> new ArrayList<>()).add(root);
    }

    @Override
    public void onStep(String traceId, int seq, String stepType, String name,
                       String inputJson, String outputJson, long latencyMs, String status) {
        if (traceId == null || !traceSpans.containsKey(traceId)) {
            return;
        }
        GenAiSpanKind kind = GenAiSpanKind.fromStepType(stepType);
        String spanName = kind == GenAiSpanKind.TOOL ? "execute_tool " + nullSafe(name) : nullSafe(name);
        GenAiSpan span = new GenAiSpan(traceId, spanId(traceId, "s" + seq), spanName, kind)
                .attribute(GenAiAttributeNames.TOOL_NAME, name)
                .attribute(GenAiAttributeNames.TOOL_INPUT, truncate(inputJson, 500))
                .attribute(GenAiAttributeNames.TOOL_OUTPUT, truncate(outputJson, 500));
        // 已知模型字段尽量按语义约定打点（token 数从输入/输出 JSON 无法确定时省略）
        span.end(status == null ? "ok" : status);
        traceSpans.get(traceId).add(span);
    }

    @Override
    public void onEnd(String traceId, String status, String prompt, String response,
                      int tokens, long latencyMs) {
        GenAiSpan root = rootSpans.remove(traceId);
        List<GenAiSpan> spans = traceSpans.remove(traceId);
        if (root == null || spans == null || spans.isEmpty()) {
            return;
        }
        root.attribute(GenAiAttributeNames.PROMPT, truncate(prompt, 1000))
                .attribute(GenAiAttributeNames.COMPLETION, truncate(response, 1000))
                .attribute(GenAiAttributeNames.USAGE_OUTPUT_TOKENS, tokens);
        root.end(status == null ? "success" : status);
        if (exporter != null) {
            exporter.export(spans);
        }
    }

    private static String spanId(String traceId, String suffix) {
        String base = traceId.replaceAll("[^a-zA-Z0-9_-]", "");
        String s = base.length() > 10 ? base.substring(base.length() - 10) : base;
        return s + "-" + suffix;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static String nullSafe(String value) {
        return value == null ? "unknown" : value;
    }
}
