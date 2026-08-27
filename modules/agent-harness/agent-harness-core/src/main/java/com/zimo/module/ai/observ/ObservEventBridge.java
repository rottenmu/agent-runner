package com.zimo.module.ai.observ;

import com.zimo.framework.common.ai.event.AgentStepEvent;
import com.zimo.framework.common.ai.event.ConversationTurnEvent;
import com.zimo.framework.common.ai.event.ToolCallEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

/**
 * 遥测事件桥（⑪）：订阅事件总线（会话/Agent/工具三类域）并写入 Observ 追踪，
 * 解耦业务代码与 trace 落库。
 *
 * <p>Agent 步骤事件映射为 observ trace step（stepType=agent，phase=begin/end）；<br>
 * 会话事件创建/更新 trace（以 sessionId 为键）；工具事件记录 tool step。</p>
 */
public class ObservEventBridge {

    private static final Logger log = LoggerFactory.getLogger(ObservEventBridge.class);

    private final ObservTraceService traceService;
    /** sessionId → traceId 映射（会话事件自动建 trace 用）。 */
    private final Map<String, String> sessionTraces = new ConcurrentHashMap<>();

    public ObservEventBridge(ObservTraceService traceService) {
        this.traceService = traceService;
    }

    /** Agent 步骤事件 → observ step。 */
    @EventListener
    public void onAgentStep(AgentStepEvent event) {
        try {
            String traceId = sessionTraces.computeIfAbsent(
                    nullSafe(event.sessionId()), k -> "evt-" + k + "-" + System.currentTimeMillis());
            int seq = "begin".equals(event.phase()) ? 1 : 2;
            traceService.onStep(traceId, seq, "agent", event.phase(),
                    event.prompt(), event.response(), 0L, event.status());
        } catch (Exception e) {
            log.warn("遥测桥 AgentStep 记录失败: {}", e.getMessage());
        }
    }

    /** 会话事件 → 建 trace（每会话一条，标题即首条用户消息）。 */
    @EventListener
    public void onConversation(ConversationTurnEvent event) {
        try {
            String traceId = sessionTraces.computeIfAbsent(
                    nullSafe(event.sessionId()), k -> "evt-" + k + "-" + System.currentTimeMillis());
            traceService.onBegin(traceId, event.sessionId(), "agent", "conversation",
                    event.userMessage(), "event");
        } catch (Exception e) {
            log.warn("遥测桥 Conversation 记录失败: {}", e.getMessage());
        }
    }

    /** 工具事件 → observ tool step。 */
    @EventListener
    public void onTool(ToolCallEvent event) {
        try {
            // Plan Mode 工具（plan_enter/write/exit/todo_write）归类为 plan 事件，
            // 便于终态判定（是否规划过/是否退出）
            boolean planTool = isPlanTool(event.toolName());
            String stepType = planTool ? "plan" : "tool";
            String stepName = event.toolName() + (planTool ? ":" + event.phase() : "");
            traceService.onStep(stepType + "-" + stepName + "-" + event.ts(),
                    0, stepType, event.phase(), String.valueOf(event.params()),
                    event.result() == null ? event.error() : String.valueOf(event.result()),
                    0L, event.error() == null ? "ok" : "error");
        } catch (Exception e) {
            log.warn("遥测桥 Tool 记录失败: {}", e.getMessage());
        }
    }

    private static boolean isPlanTool(String toolName) {
        return toolName != null && (toolName.startsWith("plan_") || "todo_write".equals(toolName));
    }

    private static String nullSafe(String s) {
        return s == null ? "default" : s;
    }
}
