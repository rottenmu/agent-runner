package com.zimo.starter.ai.agent;

import com.zimo.starter.ai.channel.AiChannelMessage;

/**
 * 智能体分层路由所需的不可变输入。
 *
 * @param tenantId 租户标识
 * @param channel 渠道标识
 * @param userId 用户标识
 * @param conversationId 会话标识
 * @param explicitProfile 调用方显式指定的智能体配置
 * @param channelMessage 完整渠道消息；非渠道调用可为空
 */
public record AiAgentRouteRequest(
        String tenantId,
        String channel,
        String userId,
        String conversationId,
        AiAgentProfile explicitProfile,
        AiChannelMessage channelMessage) {
}
