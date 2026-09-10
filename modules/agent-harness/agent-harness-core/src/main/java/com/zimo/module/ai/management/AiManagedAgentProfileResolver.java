package com.zimo.module.ai.management;

import com.zimo.framework.ai.agent.AiAgentProfile;
import com.zimo.framework.ai.agent.AiAgentProfileResolver;
import com.zimo.framework.ai.channel.AiChannelAgentBinding;
import com.zimo.framework.ai.channel.AiChannelMessage;
import java.util.Objects;
import java.util.Optional;

/**
 * 将 module-ai 管理领域中的智能体配置适配为 starter 运行时配置。
 *
 * <p>解析器只暴露聊天与渠道路由所需字段，避免 starter 依赖管理实体。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
public class AiManagedAgentProfileResolver implements AiAgentProfileResolver {

    private static final String FEISHU_CHANNEL = "feishu";

    private final AiAgentManagementService managementService;

    /**
     * 创建智能体运行时配置解析器。
     *
     * @param managementService AI 管理服务，不允许为空
     */
    public AiManagedAgentProfileResolver(AiAgentManagementService managementService) {
        this.managementService = Objects.requireNonNull(managementService, "managementService must not be null");
    }

    /**
     * 按渠道解析已启用的默认智能体。
     *
     * @param channel 渠道编码，不允许为空
     * @return 匹配的运行时配置；未配置时返回空
     */
    @Override
    public Optional<AiAgentProfile> resolveDefaultForChannel(String channel) {
        return managementService.findDefaultAgentForChannel(channel).map(this::toProfile);
    }

    /**
     * 按渠道和内部租户解析已启用的默认智能体。
     *
     * @param channel 渠道编码，不允许为空
     * @param tenantId 内部租户标识，是智能体数据隔离维度
     * @return 同时匹配渠道和租户的运行时配置；未配置时返回空
     */
    @Override
    public Optional<AiAgentProfile> resolveDefaultForChannel(String channel, String tenantId) {
        return managementService.findDefaultAgentForChannel(channel, tenantId)
                .map(this::toProfile);
    }

    /**
     * 按完整渠道消息解析运行时智能体配置。
     *
     * <p>飞书消息优先使用 结构化服务端校验绑定指向的已启用智能体；绑定缺失、
     * 指向不存在或停用智能体时降级到飞书渠道默认配置。其他渠道忽略该属性，保持原有渠道默认解析。</p>
     *
     * @param message 完整渠道消息；为 {@code null} 时不执行解析
     * @return 匹配的运行时配置；没有绑定和渠道默认配置时返回 {@link Optional#empty()}
     */
    @Override
    public Optional<AiAgentProfile> resolveForMessage(AiChannelMessage message) {
        if (message == null) {
            return Optional.empty();
        }
        if (!FEISHU_CHANNEL.equals(message.channel())) {
            return resolveDefaultForChannel(message.channel(), message.tenantId());
        }
        Object bindingAttribute = message.attributes().get(AiChannelAgentBinding.ATTRIBUTE_NAME);
        Optional<AiAgentProfile> boundProfile = bindingAttribute instanceof AiChannelAgentBinding binding
                && binding.matches(message.tenantId(), binding.agentId())
                ? managementService.findEnabledAgentById(binding.agentId()).map(this::toProfile)
                : Optional.empty();
        return boundProfile.or(() ->
                resolveDefaultForChannel(message.channel(), message.tenantId()));
    }
    private AiAgentProfile toProfile(AiManagedAgent agent) {
        return new AiAgentProfile(
                agent.id(),
                agent.tenantId(),
                agent.name(),
                agent.model(),
                agent.persona(),
                agent.skillIds(),
                agent.agentType(),
                agent.agentConfig(),
                agent.enabled());
    }
}
