package com.zimo.module.ai.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.ai.mcp.ToolBridge;
import com.zimo.framework.ai.sandbox.SandboxBackend;
import com.zimo.framework.common.security.SecurityFacade;
import com.zimo.module.ai.runtime.AgentRuntime;
import com.zimo.module.ai.runtime.RuntimeNodeHandler;
import com.zimo.module.ai.tool.LocalToolProvider;
import com.zimo.module.ai.tool.McpToolProvider;
import com.zimo.module.ai.tool.SandboxToolProvider;
import com.zimo.module.ai.tool.GraphToolInvokeLogMapper;
import com.zimo.module.ai.tool.ToolMetaMapper;
import com.zimo.module.ai.tool.ToolRegistryService;
import com.zimo.module.ai.tool.ToolRegistryServiceImpl;
import com.zimo.module.ai.tool.ToolSchemaInitializer;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 工具市场与统一运行时装配。
 *
 * <p>所属模块：agent-harness 管控面。装配三样东西：</p>
 * <ol>
 *   <li><b>工具治理层</b>：目录 + 审计表（{@link ToolSchemaInitializer}）与
 *       三个执行后端（内置 / MCP / 沙箱）；</li>
 *   <li><b>统一运行时</b>：{@link AgentRuntime}，新增通道进入模型的唯一入口；</li>
 *   <li><b>图节点桥</b>：{@link RuntimeNodeHandler}，把 agent / tool 节点接到上述两者。</li>
 * </ol>
 *
 * <p><b>为什么 Mapper 扫描用 {@code annotationClass}</b>：本包内还有
 * {@code ToolProvider} 这类接口，若按包全扫会把业务接口误注册成 MyBatis Mapper，
 * 启动即报找不到 SQL。显式限定注解可避免这个坑。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
@AutoConfiguration
@MapperScan(basePackages = "com.zimo.module.ai.tool", annotationClass = Mapper.class)
@ConditionalOnProperty(prefix = "plugin.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiToolAutoConfiguration {

    /**
     * 建表初始化器：启动时幂等创建工具目录与审计表。
     *
     * @param jdbcTemplate JDBC 模板
     * @return 建表初始化器
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolSchemaInitializer toolSchemaInitializer(JdbcTemplate jdbcTemplate) {
        return new ToolSchemaInitializer(jdbcTemplate);
    }

    /**
     * 内置工具后端（确定性工具，用于治理链路可测）。
     *
     * @return 内置工具后端
     */
    @Bean
    @ConditionalOnMissingBean
    public LocalToolProvider localToolProvider() {
        return new LocalToolProvider();
    }

    /**
     * MCP 工具后端。
     *
     * @param bridgeProvider MCP 工具桥，允许缺失
     * @return MCP 工具后端
     */
    @Bean
    @ConditionalOnMissingBean
    public McpToolProvider mcpToolProvider(ObjectProvider<ToolBridge> bridgeProvider) {
        return new McpToolProvider(bridgeProvider);
    }

    /**
     * 命令沙箱工具后端。
     *
     * @param backendProvider 沙箱后端，允许缺失
     * @return 沙箱工具后端
     */
    @Bean
    @ConditionalOnMissingBean
    public SandboxToolProvider sandboxToolProvider(ObjectProvider<SandboxBackend> backendProvider) {
        return new SandboxToolProvider(backendProvider);
    }

    /**
     * 工具市场治理服务。
     *
     * @param metaMapper 工具目录 Mapper
     * @param logMapper 审计 Mapper
     * @param providers 已装配后端列表
     * @param security 安全门面，允许缺失
     * @param objectMapper JSON 映射器
     * @return 治理服务
     */
    @Bean
    @ConditionalOnMissingBean
    public ToolRegistryService toolRegistryService(ToolMetaMapper metaMapper,
                                                   GraphToolInvokeLogMapper logMapper,
                                                   List<com.zimo.module.ai.tool.ToolProvider> providers,
                                                   ObjectProvider<SecurityFacade> security,
                                                   ObjectMapper objectMapper) {
        return new ToolRegistryServiceImpl(metaMapper, logMapper, providers, security, objectMapper);
    }

    /**
     * 统一智能体运行时。
     *
     * @param preflightProvider 对话闸门提供者
     * @param agentServiceProvider 智能体服务提供者
     * @param toolRegistry 工具市场治理服务
     * @param security 安全门面，允许缺失
     * @param memoryRecorder 记忆记录器，允许缺失
     * @return 统一运行时
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentRuntime agentRuntime(
            ObjectProvider<com.zimo.module.ai.controller.AiChatPreflight> preflightProvider,
            ObjectProvider<com.zimo.framework.ai.AiAgentService> agentServiceProvider,
            ObjectProvider<ToolRegistryService> toolRegistry,
            ObjectProvider<SecurityFacade> security,
            ObjectProvider<com.zimo.module.ai.memory.ChatTurnMemoryRecorder> memoryRecorder) {
        return new AgentRuntime(preflightProvider, agentServiceProvider,
                toolRegistry.getIfAvailable(), security, memoryRecorder);
    }

    /**
     * 图节点运行时处理器。
     *
     * @param agentRuntimeProvider 统一运行时，允许缺失
     * @param toolRegistryProvider 工具治理服务，允许缺失
     * @return 运行时节点处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public RuntimeNodeHandler runtimeNodeHandler(ObjectProvider<AgentRuntime> agentRuntimeProvider,
                                                 ObjectProvider<ToolRegistryService> toolRegistryProvider) {
        return new RuntimeNodeHandler(agentRuntimeProvider.getIfAvailable(),
                toolRegistryProvider.getIfAvailable());
    }
}
