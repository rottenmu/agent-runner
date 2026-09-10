package com.zimo.module.ai.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.ai.collab.AgentTaskMapper;
import com.zimo.module.ai.observ.AlertService;
import com.zimo.module.ai.observ.DashboardService;
import com.zimo.module.ai.observ.ObservAlertEventMapper;
import com.zimo.module.ai.observ.ObservAlertRuleMapper;
import com.zimo.module.ai.observ.ObservController;
import com.zimo.module.ai.observ.SessionEventLogMapper;
import com.zimo.module.ai.observ.SessionEventLogService;
import com.zimo.module.ai.observ.SessionEventLogServiceImpl;
import com.zimo.module.ai.observ.ObservTestCaseMapper;
import com.zimo.module.ai.observ.ObservTestCaseResultMapper;
import com.zimo.module.ai.observ.ObservTestRunMapper;
import com.zimo.module.ai.observ.ObservTraceMapper;
import com.zimo.module.ai.observ.ObservTraceService;
import com.zimo.module.ai.observ.ObservTraceStepMapper;
import com.zimo.module.ai.observ.TestRunnerService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 观测中心装配（P1-3 第二阶段拆分）：链路追踪 / 告警 / 自动化测试 / 大盘。
 *
 * @author WorkBuddy
 * @since 2026-08-17
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "plugin.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@MapperScan(basePackages = "com.zimo.module.ai.observ", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class AiObservAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AlertService observAlertService(
            ObservAlertRuleMapper ruleMapper,
            ObservAlertEventMapper eventMapper) {
        return new AlertService(ruleMapper, eventMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionEventLogService sessionEventLogService(
            SessionEventLogMapper eventLogMapper,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        return new SessionEventLogServiceImpl(eventLogMapper, jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservTraceService observTraceService(
            ObservTraceMapper traceMapper,
            ObservTraceStepMapper stepMapper,
            AlertService alertService) {
        return new ObservTraceService(traceMapper, stepMapper, alertService);
    }

    /** 遥测事件桥（订阅事件总线写入 observ，解耦业务代码）。 */
    @Bean
    @ConditionalOnMissingBean
    public com.zimo.module.ai.observ.ObservEventBridge observEventBridge(
            ObservTraceService observTraceService) {
        return new com.zimo.module.ai.observ.ObservEventBridge(observTraceService);
    }

    @Bean
    @ConditionalOnMissingBean
    public TestRunnerService observTestRunnerService(
            ObservTestCaseMapper caseMapper,
            ObservTestRunMapper runMapper,
            ObservTestCaseResultMapper resultMapper,
            com.zimo.framework.ai.AiAgentService aiAgentService,
            com.zimo.module.rag.service.RagRetrieveService retrieveService,
            ObjectMapper objectMapper) {
        return new TestRunnerService(caseMapper, runMapper, resultMapper,
                aiAgentService, retrieveService, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public DashboardService observDashboardService(
            ObservTraceMapper traceMapper,
            ObservTraceStepMapper stepMapper,
            AgentTaskMapper agentTaskMapper) {
        return new DashboardService(traceMapper, stepMapper, agentTaskMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservController observController(
            ObservTraceService traceService,
            TestRunnerService testRunnerService,
            AlertService alertService,
            DashboardService dashboardService,
            SessionEventLogService eventLogService) {
        return new ObservController(traceService, testRunnerService, alertService,
                dashboardService, eventLogService);
    }
}
