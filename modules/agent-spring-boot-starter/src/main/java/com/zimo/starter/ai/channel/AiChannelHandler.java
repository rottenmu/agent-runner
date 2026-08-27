package com.zimo.starter.ai.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.starter.ai.AiAgentReply;
import com.zimo.starter.ai.AiAgentService;
import com.zimo.starter.ai.agent.AiAgentProfile;
import com.zimo.starter.ai.agent.AiAgentProfileResolver;
import com.zimo.starter.ai.agent.AiAgentRouteRequest;
import com.zimo.starter.ai.skill.AiSkillRegistry;
import com.zimo.starter.ai.skill.AiSkillResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 渠道消息统一处理器。
 *
 * <p>负责消息校验、结构化渠道绑定解析、意图处理、受智能体技能白名单约束的快捷技能命令，
 * 以及向分层 HarnessAgent 路由调用链转发普通对话。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
public class AiChannelHandler {
    private static final String SKILL_PREFIX = "\u6280\u80fd";
    private static final String FEISHU_CHANNEL = "feishu";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiAgentService agentService;
    private final AiSkillRegistry skillRegistry;
    private final AiAgentProfileResolver profileResolver;
    private final List<AiChannelIntentHandler> intentHandlers;

    /**
     * 创建不启用业务 profile 解析和渠道意图扩展的处理器。
     *
     * @param agentService AI 智能体调用服务，不允许为空
     * @param skillRegistry 技能注册表，不允许为空
     */
    public AiChannelHandler(AiAgentService agentService, AiSkillRegistry skillRegistry) {
        this(agentService, skillRegistry, null, List.of());
    }

    /**
     * 创建支持业务 profile 解析、不启用渠道意图扩展的处理器。
     *
     * @param agentService AI 智能体调用服务，不允许为空
     * @param skillRegistry 技能注册表，不允许为空
     * @param profileResolver 渠道智能体解析器，允许为空
     */
    public AiChannelHandler(
            AiAgentService agentService,
            AiSkillRegistry skillRegistry,
            AiAgentProfileResolver profileResolver) {
        this(agentService, skillRegistry, profileResolver, List.of());
    }

    /**
     * 创建完整渠道处理器。
     *
     * @param agentService AI 智能体调用服务，不允许为空
     * @param skillRegistry 技能注册表，不允许为空
     * @param profileResolver 渠道智能体解析器，允许为空
     * @param intentHandlers 渠道意图处理器列表，空值按空列表处理
     */
    public AiChannelHandler(
            AiAgentService agentService,
            AiSkillRegistry skillRegistry,
            AiAgentProfileResolver profileResolver,
            List<AiChannelIntentHandler> intentHandlers) {
        this.agentService = Objects.requireNonNull(agentService, "agentService must not be null");
        this.skillRegistry = Objects.requireNonNull(skillRegistry, "skillRegistry must not be null");
        this.profileResolver = profileResolver;
        this.intentHandlers = intentHandlers == null ? List.of() : List.copyOf(intentHandlers);
    }

    /**
     * 处理单条渠道消息。
     *
     * <p>空消息返回校验提示；快捷技能命令必须命中已解析智能体的技能白名单；普通消息先执行意图处理，
     * 未命中时转入分层 HarnessAgent 路由。飞书跨内部租户映射只接受结构化服务端校验绑定。</p>
     *
     * @param message 渠道消息，允许为空
     * @return 可直接由渠道适配器发送的文本或结构化回复
     */
    public AiChannelReply handle(AiChannelMessage message) {
        if (message == null || !hasText(message.text())) {
            return AiChannelReply.text("\u6d88\u606f\u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a");
        }
        String text = message.text().trim();
        AiAgentProfile channelAgent = defaultAgent(message);
        if (text.startsWith(SKILL_PREFIX)) {
            return handleSkill(
                    text.substring(SKILL_PREFIX.length()).trim(),
                    channelAgent);
        }
        AiChannelReply intentReply = handleIntent(message, channelAgent);
        if (intentReply != null) {
            return intentReply;
        }
        AiAgentRouteRequest routeRequest = new AiAgentRouteRequest(
                channelAgent == null ? message.tenantId() : channelAgent.tenantId(),
                message.channel(),
                message.userId(),
                message.conversationId(),
                channelAgent,
                message);
        AiAgentReply reply = agentService.chat(text, routeRequest);
        return AiChannelReply.text(reply.content());
    }

