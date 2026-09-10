package com.zimo.module.ai.collab;

import com.zimo.module.ai.observ.SessionEventLog;
import com.zimo.module.ai.observ.SessionEventLogService;
import com.zimo.framework.ai.AiAgentReply;
import com.zimo.framework.ai.AiAgentService;
import com.zimo.framework.ai.agent.AiAgentProfile;
import com.zimo.framework.ai.agent.AiAgentRouteRequest;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * 会话键级 fork/resume 服务（对齐 DeepSeek Harness A3 派生能力）。
 *
 * <p>fork：从来源会话（conversationId 或 traceId）重建对话历史 → 派生<b>新 conversationId</b>
 * （新会话键）→ 带历史上下文执行续跑。原会话不变，分叉链独立演进。
 * <br>resume：从同一 conversationId 重建历史 → 用<b>相同会话键</b>断点续跑，
 * 新回复与历史上下文连贯，不新建会话。</p>
 *
 * <p>上下文完全派生自 append-only 事件日志（observ_session_event），不依赖进程内记忆，
 * 可跨实例、跨重启还原。fork 出的新链路通过 TraceCollector 独立打点。</p>
 */
public class SessionForkResumeService {

    /** fork 派生会话后缀标记。 */
    public static final String FORK_SUFFIX = "-fork-";
    /** 历史消息条数上限（防止上下文爆炸）。 */
    private static final int MAX_HISTORY = 60;

    private final SessionEventLogService eventLogService;
    private final AiAgentService aiAgentService;
    private final com.zimo.module.ai.management.AiAgentManagementService agentManagement;

    public SessionForkResumeService(SessionEventLogService eventLogService,
                                    AiAgentService aiAgentService,
                                    com.zimo.module.ai.management.AiAgentManagementService agentManagement) {
        this.eventLogService = eventLogService;
        this.aiAgentService = aiAgentService;
        this.agentManagement = agentManagement;
    }

    /* ================= 对外 API ================= */

    /**
     * 分叉：从来源会话派生新会话并续跑。
     *
     * @param sourceKey 来源会话 ID（或 traceId）
     * @param message 分叉后的第一条消息
     * @param tenantId 租户
     * @param userId 用户
     * @param agentId 智能体 ID（必须已启用）
     * @return fork 结果（含新会话 ID / 回复 / 历史条数）
     */
    public Map<String, Object> fork(String sourceKey, String message,
                                    String tenantId, String userId, String agentId) {
        validate(sourceKey, message, agentId);
        List<ReplayMessage> history = rebuildHistory(sourceKey);
        String newSessionId = sourceKey + FORK_SUFFIX + shortUuid();
        AiAgentProfile profile = resolveProfile(tenantId, agentId);
        AiAgentRouteRequest route = new AiAgentRouteRequest(
                tenantId, "console", userId, newSessionId, profile, null);
        AiAgentReply reply = aiAgentService.chatWithHistory(
                message, route, toAgentMessages(history));
        return result("fork", sourceKey, newSessionId, reply, history.size());
    }

    /**
     * 续跑：从同一会话断点续跑（不新建会话）。
     *
     * @param sessionId 会话 ID（与来源一致）
     * @param message 续跑消息
     * @param tenantId 租户
     * @param userId 用户
     * @param agentId 智能体 ID
     * @return resume 结果
     */
    public Map<String, Object> resume(String sessionId, String message,
                                      String tenantId, String userId, String agentId) {
        validate(sessionId, message, agentId);
        List<ReplayMessage> history = rebuildHistory(sessionId);
        AiAgentProfile profile = resolveProfile(tenantId, agentId);
        AiAgentRouteRequest route = new AiAgentRouteRequest(
                tenantId, "console", userId, sessionId, profile, null);
        AiAgentReply reply = aiAgentService.chatWithHistory(
                message, route, toAgentMessages(history));
        return result("resume", sessionId, sessionId, reply, history.size());
    }

    /**
     * 预览：重建指定会话的对话历史（供前端展示）。
     *
     * @param sessionId 会话 ID（或 traceId）
     * @return 重建历史（role/content 列表 + 统计）
     */
    public Map<String, Object> preview(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return emptyPreview(sessionId);
        }
        List<ReplayMessage> history = rebuildHistory(sessionId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("count", history.size());
        data.put("messages", ReplayMessage.toDto(history));
        data.put("userCount", history.stream().filter(m -> "user".equals(m.role())).count());
        data.put("assistantCount", history.stream().filter(m -> "assistant".equals(m.role())).count());
        return data;
    }

    /* ================= 历史重建（事件日志派生） ================= */

