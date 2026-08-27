package com.zimo.module.trace.genai;

import java.util.List;

/**
 * GenAI span 导出 SPI。
 *
 * <p>实现示例：OTLP 导出器（对接 Jaeger/Tempo/阿里云 ARMS）、结构化日志导出器、
 * 或聚合写入 observ_trace 宽表。默认提供 {@link LogGenAiSpanExporter}。</p>
 */
public interface GenAiSpanExporter {

    /** 导出一批 span（调用方保证非空）。 */
    void export(List<GenAiSpan> spans);

    /** 名称（日志/注册标识）。 */
    default String name() {
        return getClass().getSimpleName();
    }
}
