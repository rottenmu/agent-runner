package com.zimo.module.ai.autoconfig;

import com.zimo.module.ai.memory.ChatMemoryRecorderProperties;
import com.zimo.module.ai.memory.ChatTurnMemoryRecorder;
import com.zimo.module.ai.memory.MemoryMcpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * 「每轮对话写入 agent-memory」的装配。
 *
 * <p>开关条件与 {@link AiControllerScanAutoConfiguration} **完全一致**：该装配依赖的
 * 控制器（{@code AiChatController} / {@code AiChatStreamController}）只在这两个开关都打开时
 * 才被扫描注册，此处若用不同条件，会出现「控制器在、记录器不在」的启动失败。</p>
 *
 * <p>注意：Bean **无条件注册**（不看 {@code ai.chat-recorder.enabled}），
 * 由 {@link ChatTurnMemoryRecorder#record} 内部判断开关。理由：控制器用构造器注入它，
 * 条件化注册会让「关掉记录」直接变成「应用起不来」，而关掉记录本应是一个
 * 安全、可随时执行的运维动作（也是对照实验的手段）。</p>
 *
 * @author WorkBuddy
 * @since 2026-09-19
 */
@AutoConfiguration
@ConditionalOnExpression("${plugin.ai.enabled:true} && ${ai.agent.enabled:true}")
@EnableConfigurationProperties(ChatMemoryRecorderProperties.class)
public class AiChatMemoryAutoConfiguration {

    /** agent-memory MCP 端点路径（与 {@code MemoryMcpEndpoint} 的映射一致） */
    private static final String MCP_PATH = "/api/agent-memory/mcp";

    /**
     * MCP 客户端。令牌优先级：{@code ai.chat-recorder.token} ＞ {@code agent-memory.mcp-token}。
     *
     * <p>回落到 {@code agent-memory.mcp-token} 是必要的：agent-memory 侧一旦配置了令牌，
     * 准入就从「回环放行」切换为「只认令牌」，本端漏配会稳定拿到 401。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public MemoryMcpClient memoryMcpClient(
            ChatMemoryRecorderProperties properties,
            ApplicationContext applicationContext,
            @Value("${agent-memory.mcp-token:}") String agentMemoryToken,
            @Value("${server.port:9900}") String configuredPort) {
        String token = hasText(properties.getToken()) ? properties.getToken() : agentMemoryToken;
        return new MemoryMcpClient(properties, token,
                () -> resolveEndpoint(applicationContext, configuredPort));
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public ChatTurnMemoryRecorder chatTurnMemoryRecorder(
            MemoryMcpClient memoryMcpClient, ChatMemoryRecorderProperties properties) {
        return new ChatTurnMemoryRecorder(memoryMcpClient, properties);
    }

    /**
     * 推导 MCP 端点地址。
     *
     * <p>优先取 **Web 容器实际监听端口**：本项目支持用环境变量覆盖 {@code server.port}
     * （例如 {@code SERVER__PORT=58090}），读配置值会指向一个没人监听的端口，
     * 现象是「记录全部静默失败而对话一切正常」。</p>
     */
    private String resolveEndpoint(ApplicationContext applicationContext, String configuredPort) {
        if (applicationContext instanceof ServletWebServerApplicationContext webContext) {
            WebServer webServer = webContext.getWebServer();
            if (webServer != null && webServer.getPort() > 0) {
                return "http://127.0.0.1:" + webServer.getPort() + MCP_PATH;
            }
        }
        return "http://127.0.0.1:" + configuredPort + MCP_PATH;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
