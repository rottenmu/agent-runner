package com.zimo.module.trace.autoconfig;

import com.zimo.module.trace.genai.GenAiSpanExporter;
import com.zimo.module.trace.genai.GenAiTraceDiagnostics;
import com.zimo.module.trace.genai.GenAiTraceObserver;
import com.zimo.module.trace.genai.LogGenAiSpanExporter;
import com.zimo.framework.ai.observ.TraceCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <h2>⚠️ 注册 ≠ 生效</h2>
 * <p>本类只保证 observer 进了 {@link TraceCollector} 的列表，<b>不代表真的导出了 span</b>。
 * 历史故障：observer 注册成功、启动日志正常，但导出为 0 条——原因是 stepType 词汇表
 * 三套互不匹配（详见 {@link com.zimo.module.trace.genai.GenAiStepTypes}）。</p>
 *
 * <p>排查时<b>不要</b>用模糊关键词 {@code genai} 检索日志：曾出现 130 条命中里 129 条是
 * 名为 {@code genai-trace-final} 的测试链路数据造成的假阳性。正确判据是精确标记
 * {@code [genai-span]}。推荐先看自检接口 {@link GenAiTraceDiagnostics#health()}，
 * 其中 {@code warnings} 会直接指出「begun &gt; 0 但 exported == 0」这类静默失效。</p>
 */
@AutoConfiguration
@ConditionalOnClass(TraceCollector.class)
public class GenAiTraceAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GenAiTraceAutoConfiguration.class);

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
        // 只证明"已注册"，不证明"会产出 span"。真实产出情况查 GenAiTraceDiagnostics.health()。
        log.info("[genai-trace] GenAiTraceObserver registered: {} (exporter={}), "
                        + "verify actual output via GenAiTraceDiagnostics.health()",
                observer, exporter.getClass().getSimpleName());
        return observer;
    }
}