    /**
     * 从事件日志重建"模型可见"消息序列。
     *
     * <p>优先按 traceId 精确取流；无则按 sessionId 跨 trace 聚合。
     * END 事件（agent_reply，inputText=prompt / outputText=response）构成用户/助手对；
     * 无 END 的链路退化取 generation STEP 对。</p>
     */
    public List<ReplayMessage> rebuildHistory(String sourceKey) {
        List<ReplayMessage> result = new ArrayList<>();
        if (!StringUtils.hasText(sourceKey)) {
            return result;
        }
        List<SessionEventLog> events = eventLogService.listByTraceId(sourceKey);
        if (events == null || events.isEmpty()) {
            events = eventLogService.listBySessionId(sourceKey);
        }
        if (events == null || events.isEmpty()) {
            return result;
        }
        String lastUser = null;
        for (SessionEventLog event : events) {
            if (event == null) {
                continue;
            }
            String eventType = event.getEventType();
            if (SessionEventLog.TYPE_END.equals(eventType)) {
                String prompt = trim(event.getInputText());
                String response = trim(event.getOutputText());
                if (hasText(prompt)) {
                    if (lastUser != null && !lastUser.equals(prompt)) {
                        result.add(new ReplayMessage("user", lastUser));
                    }
                    result.add(new ReplayMessage("user", prompt));
                    result.add(new ReplayMessage("assistant", hasText(response) ? response : ""));
                } else if (hasText(response)) {
                    result.add(new ReplayMessage("assistant", response));
                }
                lastUser = null;
            } else if (SessionEventLog.TYPE_STEP.equals(eventType)
                    && "generation".equals(event.getStepType())) {
                String prompt = trim(event.getInputText());
                String response = trim(event.getOutputText());
                if (hasText(prompt)) {
                    result.add(new ReplayMessage("user", prompt));
                    result.add(new ReplayMessage("assistant", hasText(response) ? response : ""));
                }
                lastUser = null;
            } else if (SessionEventLog.TYPE_BEGIN.equals(eventType)
                    && !hasAnyEnd(events)) {
                // 无 END 时用 BEGIN 输入兜底首条
                String input = trim(event.getInputText());
                if (hasText(input)) {
                    lastUser = input;
                }
            }
        }
        return trimHistory(result);
    }

    /* ================= 工具方法 ================= */

    private void validate(String source, String message, String agentId) {
        if (!StringUtils.hasText(source)) {
            throw new IllegalArgumentException("source sessionId/traceId 不能为空");
        }
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        if (!StringUtils.hasText(agentId)) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
    }

    private AiAgentProfile resolveProfile(String tenantId, String agentId) {
        if (!StringUtils.hasText(agentId)) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
        com.zimo.module.ai.management.AiManagedAgent agent =
                agentManagement == null ? null
                        : agentManagement.findEnabledAgentById(agentId).orElse(null);
        if (agent == null && agentManagement != null) {
            throw new IllegalArgumentException("智能体不存在或未启用: " + agentId);
        }
        if (agent != null) {
            String tenant = StringUtils.hasText(agent.tenantId()) ? agent.tenantId()
                    : (StringUtils.hasText(tenantId) ? tenantId : "_");
            return new AiAgentProfile(
                    agent.id(), tenant, agent.name(), agent.model(), agent.persona(),
                    agent.skillIds(), agent.agentType(), agent.agentConfig(), true);
        }
        // 无管理服务（测试场景）退化为轻量 profile
        return new AiAgentProfile(
                agentId, StringUtils.hasText(tenantId) ? tenantId : "_",
                agentId, null, null, List.of(), true);
    }

    private boolean hasAnyEnd(List<SessionEventLog> events) {
        for (SessionEventLog event : events) {
            if (event != null && SessionEventLog.TYPE_END.equals(event.getEventType())) {
                return true;
            }
        }
        return false;
    }

    private List<Msg> toAgentMessages(List<ReplayMessage> history) {
        List<Msg> msgs = new ArrayList<>();
        if (history == null) {
            return msgs;
        }
        for (ReplayMessage message : history) {
            if (!hasText(message.content())) {
                continue;
            }
            MsgRole role = "user".equals(message.role()) ? MsgRole.USER : MsgRole.ASSISTANT;
            Msg.Builder builder = Msg.builder().role(role).textContent(message.content());
            if ("user".equals(message.role())) {
                builder.name("user");
            }
            msgs.add(builder.build());
        }
        return msgs;
    }

    private Map<String, Object> result(String mode, String source, String target,
                                       AiAgentReply reply, int historyCount) {
        Map<String, Object> data = new LinkedHashMap<>();
        String content = reply == null ? "智能体未返回结果" : reply.content();
        data.put("mode", mode);
        data.put("sourceKey", source);
        data.put("conversationId", target);
        data.put("reply", content);
        data.put("success", content != null && !content.startsWith("AI 智能体"));
        data.put("historyCount", historyCount);
        return data;
    }

    private Map<String, Object> emptyPreview(String sessionId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("count", 0);
        data.put("messages", List.of());
        data.put("userCount", 0);
        data.put("assistantCount", 0);
        return data;
    }

    private List<ReplayMessage> trimHistory(List<ReplayMessage> messages) {
        if (messages.size() <= MAX_HISTORY) {
            return messages;
        }
        return new ArrayList<>(messages.subList(
                messages.size() - MAX_HISTORY, messages.size()));
    }

    private String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    /** 重建的消息条目。 */
    public record ReplayMessage(String role, String content) {

        private static List<Map<String, String>> toDto(List<ReplayMessage> messages) {
            List<Map<String, String>> dto = new ArrayList<>();
            if (messages == null) {
                return dto;
            }
            for (ReplayMessage message : messages) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("role", message.role());
                entry.put("content", message.content());
                dto.add(entry);
            }
            return dto;
        }
    }
}