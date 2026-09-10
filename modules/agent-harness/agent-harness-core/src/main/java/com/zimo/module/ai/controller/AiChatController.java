package com.zimo.module.ai.controller;

import com.zimo.framework.ai.AiAgentReply;
import com.zimo.framework.common.validation.ValidationUtil;
import cn.hutool.core.util.StrUtil;
import com.zimo.framework.ai.AiAgentService;
import com.zimo.framework.ai.agent.AiAgentProfile;
import com.zimo.framework.ai.agent.AiAgentRouteRequest;
import com.zimo.framework.common.ApiResponse;
import com.zimo.framework.common.security.SecurityFacade;
import com.zimo.framework.ai.intent.ConversationContext;
import com.zimo.framework.ai.intent.IntentAwareSkillRouter;
import com.zimo.framework.ai.intent.IntentRoutingDecision;
import com.zimo.intent.model.IntentParseResult;
import com.zimo.intent.service.IntentRecognitionService;
import com.zimo.module.ai.management.AiManagedAgent;
import com.zimo.module.ai.management.AiAgentManagementService;
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
 * <p>集成安全管控：对话前资源权限校验（agent:execute）与敏感词拦截，对话后输出脱敏与审计留痕。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
@RestController
@RequestMapping("/api/biz/ai/chat")
public class AiChatController {

    private final AiAgentService aiAgentService;
    private final AiAgentManagementService managementService;
    private final Optional<SecurityFacade> security;
    private final Optional<IntentRecognitionService> intentService;
    private final Optional<IntentAwareSkillRouter> skillRouter;

    public AiChatController(AiAgentService aiAgentService,
                            AiAgentManagementService managementService,
                            Optional<SecurityFacade> security,
                            Optional<IntentRecognitionService> intentService,
                            Optional<IntentAwareSkillRouter> skillRouter) {
        this.aiAgentService = aiAgentService;
        this.managementService = managementService;
        this.security = security;
        this.intentService = intentService;
        this.skillRouter = skillRouter == null ? java.util.Optional.empty() : skillRouter;
    }

