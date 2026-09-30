package com.zimo.module.ai.controller;

import cn.hutool.core.util.StrUtil;
import com.zimo.framework.ai.agent.AiAgentProfile;
import com.zimo.framework.ai.intent.ConversationContext;
import com.zimo.framework.ai.intent.IntentAwareSkillRouter;
import com.zimo.framework.ai.intent.IntentRoutingDecision;
import com.zimo.framework.common.security.SecurityFacade;
import com.zimo.framework.common.validation.ValidationUtil;
import com.zimo.intent.model.IntentParseResult;
import com.zimo.intent.service.IntentRecognitionService;
import com.zimo.module.ai.management.AiAgentManagementService;
import com.zimo.module.ai.management.AiManagedAgent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 对话前置闸门：权限校验 → 意图识别路由 → 敏感词拦截 → Skill 级意图准入 → 智能体装载。
 *
 * <p><b>为什么要单独抽出来</b>：对话现在有两条入口通道 —— 同步的
 * {@code POST /api/biz/ai/chat} 与流式的 {@code POST /api/biz/ai/chat/stream}。
 * 若两条通道各写一份闸门，流式通道一旦漏掉某道校验（尤其是 {@code agent:execute}
 * 权限与敏感词拦截），就会成为一条**绕过安全管控的旁路**，而且因为流式端点通常只在
 * 新前端里被调用，这种缺失很难在常规回归中被发现。抽成同一份实现，从结构上排除该风险。</p>
 *
 * @author WorkBuddy
 * @since 2026-09-18
 */
@Component
public class AiChatPreflight {

    private static final String DEFAULT_TENANT = "u001";

    /**
     * 闸门结论。
     *
     * @param shortCircuit   非 {@code null} 表示请求已被闸门接管（缺参追问 / 写操作二次确认 /
     *                       技能澄清 / 技能冲突），调用方必须直接返回它，<b>不得进入模型</b>
     * @param agent          已装载的智能体实体；被接管时为 {@code null}
     * @param profile        智能体画像；被接管时为 {@code null}
     * @param tenantId       归一化后的租户标识
     * @param userId         归一化后的用户标识
     * @param sessionId      会话标识，可为 {@code null}
     * @param skillRouteHint 单一命中的技能名，用于引导模型优先调用；未命中为 {@code null}
     * @param intent         意图识别结果，可为 {@code null}
     */
    public record Result(
            Map<String, Object> shortCircuit,
            AiManagedAgent agent,
            AiAgentProfile profile,
            String tenantId,
            String userId,
            String sessionId,
            String skillRouteHint,
            IntentParseResult intent) {

        /** 是否已被闸门接管（调用方据此决定是否进入模型） */
        public boolean shortCircuited() {
            return shortCircuit != null;
        }
    }

    private final AiAgentManagementService managementService;
    private final Optional<SecurityFacade> security;
    private final Optional<IntentRecognitionService> intentService;
    private final Optional<IntentAwareSkillRouter> skillRouter;

    public AiChatPreflight(AiAgentManagementService managementService,
                           Optional<SecurityFacade> security,
                           Optional<IntentRecognitionService> intentService,
                           Optional<IntentAwareSkillRouter> skillRouter) {
        this.managementService = managementService;
        this.security = security;
        this.intentService = intentService;
        this.skillRouter = skillRouter == null ? Optional.empty() : skillRouter;
    }

