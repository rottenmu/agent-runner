package com.zimo.module.trace.genai;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GenAI 链路 span 模型（OTel GenAI 语义的自包含表示，不依赖 OTel SDK）。
 *
 * <p>字段对齐语义约定：name（如 {@code invoke_agent <name>} / {@code chat <model>} /
 * {@code execute_tool <name>}）、kind、operationName、attributes（gen_ai.*）、
 * 起止纳秒时间与状态。导出由 {@link GenAiSpanExporter} SPI 完成（OTLP/日志等）。</p>
 */
public final class GenAiSpan {

    private final String traceId;
    private final String spanId;
    private final String name;
    private final GenAiSpanKind kind;
    private final String operationName;
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private long startNs;
    private long endNs;
    private String status;

    public GenAiSpan(String traceId, String spanId, String name, GenAiSpanKind kind) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.name = name;
        this.kind = kind;
        this.operationName = GenAiOperationName.fromKind(kind);
        this.startNs = System.nanoTime();
    }

    public GenAiSpan attribute(String key, Object value) {
        if (key != null && value != null) {
            attributes.put(key, value);
        }
        return this;
    }

    public GenAiSpan attributes(Map<String, Object> values) {
        if (values != null) {
            values.forEach(this::attribute);
        }
        return this;
    }

    public void end(String endStatus) {
        this.endNs = System.nanoTime();
        this.status = endStatus;
    }

    public long durationMs() {
        return (endNs - startNs) / 1_000_000L;
    }

    public String traceId() {
        return traceId;
    }

    public String spanId() {
        return spanId;
    }

    public String name() {
        return name;
    }

    public GenAiSpanKind kind() {
        return kind;
    }

    public String operationName() {
        return operationName;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public String status() {
        return status;
    }

    public long startNs() {
        return startNs;
    }

    public long endNs() {
        return endNs;
    }

    /** 摘要行（日志/调试用）。 */
    public String summary() {
        return name + " [" + kind.value() + "] op=" + operationName
                + " status=" + status + " dur=" + durationMs() + "ms"
                + " attrs=" + attributes.size();
    }
}