    /**
     * 与指定智能体对话。
     *
     * @param body 包含 agentId（必填）、message（必填）、sessionId（可空，同会话携带记忆）、tenantId/userId（可空）
     * @return { reply, agent, agentType }
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        String agentId = body == null ? null : body.get("agentId");
        String message = body == null ? null : body.get("message");
        ValidationUtil.requireText(agentId, "agentId 不能为空");
        ValidationUtil.requireText(message, "消息不能为空");
        String sessionId = body.get("sessionId");
        String tenantId = blankTo(body.get("tenantId"), "u001");
        String userId = blankTo(body.get("userId"), "u001");

        // 安全：资源权限（agent:execute）
        if (security.isPresent() && !security.get().canAccess("agent", agentId, "execute")) {
            throw new IllegalArgumentException("无权限执行该智能体: " + agentId);
        }

        // 意图识别路由：风险拒绝 / 缺参追问 / 写操作二次确认 均不调用模型，直接返回
        IntentParseResult intent = null;
        if (intentService.isPresent()) {
            intent = intentService.get().parse(message, Map.of());
            if (intent.isReject()) {
                String rejectReason = intent.rejectReason();
                security.ifPresent(s -> s.audit("user", userId, "chat.intent_reject", "agent", agentId,
                        safeJson(rejectReason), false));
                throw new IllegalArgumentException("已拒绝该请求：" + rejectReason);
            }
            if (intent.needClarify()) {
                if ("PARAM_CLARIFY".equals(intent.intentCode())) {
                    // 必填槽位缺失 → 直接追问补全，禁止调用模型执行
                    security.ifPresent(s -> s.audit("user", userId, "chat.intent_clarify", "agent", agentId,
                            safeJson(message), true));
                    return ApiResponse.ok(intentHandledReply(agentId, agentName(body, agentId), intent,
                            "CLARIFY_REQUIRED", intent.clarifyPrompt()));
                }
                // 写操作二次确认（ORDER_OPERATE 等 TOOL_CALL_WITH_CONFIRM）→ 确认后再执行
                security.ifPresent(s -> s.audit("user", userId, "chat.intent_confirm", "agent", agentId,
                        safeJson(message), true));
                return ApiResponse.ok(intentHandledReply(agentId, agentName(body, agentId), intent,
                        "CONFIRM_REQUIRED", intent.clarifyPrompt()));
            }
        }

        // 安全：输入敏感词拦截
        if (security.isPresent()) {
            String blocked = security.get().interceptInput(message);
            if (blocked != null) {
                security.get().audit("user", userId, "chat.blocked", "agent", agentId, blocked, false);
                throw new IllegalArgumentException(blocked);
            }
        }

        // Skill 级意图准入路由（对注册了 IntentCheckableSkill 的技能集做准入判断）
        String skillRouteHint = null;
        if (skillRouter.isPresent() && !skillRouter.get().skills().isEmpty()) {
            IntentRoutingDecision skillDecision = skillRouter.get().route(
                    message, ConversationContext.of(tenantId, userId, sessionId));
            switch (skillDecision.status()) {
                case NEED_CLARIFY -> {
                    // 技能意图歧义：停止调度，向用户澄清
                    security.ifPresent(s -> s.audit("user", userId, "chat.skill_clarify", "agent", agentId,
                            safeJson(message), true));
                    return ApiResponse.ok(intentHandledReply(agentId, agentName(body, agentId),
                            skillToIntentResult(skillDecision), "SKILL_CLARIFY_REQUIRED",
                            skillDecision.clarifyReason()));
                }
                case MULTI_CONFLICT -> {
                    // 多技能冲突：返回冲突提示，由用户澄清
                    security.ifPresent(s -> s.audit("user", userId, "chat.skill_conflict", "agent", agentId,
                            safeJson(message), true));
                    return ApiResponse.ok(intentHandledReply(agentId, agentName(body, agentId),
                            skillToIntentResult(skillDecision), "SKILL_CONFLICT",
                            skillDecision.clarifyReason()));
                }
                case SINGLE -> {
                    // 单一命中：注入提示，引导模型优先调用该技能
                    skillRouteHint = skillDecision.matchedSkills().get(0).name();
                }
                case NONE -> {
                    // 全部未命中：正常对话（模型自主决定是否调用技能）
                }
            }
        }

        AiManagedAgent agent = managementService.findEnabledAgentById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("智能体不存在或未启用: " + agentId));
        AiAgentProfile profile = new AiAgentProfile(
                agent.id(),
                agent.tenantId() == null ? tenantId : agent.tenantId(),
                agent.name(),
                agent.model(),
                agent.persona(),
                agent.skillIds(),
                agent.agentType(),
                agent.agentConfig(),
                true);
        AiAgentRouteRequest route = new AiAgentRouteRequest(
                tenantId, "console", userId, sessionId, profile, null);
        AiAgentReply reply = aiAgentService.chat(message, route);

        // 安全：输出脱敏 + 审计
        String content = reply == null ? "" : reply.content();
        if (security.isPresent()) {
            content = security.get().sanitizeOutput(content);
            security.get().audit("user", userId, "chat.send", "agent", agentId,
                    "{\"msg\":\"" + safeJson(message) + "\"}", true);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", content);
        result.put("agent", reply == null ? "" : reply.agent());
        result.put("agentType", agent.agentType());
        result.put("agentName", agent.name());
        if (skillRouteHint != null) {
            Map<String, Object> skillMeta = new LinkedHashMap<>();
            skillMeta.put("skillName", skillRouteHint);
            result.put("skillRoute", skillMeta);
        }
        if (intent != null) {
            Map<String, Object> intentMeta = new LinkedHashMap<>();
            intentMeta.put("intentCode", intent.intentCode());
            intentMeta.put("intentName", intent.intentName());
            intentMeta.put("confidence", intent.confidence());
            intentMeta.put("needClarify", intent.needClarify());
            intentMeta.put("needTool", intent.needTool());
            intentMeta.put("routeStrategy", intent.routeStrategy());
            intentMeta.put("requiredSlotMissing", intent.requiredSlotMissing());
            result.put("intent", intentMeta);
        }
        return ApiResponse.ok(result);
    }

    private String blankTo(String value, String fallback) {
        return StrUtil.isBlank(value) ? fallback : value.trim();
    }

    /** 意图拦截响应：追问/确认场景不调用模型，直接返回路由结果。 */
    private Map<String, Object> intentHandledReply(String agentId, String agentName,
                                                   IntentParseResult intent, String action, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", message);
        result.put("agent", agentId);
        result.put("agentName", agentName);
        result.put("intentHandled", true);
        result.put("action", action);
        Map<String, Object> intentMeta = new LinkedHashMap<>();
        intentMeta.put("intentCode", intent.intentCode());
        intentMeta.put("intentName", intent.intentName());
        intentMeta.put("confidence", intent.confidence());
        intentMeta.put("needClarify", true);
        intentMeta.put("needTool", intent.needTool());
        intentMeta.put("routeStrategy", intent.routeStrategy());
        intentMeta.put("requiredSlotMissing", intent.requiredSlotMissing());
        result.put("intent", intentMeta);
        return result;
    }

    /** 将 Skill 级路由决策转换为意图结果结构（复用 intentHandledReply 输出）。 */
    private com.zimo.intent.model.IntentParseResult skillToIntentResult(IntentRoutingDecision decision) {
        String skillName = decision.matchedSkills().isEmpty()
                ? "SKILL" : decision.matchedSkills().get(0).name();
        double confidence = decision.results().isEmpty()
                ? 0 : decision.results().get(0).confidence();
        return com.zimo.intent.model.IntentParseResult.business("", "SKILL_ROUTE", skillName, confidence,
                java.util.Map.of(), java.util.List.of(), java.util.List.of(),
                false, "", "TOOL_CALL");
    }

    private String agentName(Map<String, String> body, String agentId) {
        try {
            return managementService.findEnabledAgentById(agentId)
                    .map(AiManagedAgent::name).orElse(agentId);
        } catch (Exception e) {
            return agentId;
        }
    }

    private static String safeJson(String s) {
        return s == null ? "" : s.replace("\"", "'").replace("\n", " ");
    }
}
