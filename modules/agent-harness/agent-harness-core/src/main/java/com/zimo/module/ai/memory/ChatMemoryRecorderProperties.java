package com.zimo.module.ai.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 「每轮对话写入 agent-memory」的记录器配置。
 *
 * <p>记录走的是 agent-memory 暴露的 **MCP 端点**（{@code POST /api/agent-memory/mcp}，
 * JSON-RPC 2.0 + {@code tools/call}），而不是进程内直调其 Service —— 这样
 * {@code agent-harness} 与 {@code agent-memory} 保持零编译耦合（后者带 arrow / calcite /
 * h2 等重依赖），且写入路径与 WorkBuddy 等外部 MCP 客户端**完全同一条契约**，
 * 不存在「外部调用被校验、内部调用走捷径」的双标。</p>
 *
 * <p>配置前缀 {@code ai.chat-recorder}。</p>
 *
 * @author WorkBuddy
 * @since 2026-09-19
 */
@ConfigurationProperties(prefix = "ai.chat-recorder")
public class ChatMemoryRecorderProperties {

    /**
     * 总开关。关闭后对话照常，只是不再产生记忆写入 —— 便于对照实验：
     * 关掉它必须能观察到「记忆没有增长」，否则断言本身没有鉴别力。
     */
    private boolean enabled = true;

    /**
     * MCP 端点地址。留空时自动推导为 {@code http://127.0.0.1:<实际监听端口>/api/agent-memory/mcp}。
     *
     * <p>刻意取**实际监听端口**而非配置里的 {@code server.port}：本项目支持用环境变量
     * （如 {@code SERVER__PORT}）覆盖端口，读配置值会指向一个没人监听的端口，
     * 表现为「记录静默失败」。</p>
     */
    private String url = "";

    /**
     * MCP 端点访问令牌。留空时回落到 {@code agent-memory.mcp-token}。
     *
     * <p>两端必须一致：agent-memory 侧配了令牌就只认令牌、不再放行回环来源，
     * 因此本端漏配会直接拿到 401 —— 记忆一条都写不进去。</p>
     */
    private String token = "";

    /** 建立连接超时（毫秒）。 */
    private int connectTimeoutMs = 1000;

    /** 读取响应超时（毫秒）。刻意短：记录是旁路，宁可丢一条也不能拖住对话。 */
    private int readTimeoutMs = 3000;

    /** 单侧（用户消息 / 助手回复）最大记录字符数，超出截断并标注。 */
    private int maxChars = 4000;

    /** 异步写入队列容量。满则丢弃并告警，**绝不阻塞**对话线程。 */
    private int queueCapacity = 512;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getMaxChars() {
        return maxChars;
    }

    public void setMaxChars(int maxChars) {
        this.maxChars = maxChars;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
}