    private AiAgentProfile defaultAgent(AiChannelMessage message) {
        if (profileResolver == null || message == null) {
            return null;
        }
        AiAgentProfile profile = profileResolver.resolveForMessage(message).orElse(null);
        if (profile == null
                || !profile.enabled()
                || !hasText(profile.id())
                || !hasText(profile.tenantId())) {
            return null;
        }
        if (profile.tenantId().equals(message.tenantId())
                || isTrustedFeishuBinding(message, profile)) {
            return profile;
        }
        return null;
    }

    private boolean isTrustedFeishuBinding(
            AiChannelMessage message,
            AiAgentProfile profile) {
        if (!FEISHU_CHANNEL.equalsIgnoreCase(message.channel())) {
            return false;
        }
        Object binding = message.attributes().get(AiChannelAgentBinding.ATTRIBUTE_NAME);
        return binding instanceof AiChannelAgentBinding verified
                && verified.matches(message.tenantId(), profile.id());
    }

    private AiChannelReply handleIntent(AiChannelMessage message, AiAgentProfile channelAgent) {
        for (AiChannelIntentHandler intentHandler : intentHandlers) {
            AiChannelReply reply = intentHandler.handle(message, channelAgent);
            if (reply != null) {
                return reply;
            }
        }
        return null;
    }


    private AiChannelReply handleSkill(
            String command,
            AiAgentProfile channelAgent) {
        if (!hasText(command)) {
            return AiChannelReply.text("\u6280\u80fd\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a");
        }
        int skillNameEnd = firstWhitespaceIndex(command);
        String skillName = skillNameEnd < 0 ? command : command.substring(0, skillNameEnd);
        if (channelAgent == null) {
            return AiChannelReply.text("未找到可用智能体，无法调用技能");
        }
        if (!channelAgent.skillIds().contains(skillName)) {
            return AiChannelReply.text("当前智能体未绑定技能：" + skillName);
        }
        try {
            String rawArguments = skillNameEnd < 0 ? "" : command.substring(skillNameEnd + 1).trim();
            AiSkillResult result = skillRegistry.call(skillName, parseArguments(rawArguments));
            return AiChannelReply.text(result.content());
        } catch (IllegalArgumentException e) {
            return AiChannelReply.text(e.getMessage());
        } catch (Exception e) {
            String message = e.getMessage();
            return AiChannelReply.text("\u6280\u80fd\u8c03\u7528\u5931\u8d25\uff1a"
                    + (hasText(message) ? message : e.getClass().getSimpleName()));
        }
    }

    private Map<String, Object> parseArguments(String rawArguments) {
        if (!hasText(rawArguments)) {
            return Collections.emptyMap();
        }
        if (rawArguments.trim().startsWith("{")) {
            try {
                return OBJECT_MAPPER.readValue(rawArguments, new TypeReference<>() {
                });
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "\u6280\u80fd\u53c2\u6570\u5fc5\u987b\u662f JSON \u5bf9\u8c61\u6216 key=value \u683c\u5f0f");
            }
        }
        Map<String, Object> arguments = new java.util.LinkedHashMap<>();
        for (String token : splitArgumentTokens(rawArguments)) {
            int index = token.indexOf('=');
            if (index <= 0) {
                continue;
            }
            arguments.put(token.substring(0, index), token.substring(index + 1));
        }
        return arguments;
    }

    private List<String> splitArgumentTokens(String rawArguments) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean escaping = false;
        for (int i = 0; i < rawArguments.length(); i++) {
            char ch = rawArguments.charAt(i);
            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }
            if (quote != 0) {
                if (ch == '\\') {
                    escaping = true;
                } else if (ch == quote) {
                    quote = 0;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                quote = ch;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (quote != 0) {
            throw new IllegalArgumentException("\u6280\u80fd\u53c2\u6570\u5f15\u53f7\u672a\u95ed\u5408");
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static int firstWhitespaceIndex(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
