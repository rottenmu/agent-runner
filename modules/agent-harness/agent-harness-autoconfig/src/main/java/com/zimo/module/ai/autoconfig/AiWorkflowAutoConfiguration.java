package com.zimo.module.ai.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.ai.mapper.WfTemplateMapper;
import com.zimo.module.ai.mapper.WfWorkflowMapper;
import com.zimo.module.ai.mapper.WfWorkflowRunLogMapper;
import com.zimo.module.ai.mapper.WfWorkflowRunMapper;
import com.zimo.module.ai.mapper.WfWorkflowVersionMapper;
import com.zimo.module.ai.workflow.WfRunService;
import com.zimo.module.ai.workflow.WfTemplateService;
import com.zimo.module.ai.workflow.WfWorkflowEngine;
import com.zimo.module.ai.workflow.WfWorkflowService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 可视化工作流编排装配（P1-3 第二阶段拆分）。
 *
 * <p>工作流定义 / 执行引擎 / 运行控制 / 模板市场 独立于智能体管理核心服务，
 * 拆出后可按需独立演进与测试。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-17
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "plugin.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiWorkflowAutoConfiguration {

    /** 注册工作流定义服务。 */
    @Bean
    @ConditionalOnMissingBean
    public WfWorkflowService wfWorkflowService(
            WfWorkflowMapper workflowMapper,
            WfWorkflowVersionMapper versionMapper) {
        return new WfWorkflowService(workflowMapper, versionMapper);
    }

    /** 注册工作流执行引擎。 */
    @Bean
    @ConditionalOnMissingBean
    public WfWorkflowEngine wfWorkflowEngine(
            WfWorkflowRunMapper runMapper,
            WfWorkflowRunLogMapper logMapper,
            ObjectMapper objectMapper,
            org.springframework.beans.factory.ObjectProvider<com.zimo.starter.ai.chat.AiChatClient> chatClientProvider) {
        return new WfWorkflowEngine(runMapper, logMapper, objectMapper, chatClientProvider.getIfAvailable());
    }

    /** 注册工作流运行控制服务。 */
    @Bean
    @ConditionalOnMissingBean
    public WfRunService wfRunService(
            WfWorkflowService workflowService,
            WfWorkflowEngine engine,
            WfWorkflowRunMapper runMapper,
            WfWorkflowRunLogMapper logMapper) {
        return new WfRunService(workflowService, engine, runMapper, logMapper);
    }

    /** 注册工作流模板市场服务。 */
    @Bean
    @ConditionalOnMissingBean
    public WfTemplateService wfTemplateService(WfTemplateMapper templateMapper) {
        return new WfTemplateService(templateMapper);
    }
}