    /**
     * 依次执行全部闸门。
     *
     * @param body 请求体，需含 {@code agentId}、{@code message}；{@code sessionId} /
     *             {@code tenantId} / {@code userId} 可空
     * @return 闸门结论
     * @throws IllegalArgumentException 校验不通过（权限不足 / 意图拒绝 / 命中敏感词 / 智能体不存在）
     */
    public Result run(Map<String, String> body) {
        String agentId = body == null ? null : body.get("agentId");
        String message = body == null ? null : body.get("message");
        ValidationUtil.requireText(agentId, "agentId 不能为空");
        ValidationUtil.requireText(message, "消息不能为空");
        String sessionId = body.get("sessionId");
        String tenantId = blankTo(body.get("tenantId"), DEFAULT_TENANT);
        String userId = blankTo(body.get("userId"), DEFAULT_TENANT);

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
                Result clarify = clarifyOrConfirm(body, agentId, message, intent,
                        tenantId, userId, sessionId);
                if (clarify != null) {
                    return clarify;
                }
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
        SkillRoute skillRoute = routeSkill(message, agentId, tenantId, userId, sessionId);
        if (skillRoute.takenOver() != null) {
            return skillRoute.takenOver();
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
        return new Result(null, agent, profile, tenantId, userId, sessionId,
                skillRoute.hint(), intent);
    }

    /** 缺参追问 / 写操作二次确认：命中时返回接管结论，未命中返回 {@code null}。 */
    private Result clarifyOrConfirm(Map<String, String> body, String agentId, String message,
                                    IntentParseResult intent, String tenantId, String userId,
                                    String sessionId) {
        if ("PARAM_CLARIFY".equals(intent.intentCode())) {
            // 必填槽位缺失 → 直接追问补全，禁止调用模型执行
            security.ifPresent(s -> s.audit("user", userId, "chat.intent_clarify", "agent", agentId,
                    safeJson(message), true));
            return takeOver(intentHandledReply(agentId, agentName(agentId), intent,
                    "CLARIFY_REQUIRED", intent.clarifyPrompt()), tenantId, userId, sessionId);
        }
        // 写操作二次确认（ORDER_OPERATE 等 TOOL_CALL_WITH_CONFIRM）→ 确认后再执行
        security.ifPresent(s -> s.audit("user", userId, "chat.intent_confirm", "agent", agentId,
                safeJson(message), true));
        return takeOver(intentHandledReply(agentId, agentName(agentId), intent,
                "CONFIRM_REQUIRED", intent.clarifyPrompt()), tenantId, userId, sessionId);
    }

    /** Skill 级准入路由结论：接管结论文或单一命中提示（二者互斥）。 */
    private record SkillRoute(Result takenOver, String hint) {
    }

    private SkillRoute routeSkill(String message, String agentId, String tenantId,
                                  String userId, String sessionId) {
        if (skillRouter.isEmpty() || skillRouter.get().skills().isEmpty()) {
            return new SkillRoute(null, null);
        }
        IntentRoutingDecision decision = skillRouter.get().route(
                message, ConversationContext.of(tenantId, userId, sessionId));
        switch (decision.status()) {
            case NEED_CLARIFY -> {
                // 技能意图歧义：停止调度，向用户澄清
                security.ifPresent(s -> s.audit("user", userId, "chat.skill_clarify", "agent", agentId,
                        safeJson(message), true));
                return new SkillRoute(takeOver(intentHandledReply(agentId, agentName(agentId),
                        skillToIntentResult(decision), "SKILL_CLARIFY_REQUIRED",
                        decision.clarifyReason()), tenantId, userId, sessionId), null);
            }
            case MULTI_CONFLICT -> {
                // 多技能冲突：返回冲突提示，由用户澄清
                security.ifPresent(s -> s.audit("user", userId, "chat.skill_conflict", "agent", agentId,
                        safeJson(message), true));
                return new SkillRoute(takeOver(intentHandledReply(agentId, agentName(agentId),
                        skillToIntentResult(decision), "SKILL_CONFLICT",
                        decision.clarifyReason()), tenantId, userId, sessionId), null);
            }
            case SINGLE -> {
                // 单一命中：注入提示，引导模型优先调用该技能
                return new SkillRoute(null, decision.matchedSkills().get(0).name());
            }
            case NONE -> {
                // 全部未命中：正常对话（模型自主决定是否调用技能）
                return new SkillRoute(null, null);
            }
            default -> {
                return new SkillRoute(null, null);
            }
        }
    }

    private Result takeOver(Map<String, Object> reply, String tenantId, String userId,
                            String sessionId) {
        return new Result(reply, null, null, tenantId, userId, sessionId, null, null);
    }

    private String blankTo(String value, String fallback) {
        return StrUtil.isBlank(value) ? fallback : value.trim();
    }

    /** 意图拦截响应：追问/确认场景不调用模型，直接返回路由结果。 */
    private Map<String, Object> intentHandledReply(String agentId, String agentName,
                                                   IntentParseResult intent, String action,
                                                   String message) {
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
    private IntentParseResult skillToIntentResult(IntentRoutingDecision decision) {
        String skillName = decision.matchedSkills().isEmpty()
                ? "SKILL" : decision.matchedSkills().get(0).name();
        double confidence = decision.results().isEmpty()
                ? 0 : decision.results().get(0).confidence();
        return IntentParseResult.business("", "SKILL_ROUTE", skillName, confidence,
                Map.of(), java.util.List.of(), java.util.List.of(),
                false, "", "TOOL_CALL");
    }

    private String agentName(String agentId) {
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
