package com.zimo.module.ai.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.ai.collab.AgentRoleTemplateMapper;
import com.zimo.module.ai.collab.AgentSessionMapper;
import com.zimo.module.ai.collab.AgentSessionMemberMapper;
import com.zimo.module.ai.collab.AgentSessionMessageMapper;
import com.zimo.module.ai.collab.AgentTaskFlowMapper;
import com.zimo.module.ai.collab.AgentTaskMapper;
import com.zimo.module.ai.collab.CollabController;
import com.zimo.module.ai.collab.MultiAgentCollaborationService;
import com.zimo.module.ai.collab.SessionForkResumeController;
import com.zimo.module.ai.collab.SessionForkResumeService;
import com.zimo.module.ai.management.AiAgentManagementService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 多智能体协同编排装配（P1-3 第二阶段拆分）。
 *
 * <p>依赖智能体管理服务（{@link AiAgentManagementService}），管理服务缺失时整体跳过
 * （类级条件经 after 时序保证安全，与主配置类同模式）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-17
 */
@AutoConfiguration(after = AiSkillAdminAutoConfiguration.class)
@ConditionalOnProperty(prefix = "plugin.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(AiAgentManagementService.class)
@MapperScan(basePackages = "com.zimo.module.ai.collab")
public class AiCollabAutoConfiguration {

    /** 注册多智能体协同编排服务与接口。 */
    @Bean
    @ConditionalOnMissingBean
    public MultiAgentCollaborationService multiAgentCollaborationService(
            AgentSessionMapper sessionMapper,
            AgentSessionMemberMapper memberMapper,
            AgentTaskMapper taskMapper,
            AgentTaskFlowMapper flowMapper,
            AgentSessionMessageMapper messageMapper,
            AgentRoleTemplateMapper templateMapper,
            AiAgentManagementService agentManagement,
            com.zimo.starter.ai.AiAgentService aiAgentService,
            ObjectMapper objectMapper) {
        MultiAgentCollaborationService service = new MultiAgentCollaborationService(
                sessionMapper, memberMapper, taskMapper, flowMapper, messageMapper, templateMapper,
                agentManagement, aiAgentService, objectMapper);
        service.initSeedTemplates();
        return service;
    }

    @Bean
    @ConditionalOnMissingBean
    public CollabController collabController(MultiAgentCollaborationService collabService) {
        return new CollabController(collabService);
    }

    /** 会话键级 fork/resume（基于事件日志派生上下文，需智能体管理服务解析 profile）。 */
    @Bean
    @ConditionalOnMissingBean
    public SessionForkResumeService sessionForkResumeService(
            com.zimo.module.ai.observ.SessionEventLogService eventLogService,
            com.zimo.starter.ai.AiAgentService aiAgentService,
            AiAgentManagementService agentManagement) {
        return new SessionForkResumeService(eventLogService, aiAgentService, agentManagement);
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionForkResumeController sessionForkResumeController(
            SessionForkResumeService forkResumeService) {
        return new SessionForkResumeController(forkResumeService);
    }
}
