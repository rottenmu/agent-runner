package com.zimo.framework.ai.observ;

import com.zimo.framework.ai.AiAgentProperties;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenTelemetry SDK 初始化器：把 {@code OtelTracingMiddleware} 产出的 span 通过 OTLP 导出。
 *
 * <p><b>为什么需要单独初始化</b>：AgentScope 从进程级 {@code GlobalOpenTelemetry} 读取配置。
 * 只挂中间件而不注册 SDK 时，全局实例是默认的 no-op provider，所有 hook 会直接短路——
 * 即"装了但看不到任何数据"。本类补上这一环。</p>
 *
 * <p><b>默认关闭</b>：{@code ai.agent.otel-enabled} 默认 {@code false}。未开启时不注册 SDK，
 * 中间件自动降级为 no-op，不产生网络流量与额外开销。开启需后端有 OTLP 接收端，
 * 否则 batch processor 会持续重试并在关闭时打日志。</p>
 *
 * <p><b>与 Spring Boot Actuator 的关系</b>：若运行环境已通过 OpenTelemetry 自动配置注册了
 * {@code GlobalOpenTelemetry}，本类检测到后直接复用，不重复注册（重复注册会导致 span 丢失）。</p>
 *
 * @author WorkBuddy
 * @since 2026-09-13
 */
public class OtelTracingInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(OtelTracingInitializer.class);

    private final boolean otelEnabled;
    private final String endpoint;
    private final String authorization;
    private final String serviceName;

    private SdkTracerProvider tracerProvider;

    /**
     * 构造初始化器。
     *
     * @param properties AI 智能体配置（读取 otel-* 项）
     */
    public OtelTracingInitializer(AiAgentProperties properties) {
        this.otelEnabled = properties != null && properties.isOtelEnabled();
        this.endpoint = properties == null ? null : properties.getOtelEndpoint();
        this.authorization = properties == null ? null : properties.getOtelAuthorization();
        this.serviceName = properties == null ? "agent-runner" : properties.getOtelServiceName();
    }

    /**
     * 注册全局 SDK 并挂载关停钩子。
     *
     * <p>未开启或已存在 {@code GlobalOpenTelemetry} 时不做任何事。多次调用安全（幂等）。</p>
     *
     * @return {@code true} 表示本次真正注册了 SDK
     */
    public synchronized boolean initialize() {
        if (!otelEnabled) {
            LOG.info("[otel] 未启用（ai.agent.otel-enabled=false），span 走 no-op 不导出");
            return false;
        }
        if (tracerProvider != null) {
            return false;
        }
        // 检测已有全局实例：OpenTelemetry API 无法直接查询，用 getTracerProvider 的类名判断
        if (isGlobalAlreadyConfigured()) {
            LOG.info("[otel] 检测到已注册的 GlobalOpenTelemetry，复用现有配置，不重复注册");
            return false;
        }

        var exporterBuilder = OtlpHttpSpanExporter.builder().setEndpoint(endpoint);
        if (authorization != null && !authorization.isBlank()) {
            exporterBuilder.addHeader("Authorization", authorization);
        }

        this.tracerProvider =
                SdkTracerProvider.builder()
                        .setResource(Resource.getDefault().merge(Resource.create(
                                Attributes.of(ServiceAttributes.SERVICE_NAME, serviceName))))
                        .addSpanProcessor(
                                BatchSpanProcessor.builder(exporterBuilder.build()).build())
                        .build();

        OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();

        // 关闭时刷新未导出的 span（否则进程退出会丢最后一批）
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "otel-shutdown"));

        LOG.info("[otel] 已注册 OTLP 导出，endpoint={}，service={}，auth={}",
                endpoint, serviceName, authorization == null ? "无" : "已配置");
        return true;
    }

    /**
     * 关闭 provider，让 batch processor 刷出待导出 span。
     *
     * <p>由 Spring 容器在销毁阶段或 JVM 关停钩子调用。异常不外抛（关停路径不应阻断）。</p>
     */
    public synchronized void close() {
        if (tracerProvider == null) {
            return;
        }
        try {
            tracerProvider.close();
            tracerProvider = null;
            LOG.info("[otel] TracerProvider 已关闭，待导出 span 已刷新");
        } catch (RuntimeException exception) {
            LOG.warn("[otel] 关闭 TracerProvider 失败: {}", exception.getMessage());
        }
    }

    /** 当前是否真正注册了 SDK（供健康检查/诊断端点读取）。 */
    public boolean isActive() {
        return tracerProvider != null;
    }

    /** 已注册返回导出端点，否则返回 null。 */
    public String activeEndpoint() {
        return tracerProvider == null ? null : endpoint;
    }

    /**
     * 判断全局 {@code OpenTelemetry} 是否已被配置（非 no-op）。
     *
     * <p>用 TracerProvider 实现类名判断：默认实现为 {@code DefaultTracerProvider}
     * （no-op），SDK 注册后变为 {@code SdkTracerProvider} 或自动配置的包装类。</p>
     */
    private static boolean isGlobalAlreadyConfigured() {
        try {
            Object provider = GlobalOpenTelemetry.get().getTracerProvider();
            String className = provider.getClass().getName();
            return !className.contains("DefaultTracerProvider")
                    && !className.contains("NoopTracerProvider");
        } catch (RuntimeException | LinkageError error) {
            // 探测失败时按"已配置"处理较安全：避免重复注册破坏既有 span 链路
            LOG.debug("[otel] 全局实例探测失败，按已配置处理: {}", error.getMessage());
            return true;
        }
    }
}
