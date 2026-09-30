package com.zimo.module.trace.genai;

import com.zimo.framework.ai.observ.TraceCollector;
import com.zimo.framework.ai.observ.TraceObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GenAI 链路观测实现（agent-trace 核心）。
 *
 * <p>实现 {@link TraceObserver} 并注册到 {@link TraceCollector}：
 * <ul>
 *   <li>onBegin → 建根 span {@code invoke_agent <agentName>}（AGENT kind，附 session/agent/intent 属性）</li>
 *   <li>onStep → 按 stepType 建子 span（经 {@link GenAiStepTypes} 归一化：
 *       tool_call→{@code execute_tool <name>} TOOL、model_call→{@code chat <model>} LLM、
 *       reasoning→STEP、retrieval→RETRIEVER；输入/输出/耗时写 gen_ai.* 属性）</li>
 *   <li>onEnd → 收尾根 span（status/prompt/response/tokens），整条链路交给
 *       {@link GenAiSpanExporter} 导出</li>
 * </ul>
 * span 树按 traceId 聚合，exported 后清理。</p>
 *
 * <h2>⚠️ 与 seq 顺序的关系</h2>
 * <p>{@code HarnessTraceMiddleware} 的每个 hook 都在 {@code doOnComplete}/{@code doOnError}
 * 里才写步骤（"做完才记账"），而 {@code onAgent} 包裹整次 reply（最外层），
 * 因此 <b>{@code agent} 步骤的 seq 最大</b>。实测一条真实链路：
 * {@code #1 intent → #2 model_call → #3 reasoning → #4 model_call → #5 reasoning → #6 agent}。
 * 本类按 seq 保序收集 span（消费方据此保持时序），但<b>不据此推断父子关系</b> ——
 * 父子关系由 {@link GenAiStepTypes#isContainer(String)} 与
 * {@link GenAiStepTypes#isEntry(String)} 判定。</p>
 *
 * <h2>⚠️ 静默失败的可见性</h2>
 * <p>{@link TraceCollector} 用 {@code catch (Exception ignored)} 包裹每个 observer 回调，
 * 所以本类内部的异常<b>不会影响主流程，也不会出现在日志里</b>。为此这里对
 * "拿到了 begin 却没有对应 end"的链路做了显式告警（见 {@link #onEnd(String, String, String, String, int, long)}），
 * 否则观测通道坏掉会长期无人察觉。</p>
 */
public class GenAiTraceObserver implements TraceObserver {

    private static final Logger log = LoggerFactory.getLogger(GenAiTraceObserver.class);

    /** 输入/输出属性截断长度（防 span 过大打爆导出器）。 */
    private static final int MAX_IO = 500;
    /** 提示/完成内容截断长度。 */
    private static final int MAX_CONTENT = 1000;

    private final GenAiSpanExporter exporter;

    /** traceId → 该链路已收集的 span（按写入顺序，根 span 在首位）。 */
    private final Map<String, List<GenAiSpan>> traceSpans = new ConcurrentHashMap<>();
    /** traceId → 根 span（onEnd 时收尾并导出）。 */
    private final Map<String, GenAiSpan> rootSpans = new ConcurrentHashMap<>();
    /** traceId → 链路归属信息（onStep 可能先于 onBegin 之外的场景需要回填 session/agent）。 */
    private final Map<String, String> traceAgents = new ConcurrentHashMap<>();

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
                GenAiOperationName.INVOKE_AGENT + " " + nullSafe(agentName), GenAiSpanKind.AGENT)
                .attribute(GenAiAttributeNames.SESSION_ID, sessionId)
                .attribute(GenAiAttributeNames.AGENT_NAME, agentName)
                .attribute(GenAiAttributeNames.INTENT, intent)
                .attribute(GenAiAttributeNames.TRIGGER_TYPE, triggerType)
                .attribute(GenAiAttributeNames.TRACE_ID, traceId)
                .attribute(GenAiAttributeNames.SPAN_KIND, GenAiSpanKind.AGENT.value())
                .attribute(GenAiAttributeNames.OPERATION_NAME, GenAiOperationName.INVOKE_AGENT)
                .attribute("agent.id", agentId);
        rootSpans.put(traceId, root);
        traceAgents.put(traceId, nullSafe(agentName));
        traceSpans.computeIfAbsent(traceId, k -> new ArrayList<>()).add(root);
        GenAiTraceDiagnostics.recordBegin(traceId);
    }

    /**
     * 记录子步骤。
     *
     * <p>⚠️ 早期实现开头是 {@code if (!traceSpans.containsKey(traceId)) return;} ——
     * 这会让"onBegin 因任何原因没到达本 observer"的链路<b>整条静默丢弃</b>。
     * 现在改为：只要拿到了 traceId 就建 span；若该 traceId 尚无根 span，
     * 则用 traceId 兜底补一个根，<b>保证不丢观测数据</b>。</p>
     */
    @Override
    public void onStep(String traceId, int seq, String stepType, String name,
                       String inputJson, String outputJson, long latencyMs, String status) {
        onStep(traceId, seq, stepType, name, inputJson, outputJson, latencyMs, status, null);
    }

    /**
     * 记录子步骤（带结构化属性）。
     *
     * <p>属性在<b>标准属性之后</b>写入，因此调用方可以覆盖（如显式给 {@code gen_ai.user.id}）。
     * 属性值为 {@code null} 的条目由 {@link GenAiSpan#attribute} 自动忽略，不会产生空属性。</p>
     */
    @Override
    public void onStep(String traceId, int seq, String stepType, String name, String inputJson,
                       String outputJson, long latencyMs, String status,
                       Map<String, Object> attributes) {
        if (traceId == null) {
            GenAiTraceDiagnostics.recordDroppedStep();
            return;
        }
        ensureRoot(traceId);

        GenAiSpanKind kind = GenAiStepTypes.toSpanKind(stepType);
        String normalized = GenAiStepTypes.normalize(stepType);
        GenAiSpan span = new GenAiSpan(traceId, spanId(traceId, "s" + seq),
                GenAiStepTypes.spanName(stepType, name), kind)
                .attribute(GenAiAttributeNames.SPAN_KIND, kind.value())
                .attribute(GenAiAttributeNames.OPERATION_NAME, GenAiOperationName.fromKind(kind))
                .attribute(GenAiAttributeNames.TOOL_NAME, name)
                .attribute(GenAiAttributeNames.TOOL_INPUT, truncate(inputJson, MAX_IO))
                .attribute(GenAiAttributeNames.TOOL_OUTPUT, truncate(outputJson, MAX_IO))
                .attribute(GenAiAttributeNames.STEP_TYPE, normalized)
                .attribute("step.seq", seq)
                .attribute("step.latency_ms", latencyMs);

        // 模型调用：从步骤名里提取模型名，按语义约定打到 gen_ai.request.model
        if (kind == GenAiSpanKind.LLM && name != null) {
            span.attribute(GenAiAttributeNames.REQUEST_MODEL, stripPrefix(name, "模型调用"));
            span.attribute(GenAiAttributeNames.PROVIDER_NAME, providerOf(name));
        }
        // 模块自有属性（memory.* 等）最后写入：允许调用方覆盖上面的推断值
        span.attributes(attributes);
        span.end(status == null ? "ok" : status);
        traceSpans.computeIfAbsent(traceId, k -> new ArrayList<>()).add(span);
        GenAiTraceDiagnostics.recordStep();
    }

    @Override
    public void onEnd(String traceId, String status, String prompt, String response,
                      int tokens, long latencyMs) {
        if (traceId == null) {
            return;
        }
        GenAiTraceDiagnostics.recordEnd(traceId);
        GenAiSpan root = rootSpans.remove(traceId);
        traceAgents.remove(traceId);
        List<GenAiSpan> spans = traceSpans.remove(traceId);
        if (spans == null || spans.isEmpty()) {
            // 没有 onBegin 也没有 onStep：这条链路与本 observer 无关，静默即可
            return;
        }
        if (root == null) {
            // 有步骤但根 span 缺失 —— 早期实现会在此直接 return 并把整条链路丢掉
            root = spans.get(0);
        }
        root.attribute(GenAiAttributeNames.PROMPT, truncate(prompt, MAX_CONTENT))
                .attribute(GenAiAttributeNames.COMPLETION, truncate(response, MAX_CONTENT))
                .attribute(GenAiAttributeNames.USAGE_OUTPUT_TOKENS, tokens)
                .attribute("trace.latency_ms", latencyMs)
                .attribute("trace.span_count", spans.size());
        root.end(status == null ? "success" : status);
        if (exporter != null) {
            try {
                exporter.export(spans);
                GenAiTraceDiagnostics.recordExport(spans.size());
            } catch (Exception e) {
                // 导出失败不反噬主流程，但必须留下痕迹 —— 否则又是"静默不产出"
                log.warn("[genai-trace] span 导出失败（导出器 {}）：{}",
                        exporter.name(), e.toString());
            }
        }
    }

    /** 观测通道健康快照（转发到 {@link GenAiTraceDiagnostics}）。 */
    public Map<String, Object> health() {
        return GenAiTraceDiagnostics.health();
    }

    /** 确保 traceId 有根 span（缺失时用 traceId 兜底补建，避免整条链路静默丢失）。 */
    private void ensureRoot(String traceId) {
        if (rootSpans.containsKey(traceId)) {
            return;
        }
        rootSpans.computeIfAbsent(traceId, id -> {
            GenAiSpan root = new GenAiSpan(id, spanId(id, "root"),
                    GenAiOperationName.INVOKE_AGENT + " " + nullSafe(traceAgents.get(id)),
                    GenAiSpanKind.AGENT)
                    .attribute(GenAiAttributeNames.TRACE_ID, id)
                    .attribute(GenAiAttributeNames.SPAN_KIND, GenAiSpanKind.AGENT.value())
                    .attribute(GenAiAttributeNames.OPERATION_NAME, GenAiOperationName.INVOKE_AGENT);
            traceSpans.computeIfAbsent(id, k -> new ArrayList<>()).add(root);
            return root;
        });
    }

    /** 从步骤名里剥掉中文前缀，取出模型名（如 "模型调用 DashScopeChatModel" → "DashScopeChatModel"）。 */
    private static String stripPrefix(String name, String prefix) {
        String trimmed = name.trim();
        return trimmed.startsWith(prefix) ? trimmed.substring(prefix.length()).trim() : trimmed;
    }

    /** 从模型名粗判提供商（对齐 gen_ai.provider.name）。 */
    private static String providerOf(String modelName) {
        String lower = modelName == null ? "" : modelName.toLowerCase();
        if (lower.contains("dashscope") || lower.contains("qwen")) {
            return "dashscope";
        }
        if (lower.contains("openai") || lower.contains("gpt")) {
            return "openai";
        }
        if (lower.contains("claude") || lower.contains("anthropic")) {
            return "anthropic";
        }
        if (lower.contains("deepseek")) {
            return "deepseek";
        }
        if (lower.contains("glm") || lower.contains("zhipu")) {
            return "zhipu";
        }
        return "unknown";
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
