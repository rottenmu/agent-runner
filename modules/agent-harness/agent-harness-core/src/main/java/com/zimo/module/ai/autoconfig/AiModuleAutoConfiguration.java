package com.zimo.module.ai.autoconfig;

import com.zimo.module.ai.AiPluginRegister;
import com.zimo.module.ai.skill.AiPluginStatusSkill;
import com.zimo.framework.ai.autoconfig.AiAgentAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * AI 插件基础自动装配。
 *
 * <p>该配置受 {@code plugin.ai.enabled} 开关控制，负责注册 AI 插件身份和模块状态技能等基础 Bean。
 * 配置顺序早于 {@link AiAgentAutoConfiguration}，让模块状态技能可以参与 starter 的技能注册流程。</p>
 *
 * <p>本配置不使用全包扫描注册 Controller；管理端 Bean 由 {@link AiSkillAdminAutoConfiguration} 在 starter 运行时能力就绪后统一装配。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
@Slf4j
@AutoConfiguration(before = AiAgentAutoConfiguration.class)
@ConditionalOnProperty(prefix = "plugin.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AiProperties.class)
public class AiModuleAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public AiPluginRegister aiPluginRegister() {
        return new AiPluginRegister();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiPluginStatusSkill aiPluginStatusSkill() {
        return new AiPluginStatusSkill();
    }


    /** Plan Mode 管理控制器（AiAgentService 存在时注册）。 */
    @org.springframework.context.annotation.Bean
    public com.zimo.module.ai.plan.AiPlanAdminController aiPlanAdminController(
            org.springframework.beans.factory.ObjectProvider<com.zimo.framework.ai.AiAgentService> aiAgentServiceProvider) {
        com.zimo.framework.ai.AiAgentService svc = aiAgentServiceProvider.getIfAvailable();
        if (svc == null) {
            return null;
        }
        return new com.zimo.module.ai.plan.AiPlanAdminController(svc);
    }

    /** 动态插件管理控制器（DynamicPluginManager 存在时注册，规避 bean 顺序依赖）。 */
    @org.springframework.context.annotation.Bean
    public com.zimo.module.ai.plugin.AiPluginAdminController aiPluginAdminController(
            org.springframework.beans.factory.ObjectProvider<com.zimo.framework.ai.plugin.DynamicPluginManager> pluginManagerProvider) {
        com.zimo.framework.ai.plugin.DynamicPluginManager manager = pluginManagerProvider.getIfAvailable();
        if (manager == null) {
            return null;
        }
        return new com.zimo.module.ai.plugin.AiPluginAdminController(manager);
    }
}
