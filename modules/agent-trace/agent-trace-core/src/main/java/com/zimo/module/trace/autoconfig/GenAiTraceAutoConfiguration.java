package com.zimo.module.trace.autoconfig;

import com.zimo.module.trace.genai.GenAiSpanExporter;
import com.zimo.module.trace.genai.GenAiTraceObserver;
import com.zimo.module.trace.genai.LogGenAiSpanExporter;
import com.zimo.framework.ai.observ.TraceCollector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * GenAI 链路观测自动装配（agent-trace）。
 *
 * <p>装配 GenAiSpanExporter（默认日志导出）与 GenAiTraceObserver，
 * 并注册到 {@link TraceCollector}——主链路（AiAgentService）/ RAG 检索事件自动流入
 * OTel GenAI 语义 span 树。可通过自定义 {@link GenAiSpanExporter} bean（如 OTLP）
 * 替换默认日志导出。</p>
 */
@AutoConfiguration
@ConditionalOnClass(TraceCollector.class)
public class GenAiTraceAutoConfiguration {

    /** 默认 span 导出器（结构化日志）。业务可提供自定义 GenAiSpanExporter 覆盖。 */
    @Bean
    @ConditionalOnMissingBean(GenAiSpanExporter.class)
    public GenAiSpanExporter genAiSpanExporter() {
        return new LogGenAiSpanExporter();
    }

    /** GenAI 观测者：注册到 TraceCollector，主链路事件自动分发。 */
    @Bean
    public GenAiTraceObserver genAiTraceObserver(GenAiSpanExporter exporter) {
        GenAiTraceObserver observer = new GenAiTraceObserver(exporter);
        TraceCollector.register(observer);
        System.out.println("[genai-trace] GenAiTraceObserver registered: " + observer);
        return observer;
    }
}
