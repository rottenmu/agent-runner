package com.zimo.module.ai.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.ai.chat.AiChatClient;
import com.zimo.module.ai.graph.GraphEngine;
import com.zimo.module.ai.graph.GraphNodeExecutor;
import com.zimo.module.ai.graph.GraphRunService;
import com.zimo.module.ai.mapper.WfWorkflowRunLogMapper;
import com.zimo.module.ai.mapper.WfWorkflowRunMapper;
import com.zimo.module.ai.workflow.WfWorkflowService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Agent 运行时图编排装配。
 *
 * <p>所属模块：agent-harness 运行时内核。注册图执行引擎、节点执行器与运行控制服务，
 * 与现有 {@code AiWorkflowAutoConfiguration} 并存，互不覆盖（均使用
 * {@link ConditionalOnMissingBean}）。</p>
 *
 * <p>并行执行使用独立有界线程池，避免与 Web 请求线程池或公共 ForkJoinPool 争抢资源；
 * 线程为守护线程，应用关闭时随容器退出。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "plugin.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiGraphAutoConfiguration {

    /** 并行节点线程数，固定小池以避免单次图运行打满 CPU。 */
    private static final int PARALLEL_POOL_SIZE = 4;

    /**
     * 注册图并行节点执行线程池。
     *
     * @return 固定大小守护线程池，容器关闭时自动 shutdown
     */
    @Bean(name = "graphParallelExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "graphParallelExecutor")
    public ExecutorService graphParallelExecutor() {
        AtomicInteger counter = new AtomicInteger();
        return Executors.newFixedThreadPool(PARALLEL_POOL_SIZE, runnable -> {
            Thread thread = new Thread(runnable, "graph-parallel-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 注册图节点执行器。
     *
     * @param chatClientProvider 聊天客户端（LLM 节点使用），允许缺失
     * @param runtimeHandlerProvider 运行时节点处理器（agent / tool 节点使用），允许缺失
     * @param objectMapper JSON 映射器
     * @return 节点执行器
     */
    @Bean
    @ConditionalOnMissingBean
    public GraphNodeExecutor graphNodeExecutor(
            ObjectProvider<AiChatClient> chatClientProvider,
            ObjectProvider<com.zimo.module.ai.runtime.RuntimeNodeHandler> runtimeHandlerProvider,
            ObjectMapper objectMapper) {
        return new GraphNodeExecutor(chatClientProvider.getIfAvailable(),
                objectMapper, runtimeHandlerProvider.getIfAvailable());
    }

    /**
     * 注册图执行引擎。
     *
     * @param runMapper 运行实例 Mapper
     * @param logMapper 运行日志 Mapper
     * @param nodeExecutor 节点执行器
     * @param objectMapper JSON 映射器
     * @param parallelExecutor 并行线程池
     * @return 图执行引擎
     */
    @Bean
    @ConditionalOnMissingBean
    public GraphEngine graphEngine(
            WfWorkflowRunMapper runMapper,
            WfWorkflowRunLogMapper logMapper,
            GraphNodeExecutor nodeExecutor,
            ObjectMapper objectMapper,
            @Qualifier("graphParallelExecutor") ExecutorService parallelExecutor) {
        return new GraphEngine(runMapper, logMapper, nodeExecutor, objectMapper, parallelExecutor);
    }

    /**
     * 注册图运行控制服务。
     *
     * @param workflowService 工作流定义服务
     * @param engine 图执行引擎
     * @param runMapper 运行实例 Mapper
     * @param logMapper 运行日志 Mapper
     * @param objectMapper JSON 映射器（解析 checkpoint 与图定义）
     * @return 图运行控制服务
     */
    @Bean
    @ConditionalOnMissingBean
    public GraphRunService graphRunService(
            WfWorkflowService workflowService,
            GraphEngine engine,
            WfWorkflowRunMapper runMapper,
            WfWorkflowRunLogMapper logMapper,
            ObjectMapper objectMapper) {
        return new GraphRunService(workflowService, engine, runMapper, logMapper, objectMapper);
    }
}
