package com.zimo.framework.ai.channel;

import cn.hutool.core.util.StrUtil;

/**
 * 渠道适配器完成服务端校验后写入消息的结构化智能体绑定。
 *
 * <p>该对象用于区分服务端配置产生的绑定与普通字符串属性。渠道适配器必须先校验外部租户，
 * resolver 和 handler 还会再次核对 {@code externalTenantId} 与消息租户，之后才允许映射到
 * 智能体的内部 {@code tenantId}。</p>
 *
 * @param externalTenantId 渠道侧租户标识，必须与当前消息租户一致
 * @param agentId 服务端配置绑定的智能体业务标识
 * @author Codex
 * @since 2026-07-27
 */
public record AiChannelAgentBinding(
        String externalTenantId,
        String agentId) {

    /** 消息 attributes 中保存结构化绑定的保留键。 */
    public static final String ATTRIBUTE_NAME = "_verifiedAgentBinding";

    /**
     * 创建规范化的结构化绑定。
     */
    public AiChannelAgentBinding {
        externalTenantId = normalize(externalTenantId);
        agentId = normalize(agentId);
    }

    /**
     * 核对该绑定是否适用于指定渠道消息和候选智能体。
     *
     * @param messageTenantId 当前渠道消息的外部租户标识
     * @param candidateAgentId resolver 返回的候选智能体标识
     * @return 两个标识均非空且分别完全匹配时返回 {@code true}
     */
    public boolean matches(String messageTenantId, String candidateAgentId) {
        return externalTenantId != null
                && agentId != null
                && externalTenantId.equals(normalize(messageTenantId))
                && agentId.equals(normalize(candidateAgentId));
    }

    private static String normalize(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }
}
