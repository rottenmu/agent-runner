package com.zimo.module.ai.memory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把每一轮对话写入 agent-memory 的记录器（经 MCP {@code memory_write}，target=session）。
 *
 * <h2>记录为什么是旁路而不是主流程</h2>
 * <p>用户的诉求是「每个聊天会话都记录起来」，但**记录失败绝不能让对话失败**：记忆服务是
 * 可选的旁路设施，让它决定「能不能聊天」是把可用性绑在一个非关键依赖上。因此这里：
 * 提交到有界队列后立即返回；队列满就丢弃并告警；任何异常只落 WARN 日志。
 * 反过来，若把写记忆串在对话线程里，模型响应之外还要等一次 HTTP，
 * 且下游抖动会直接变成用户可见的对话变慢。</p>
 *
 * <h2>写什么</h2>
 * <p>一轮记为一条会话变量（{@code target=session}）：{@code [user] … / [assistant] …}。
 * 与 {@code AiConversationMemory.appendTurn(sessionId, user, assistant, max)} 的语义对齐 ——
 * 项目里「一轮对话」的口径就是这一对消息，不额外发明结构。</p>
 *
 * <p>⚠️ {@code saveSessionVar} 是**追加**语义（每次生成新 id），不会按 key 覆盖，
 * 所以每轮用**独立 key** 是正确用法，不要试图用固定 key 做「每会话一条」。</p>
 *
 * @author WorkBuddy
 * @since 2026-09-19
 */
public class ChatTurnMemoryRecorder {

    private static final Logger log = LoggerFactory.getLogger(ChatTurnMemoryRecorder.class);

    /** MCP 工具名（与 {@code MemoryMcpToolkit} 的工具清单一一致） */
    private static final String TOOL_WRITE = "memory_write";

    private final MemoryMcpClient client;
    private final ChatMemoryRecorderProperties properties;
    private final ThreadPoolExecutor executor;
    private final AtomicLong turnSeq = new AtomicLong();

    public ChatTurnMemoryRecorder(MemoryMcpClient client, ChatMemoryRecorderProperties properties) {
        this.client = client;
        this.properties = properties;
        int capacity = Math.max(1, properties.getQueueCapacity());
        this.executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "chat-memory-recorder");
                    // 守护线程：记录是旁路，不应阻止 JVM 退出
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 记录一轮对话。**立即返回**，不做网络调用，不抛异常。
     *
     * @param tenantId        租户标识（须与读取侧一致，否则写进去也读不出来）
     * @param userId          用户标识
     * @param sessionId       会话标识；空白则跳过（无会话维度就无处归集）
     * @param userMessage     用户消息
     * @param assistantReply  助手回复
     */
    public void record(String tenantId, String userId, String sessionId,
                       String userMessage, String assistantReply) {
        if (!properties.isEnabled()) {
            return;
        }
        if (isBlank(sessionId)) {
            return;
        }
        String content = compose(userMessage, assistantReply);
        if (content.isEmpty()) {
            return;
        }
        long seq = turnSeq.incrementAndGet();
        try {
            executor.execute(() -> write(tenantId, userId, sessionId, seq, content));
        } catch (Exception e) {
            // 队列满（AbortPolicy）或已关闭：丢弃并告警。丢一条记忆远好过拖慢或中断对话
            log.warn("[对话记忆] 入队失败，本轮不记录 (sessionId={}): {}", sessionId, e.toString());
        }
    }

    /** 真正执行 MCP 写入；只在本类的单线程池里跑。 */
    private void write(String tenantId, String userId, String sessionId, long seq, String content) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("target", "session");
        args.put("sessionId", sessionId);
        args.put("tenantId", tenantId);
        args.put("userId", userId);
        args.put("key", "chat-" + System.currentTimeMillis() + "-" + seq);
        args.put("content", content);
        try {
            Map<String, Object> result = client.callTool(TOOL_WRITE, args);
            log.debug("[对话记忆] 已记录 sessionId={} key={} sensitiveMasked={}",
                    sessionId, args.get("key"), result.get("sensitiveMasked"));
        } catch (Exception e) {
            // 记录失败不影响对话，但必须留下线索，否则「记忆页一直是空的」无从排查
            log.warn("[对话记忆] 写入失败 sessionId={}：{}", sessionId, e.getMessage());
        }
    }

    /** 组装记录正文：按侧截断，空侧不出行。 */
    private String compose(String userMessage, String assistantReply) {
        StringBuilder sb = new StringBuilder();
        appendLine(sb, "[user] ", userMessage);
        appendLine(sb, "[assistant] ", assistantReply);
        return sb.toString();
    }

    private void appendLine(StringBuilder sb, String prefix, String text) {
        if (isBlank(text)) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append('\n');
        }
        sb.append(prefix).append(truncate(text.trim()));
    }

    /** 按配置上限截断单侧内容，并显式标注 —— 无声截断会让人以为原始回复就这么短。 */
    private String truncate(String text) {
        int max = properties.getMaxChars();
        if (max <= 0 || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "…[已截断，原文 " + text.length() + " 字]";
    }

    /** 关闭线程池（应用下线时调用）。 */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
