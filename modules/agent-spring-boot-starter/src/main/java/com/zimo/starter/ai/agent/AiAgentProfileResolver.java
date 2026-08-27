package com.zimo.starter.ai.agent;

import com.zimo.starter.ai.channel.AiChannelMessage;
import cn.hutool.core.util.StrUtil;
import java.util.Objects;
import java.util.Optional;

/**
 * AI 智能体运行时配置解析器。
 *
 * <p>业务模块通过该接口向 starter 提供渠道默认智能体，starter 不感知管理端存储实现。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
@FunctionalInterface
public interface AiAgentProfileResolver {

    /**
     * 按渠道解析默认智能体配置。
     *
     * @param channel 渠道编码，不允许为空
     * @return 匹配的运行时配置；没有配置时返回 {@link Optional#empty()}
     */
    Optional<AiAgentProfile> resolveDefaultForChannel(String channel);

    /**
     * 按渠道和内部租户解析默认智能体配置。
     *
     * <p>默认实现兼容原有解析器，并对返回结果执行严格租户过滤；多租户管理模块应覆盖此方法，
     * 直接在查询阶段限定租户，避免第一个其他租户配置阻断后续候选。</p>
     *
     * @param channel 渠道编码，不允许为空
     * @param tenantId 内部租户标识，是智能体数据隔离维度
     * @return 同时匹配渠道和租户的运行时配置；未匹配时返回 {@link Optional#empty()}
     */
    default Optional<AiAgentProfile> resolveDefaultForChannel(String channel, String tenantId) {
        if (StrUtil.isBlank(tenantId)) {
            return Optional.empty();
        }
        return resolveDefaultForChannel(channel)
                .filter(Objects::nonNull)
                .filter(profile -> tenantId.equals(profile.tenantId()));
    }

    /**
     * 按完整渠道消息解析智能体配置。
     *
     * <p>默认实现保持原有按渠道解析行为；业务模块可覆盖此方法，结合消息属性执行更细粒度的绑定解析。</p>
     *
     * @param message 完整渠道消息；为 {@code null} 时不执行解析
     * @return 匹配的运行时配置；消息为空或没有配置时返回 {@link Optional#empty()}
     */
    default Optional<AiAgentProfile> resolveForMessage(AiChannelMessage message) {
        if (message == null) {
            return Optional.empty();
        }
        return resolveDefaultForChannel(message.channel(), message.tenantId());
    }
}
