package com.zimo.module.tools.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.ai.skill.AiSkillRegistry;
import com.zimo.framework.ai.sandbox.HttpRemoteSandboxBackend;
import com.zimo.framework.ai.sandbox.LocalSandboxBackend;
import com.zimo.framework.ai.sandbox.SandboxBackend;
import com.zimo.module.tools.mapper.ToolAgentPermissionMapper;
import com.zimo.module.tools.mapper.ToolDefinitionMapper;
import com.zimo.module.tools.mapper.ToolInvokeLogMapper;
import com.zimo.module.tools.mapper.ToolPluginMapper;
import com.zimo.module.tools.service.ToolPluginService;
import com.zimo.module.tools.mapper.ToolPythonScriptMapper;
import com.zimo.module.tools.service.ToolScriptService;
import com.zimo.module.tools.ToolsProperties;
import com.zimo.module.tools.govern.ToolGovernanceService;
import com.zimo.module.tools.govern.ToolGovernor;
import com.zimo.module.tools.govern.ToolRegistry;
import com.zimo.module.tools.sdk.ToolSdk;
import com.zimo.module.tools.tool.CalcTool;
import com.zimo.module.tools.tool.CodeTool;
import com.zimo.module.tools.tool.EmailTool;
import com.zimo.module.tools.tool.FileTool;
import com.zimo.module.tools.tool.HttpTool;
import com.zimo.module.tools.tool.ScheduleTool;
import com.zimo.module.tools.tool.TableTool;
import com.zimo.module.tools.tool.TransformTool;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.StringUtils;

/**
 * 通用内置工具库自动装配。
 *
 * <p>内置工具实现 {@code AiSkill} 自动进入技能注册表；治理层（权限/限流/重试/熔断/日志）、
 * 插件市场与自定义脚本通过 {@link ToolGovernanceService} 接入 MCP（{@code ToolBridge}）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
@AutoConfiguration
@MapperScan("com.zimo.module.tools.mapper")
@EnableConfigurationProperties(ToolsProperties.class)
public class ToolsAutoConfiguration {

    /** 沙箱后端：配置远程沙箱基址时用远程，否则本地直通（对应 dsh ctx.sandbox 可切换）。 */
    @Bean
    @ConditionalOnMissingBean
    public SandboxBackend sandboxBackend(ToolsProperties properties) {
        if (StringUtils.hasText(properties.getRemoteSandboxUrl())) {
            return new HttpRemoteSandboxBackend(properties.getRemoteSandboxUrl(),
                    properties.getRemoteSandboxToken(),
                    properties.getRemoteSandboxTimeoutSeconds());
        }
        return new LocalSandboxBackend(properties.getSandboxTimeoutSeconds());
    }

    /** 沙箱文件系统：与命令执行共享同一沙箱边界（dsh A7 共享执行世界）。 */
    @Bean
    @ConditionalOnMissingBean
    public com.zimo.framework.ai.sandbox.SandboxFileSystem sandboxFileSystem(
            ToolsProperties properties) {
        if (StringUtils.hasText(properties.getRemoteSandboxUrl())) {
            return new com.zimo.framework.ai.sandbox.HttpRemoteSandboxFileSystem(
                    properties.getRemoteSandboxUrl(),
                    properties.getRemoteSandboxToken(),
                    properties.getRemoteSandboxTimeoutSeconds());
        }
        return new com.zimo.framework.ai.sandbox.LocalSandboxFileSystem(
                properties.getFileWorkspace(),
                properties.getSandboxFileAllowedExtensions());
    }

    /** 定时任务调度器（供定时任务工具使用）。 */
    @Bean
    @ConditionalOnMissingBean
    public TaskScheduler toolTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("tool-schedule-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        return new ToolRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolGovernor toolGovernor() {
        return new ToolGovernor();
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolGovernanceService toolGovernanceService(
            ToolRegistry registry,
            ToolGovernor governor,
            ToolAgentPermissionMapper permissionMapper,
            ToolDefinitionMapper definitionMapper,
            ToolInvokeLogMapper invokeLogMapper,
            AiSkillRegistry skillRegistry,
            ObjectMapper objectMapper,
            java.util.Optional<com.zimo.framework.common.security.SecurityFacade> security) {
        return new ToolGovernanceService(registry, governor, permissionMapper,
                definitionMapper, invokeLogMapper, skillRegistry, objectMapper, security);
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolPluginService toolPluginService(
            ToolPluginMapper pluginMapper,
            ToolDefinitionMapper definitionMapper,
            ToolRegistry registry,
            ObjectMapper objectMapper,
            ToolsProperties properties) {
        return new ToolPluginService(pluginMapper, definitionMapper, registry, objectMapper,
                StringUtils.hasText(properties.getPluginBaseUrl())
                        ? properties.getPluginBaseUrl() : "http://localhost:9900");
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolScriptService toolScriptService(
            ToolPythonScriptMapper scriptMapper,
            ToolRegistry registry,
            ToolsProperties properties,
            SandboxBackend sandboxBackend) {
        return new ToolScriptService(scriptMapper, registry, properties.getPythonBin(), sandboxBackend);
    }

    /** 初始化：挂载 ToolSdk 注册表 + 写入预置行业插件种子 + 恢复已启用工具。 */
    @Bean
    @ConditionalOnMissingBean
    public ToolSdkInitializer toolSdkInitializer(ToolRegistry registry,
                                                 ToolPluginService pluginService,
                                                 ToolScriptService scriptService) {
        return new ToolSdkInitializer(registry, pluginService, scriptService);
    }

    /** 内置工具（AiSkill，自动进入技能注册表）。 */
    @Bean
    @ConditionalOnMissingBean
    public CalcTool calcTool() {
        return new CalcTool();
    }

    @Bean
    @ConditionalOnMissingBean
    public FileTool fileTool(ToolsProperties properties) {
        return new FileTool(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TransformTool transformTool() {
        return new TransformTool();
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpTool httpTool() {
        return new HttpTool();
    }

    @Bean
    @ConditionalOnMissingBean
    public TableTool tableTool() {
        return new TableTool();
    }

    @Bean
    @ConditionalOnMissingBean
    public EmailTool emailTool(ToolsProperties properties) {
        return new EmailTool(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ScheduleTool scheduleTool(TaskScheduler taskScheduler) {
        return new ScheduleTool(taskScheduler);
    }

    @Bean
    @ConditionalOnMissingBean
    public CodeTool codeTool(ToolsProperties properties, SandboxBackend sandboxBackend) {
        return new CodeTool(properties, sandboxBackend);
    }

    /** 初始化器：挂载 SDK + 播种插件 + 恢复启用工具。 */
    static class ToolSdkInitializer {
        ToolSdkInitializer(ToolRegistry registry, ToolPluginService pluginService, ToolScriptService scriptService) {
            ToolSdk.attach(registry);
            pluginService.initSeedPlugins();
            pluginService.restoreEnabled();
            scriptService.restoreEnabled();
        }
    }
}
