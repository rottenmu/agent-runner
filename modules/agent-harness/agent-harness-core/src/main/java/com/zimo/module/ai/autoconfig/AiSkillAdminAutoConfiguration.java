package com.zimo.module.ai.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.ai.management.AiAgentManagementService;
import com.zimo.module.ai.management.AiManagedAgentContributor;
import com.zimo.module.ai.management.AiManagedAgentProfileResolver;
import com.zimo.module.ai.management.AiManagedAgentRepository;
import com.zimo.module.ai.management.AiManagedAgentRuntimeInvalidator;
import com.zimo.module.ai.management.AiManagedSkillConfigRepository;
import com.zimo.module.ai.management.MybatisPlusAiManagedAgentRepository;
import com.zimo.module.ai.management.MybatisPlusAiManagedSkillConfigRepository;
import com.zimo.module.ai.mapper.AiManagedAgentMapper;
import com.zimo.module.ai.mapper.AiManagedSkillConfigMapper;
import com.zimo.module.ai.skillimport.AiSkillZipImportService;
import com.zimo.module.ai.skillimport.AiSkillZipParser;
import com.zimo.framework.ai.agent.AiHarnessAgentRegistry;
import com.zimo.framework.ai.autoconfig.AiAgentAutoConfiguration;
import com.zimo.framework.ai.skill.AiSkillRegistry;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * AI 智能体、技能和提示词模板管理能力自动装配。
 *
 * <p>该配置在 starter 运行时能力就绪后注册 MyBatis-Plus Mapper、管理 Service、运行时解析器和
 * {@code /api/biz/ai/**} 管理控制器。所有 Bean 仅在 AI 插件启用时生效。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
@AutoConfiguration(after = AiAgentAutoConfiguration.class)
@ConditionalOnProperty(prefix = "plugin.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(AiSkillRegistry.class)
public class AiSkillAdminAutoConfiguration {

    /** 注册智能体 MyBatis-Plus 仓储。 */
    @Bean
    @ConditionalOnMissingBean
    public AiManagedAgentRepository aiManagedAgentRepository(
            AiManagedAgentMapper mapper,
            ObjectMapper objectMapper) {
        return new MybatisPlusAiManagedAgentRepository(mapper, objectMapper);
    }

    /** 注册技能配置 MyBatis-Plus 仓储。 */
    @Bean
    @ConditionalOnMissingBean
    public AiManagedSkillConfigRepository aiManagedSkillConfigRepository(AiManagedSkillConfigMapper mapper) {
        return new MybatisPlusAiManagedSkillConfigRepository(mapper);
    }

/** 注册智能体与技能管理服务（依赖 AgentScope 技能注册表，缺失时跳过）。 */
    @Bean
    @ConditionalOnBean(AiSkillRegistry.class)
    @ConditionalOnMissingBean
    public AiAgentManagementService aiAgentManagementService(
            AiSkillRegistry skillRegistry,
            List<AiManagedAgentContributor> contributors,
            AiManagedSkillConfigRepository skillRepository,
            AiManagedAgentRepository agentRepository,
            ObjectProvider<AiHarnessAgentRegistry> registryProvider) {
        AiHarnessAgentRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            return new AiAgentManagementService(
                    skillRegistry,
                    contributors,
                    skillRepository,
                    agentRepository);
        }
        AiManagedAgentRuntimeInvalidator invalidator = registry::invalidate;
        return new AiAgentManagementService(
                skillRegistry,
                contributors,
                skillRepository,
                agentRepository,
                invalidator);
    }

    /** 注册 starter 所需的渠道默认智能体解析器。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(AiAgentManagementService.class)
    public AiManagedAgentProfileResolver aiManagedAgentProfileResolver(AiAgentManagementService service) {
        return new AiManagedAgentProfileResolver(service);
    }

    /**
     * 注册单技能 ZIP 导入解析器。
     *
     * @param objectMapper Spring Boot JSON 映射器，不能为空
     * @return 使用严格清单规则的 ZIP 解析器
     */
/**
     * 注册单技能 ZIP 导入编排服务。
     *
     * @param parser ZIP 清单解析器，不能为空
     * @param service 技能管理服务，不能为空
     * @return 复用现有技能创建流程的导入服务
     */
/** 注册提示词模板管理控制器（保存时自动生成版本）。 */
/** 注册提示词版本 / 快照服务。 */
/** 技能 ZIP 导入服务（依赖技能管理服务，缺失时跳过）。 */
    @Bean
    @ConditionalOnBean(AiAgentManagementService.class)
    @ConditionalOnMissingBean
    public AiSkillZipImportService aiSkillZipImportService(
            AiSkillZipParser parser,
            AiAgentManagementService service) {
        return new AiSkillZipImportService(parser, service);
    }
}
