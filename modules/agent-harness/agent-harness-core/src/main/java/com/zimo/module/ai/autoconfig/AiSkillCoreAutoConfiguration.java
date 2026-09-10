package com.zimo.module.ai.autoconfig;

import com.zimo.module.ai.management.AiAbTestService;
import com.zimo.module.ai.management.AiAbTestServiceImpl;
import com.zimo.module.ai.management.AiAgentCapabilityService;
import com.zimo.module.ai.management.AiAgentCapabilityServiceImpl;
import com.zimo.module.ai.management.AiApiDocService;
import com.zimo.module.ai.management.AiApiDocServiceImpl;
import com.zimo.module.ai.management.AiMcpConfigService;
import com.zimo.module.ai.management.AiMcpConfigServiceImpl;
import com.zimo.module.ai.management.AiPromptTemplateGenerator;
import com.zimo.module.ai.management.AiPromptTemplateRepository;
import com.zimo.module.ai.management.AiPromptTemplateService;
import com.zimo.module.ai.management.AiPromptVersionService;
import com.zimo.module.ai.management.AiPromptVersionServiceImpl;
import com.zimo.module.ai.management.MybatisPlusAiPromptTemplateRepository;
import com.zimo.module.ai.mapper.AiManagedAgentMapper;
import com.zimo.module.ai.mapper.AiPromptTemplateMapper;
import com.zimo.module.ai.skillimport.AiSkillZipParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 模块核心通用服务装配（独立于 AgentScope 技能注册表）。
 *
 * <p>P1-3 拆分：将不依赖 {@code AiSkillRegistry}/{@code AiAgentManagementService}
 * 的通用服务（提示词模板 / AB 测试 / 能力配置 / MCP / API 文档 / ZIP 解析）
 * 从 {@link AiSkillAdminAutoConfiguration} 独立出来——技能注册表缺失时
 * 这些服务仍可用，避免"整组静默失效"。</p>
 *
 * <p>Mapper 扫描（{@code com.zimo.module.ai.mapper}）放在本无条件配置：
 * {@link AiSkillAdminAutoConfiguration} 受 {@code plugin.ai.enabled} 与
 * {@code AiSkillRegistry} 条件控制，若 Mapper 留在该处，本配置的通用服务
 * 在开关关闭时会因 Mapper 缺失而启动失败——Mapper 本身无业务条件，
 * 提前注册无副作用。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-17
 */
@AutoConfiguration
@MapperScan(basePackageClasses = AiManagedAgentMapper.class)
public class AiSkillCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AiPromptTemplateRepository aiPromptTemplateRepository(AiPromptTemplateMapper mapper) {
        return new MybatisPlusAiPromptTemplateRepository(mapper);
    }

    
    @Bean
    @ConditionalOnMissingBean
    public AiPromptTemplateGenerator aiPromptTemplateGenerator() {
        return new AiPromptTemplateGenerator();
    }

    
    @Bean
    @ConditionalOnMissingBean
    public AiPromptTemplateService aiPromptTemplateService(
            AiPromptTemplateRepository repository,
            AiPromptTemplateGenerator generator) {
        return new AiPromptTemplateService(repository, generator);
    }

    
    @Bean
    @ConditionalOnMissingBean
    public AiPromptVersionService aiPromptVersionService(ObjectMapper objectMapper) {
        return new AiPromptVersionServiceImpl(objectMapper);
    }

    
    @Bean
    @ConditionalOnMissingBean
    public AiSkillZipParser aiSkillZipParser(ObjectMapper objectMapper) {
        return new AiSkillZipParser(objectMapper);
    }

    
    @Bean
    @ConditionalOnMissingBean
    public AiAbTestService aiAbTestService() {
        return new AiAbTestServiceImpl();
    }


    @Bean
    @ConditionalOnMissingBean
    public AiAgentCapabilityService aiAgentCapabilityService(ObjectMapper objectMapper) {
        return new AiAgentCapabilityServiceImpl(objectMapper);
    }

    
    @Bean
    @ConditionalOnMissingBean
    public AiMcpConfigService aiMcpConfigService() {
        return new AiMcpConfigServiceImpl();
    }


    @Bean
    @ConditionalOnMissingBean
    public AiApiDocService aiApiDocService(ObjectMapper objectMapper) {
        return new AiApiDocServiceImpl(objectMapper);
    }

}
