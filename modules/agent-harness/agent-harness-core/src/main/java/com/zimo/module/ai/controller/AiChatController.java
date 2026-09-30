package com.zimo.module.ai.controller;

import com.zimo.framework.ai.AiAgentReply;
import com.zimo.framework.ai.AiAgentService;
import com.zimo.framework.ai.agent.AiAgentRouteRequest;
import com.zimo.framework.common.ApiResponse;
import com.zimo.framework.common.security.SecurityFacade;
import com.zimo.module.ai.memory.ChatTurnMemoryRecorder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能体对话调试接口：按智能体 ID 发起对话（支持全部 5 种智能体类型）。
 *
 * <p>集成安全管控：对话前资源权限校验（agent:execute）与敏感词拦截，对话后输出脱敏与审计留痕。
 * 全部前置闸门集中在 {@link AiChatPreflight}，与流式端点 {@link AiChatStreamController}
 * 共用同一份实现 —— 新增通道不得绕过任何一道校验。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
@RestController
@RequestMapping("/api/biz/ai/chat")
public class AiChatController {

    private final AiAgentService aiAgentService;
    private final AiChatPreflight preflight;
    private final Optional<SecurityFacade> security;
    private final ChatTurnMemoryRecorder memoryRecorder;

    public AiChatController(AiAgentService aiAgentService,
                            AiChatPreflight preflight,
                            Optional<SecurityFacade> security,
                            ChatTurnMemoryRecorder memoryRecorder) {
        this.aiAgentService = aiAgentService;
        this.preflight = preflight;
        this.security = security;
        this.memoryRecorder = memoryRecorder;
    }

    /**
     * 与指定智能体对话（同步返回完整回复）。
     *
     * @param body 包含 agentId（必填）、message（必填）、sessionId（可空，同会话携带记忆）、
     *             tenantId/userId（可空）
     * @return { reply, agent, agentType, agentName, intent }
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        AiChatPreflight.Result pre = preflight.run(body);
        if (pre.shortCircuited()) {
            // 意图追问 / 二次确认 / 技能澄清冲突：闸门已给出完整回复，不进入模型
            Map<String, Object> shortCircuit = pre.shortCircuit();
            memoryRecorder.record(pre.tenantId(), pre.userId(), pre.sessionId(),
                    body.get("message"), String.valueOf(shortCircuit.get("reply")));
            return ApiResponse.ok(shortCircuit);
        }
        String message = body.get("message");
        String userId = pre.userId();
        String agentId = pre.agent().id();

        AiAgentRouteRequest route = new AiAgentRouteRequest(
                pre.tenantId(), "console", userId, pre.sessionId(), pre.profile(), null);
        AiAgentReply reply = aiAgentService.chat(message, route);

        // 安全：输出脱敏 + 审计
        String content = reply == null ? "" : reply.content();
        if (security.isPresent()) {
            content = security.get().sanitizeOutput(content);
            security.get().audit("user", userId, "chat.send", "agent", agentId,
                    "{\"msg\":\"" + safeJson(message) + "\"}", true);
        }
        // 记到 agent-memory 的是**脱敏后**的正文：记忆会被注入后续对话上下文，
        // 存原文等于把敏感信息绕开脱敏复制到另一个会进 prompt 的地方
        memoryRecorder.record(pre.tenantId(), userId, pre.sessionId(), message, content);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", content);
        result.put("agent", reply == null ? "" : reply.agent());
        result.put("agentType", pre.agent().agentType());
        result.put("agentName", pre.agent().name());
        if (pre.skillRouteHint() != null) {
            Map<String, Object> skillMeta = new LinkedHashMap<>();
            skillMeta.put("skillName", pre.skillRouteHint());
            result.put("skillRoute", skillMeta);
        }
        if (pre.intent() != null) {
            Map<String, Object> intentMeta = new LinkedHashMap<>();
            intentMeta.put("intentCode", pre.intent().intentCode());
            intentMeta.put("intentName", pre.intent().intentName());
            intentMeta.put("confidence", pre.intent().confidence());
            intentMeta.put("needClarify", pre.intent().needClarify());
            intentMeta.put("needTool", pre.intent().needTool());
            intentMeta.put("routeStrategy", pre.intent().routeStrategy());
            intentMeta.put("requiredSlotMissing", pre.intent().requiredSlotMissing());
            result.put("intent", intentMeta);
        }
        return ApiResponse.ok(result);
    }

    private static String safeJson(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("\n", " ");
    }
}
