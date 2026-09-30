package com.zimo.module.trace.genai;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 结构化日志导出器（默认实现）。
 *
 * <p>把一批 span 以结构化格式打进日志，便于 {@code grep} 与日志采集系统解析。
 * 相比早期实现的 {@code System.out.println}，本实现走 SLF4J，具备：</p>
 * <ul>
 *   <li>统一日志级别与格式（可被日志框架过滤/采样，不污染 stdout）</li>
 *   <li>固定前缀 {@code [genai-span]}，便于精确检索 ——
 *       <b>不要用模糊的 {@code genai} 关键词</b>：业务数据里可能出现同名内容造成假阳性
 *       （本项目就踩过：日志里对 {@code genai} 的 129 处命中，其实全部来自一条名为
 *       {@code genai-trace-final} 的测试链路数据，真正的 span 导出是 0 行）</li>
 *   <li>一次导出打一行链路汇总 + 每 span 一行明细，大链路也不至于刷屏</li>
 * </ul>
 *
 * <p>生产环境建议替换为 OTLP 导出器（对接 Jaeger / Tempo / 阿里云 ARMS）：
 * 声明一个 {@link GenAiSpanExporter} Bean 即可覆盖本默认实现。</p>
 */
public class LogGenAiSpanExporter implements GenAiSpanExporter {

    private static final Logger log = LoggerFactory.getLogger(LogGenAiSpanExporter.class);

    @Override
    public void export(List<GenAiSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            return;
        }
        GenAiSpan root = spans.get(0);
        log.info("[genai-span] trace={} spans={} status={} dur={}ms agent={} session={}",
                root.traceId(), spans.size(), root.status(), root.durationMs(),
                root.attributes().get(GenAiAttributeNames.AGENT_NAME),
                root.attributes().get(GenAiAttributeNames.SESSION_ID));
        for (GenAiSpan span : spans) {
            log.info("[genai-span]   {}", describe(span));
        }
    }

    /** 单 span 的一行描述（只列关键 gen_ai.* 属性）。 */
    private String describe(GenAiSpan span) {
        StringBuilder sb = new StringBuilder();
        sb.append("name=").append(span.name())
                .append(" kind=").append(span.kind().value())
                .append(" op=").append(span.operationName())
                .append(" status=").append(span.status())
                .append(" dur=").append(span.durationMs()).append("ms");
        appendIfPresent(sb, "model", span.attributes().get(GenAiAttributeNames.REQUEST_MODEL));
        appendIfPresent(sb, "in", span.attributes().get(GenAiAttributeNames.USAGE_INPUT_TOKENS));
        appendIfPresent(sb, "out", span.attributes().get(GenAiAttributeNames.USAGE_OUTPUT_TOKENS));
        appendIfPresent(sb, "stepType", span.attributes().get(GenAiAttributeNames.STEP_TYPE));
        appendModuleAttributes(sb, span);
        return sb.toString();
    }

    /**
     * 追加模块自有属性（{@code memory.*} 等）到单行描述。
     *
     * <p><b>为什么必须打出来</b>：这些属性是真机验证的判据（如
     * {@code memory.recall.algorithm=s1_hybrid}、{@code memory.recall.truncated=false}、
     * {@code memory.async=true}）。若只把它们放进 span 对象而不落到日志，
     * 默认导出器下「属性有没有真的进链路」就无从验证 —— 会出现「代码写了、也测不出来」的局面。</p>
     */
    private void appendModuleAttributes(StringBuilder sb, GenAiSpan span) {
        for (java.util.Map.Entry<String, Object> entry : span.attributes().entrySet()) {
            if (entry.getKey().startsWith("memory.")) {
                appendIfPresent(sb, entry.getKey(), entry.getValue());
            }
        }
    }

    private void appendIfPresent(StringBuilder sb, String key, Object value) {
        if (value != null) {
            sb.append(' ').append(key).append('=').append(value);
        }
    }
}
