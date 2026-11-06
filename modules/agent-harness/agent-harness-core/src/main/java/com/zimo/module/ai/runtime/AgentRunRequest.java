package com.zimo.module.ai.runtime;

import java.util.List;

/**
 * 一次智能体执行的输入。
 *
 * @param agentId 智能体标识（必填）
 * @param message 本轮用户输入（必填）
 * @param tenantId 租户标识，可空（空则用闸门默认口径）
 * @param userId 用户标识，可空
 * @param sessionId 会话标识，可空（带会话时记忆与状态可延续）
 * @param channel 渠道标识，如 {@code graph} / {@code console}
 * @param requestedToolIds 调用方声明的工具白名单，可空（空表示不限制）
 * @author WorkBuddy
 * @since 2026-10-20
 */
public record AgentRunRequest(
        String agentId,
        String message,
        String tenantId,
        String userId,
        String sessionId,
        String channel,
        List<String> requestedToolIds) {

    /** 规范化空值，避免调用方到处判空。 */
    public AgentRunRequest {
        requestedToolIds = requestedToolIds == null ? List.of() : List.copyOf(requestedToolIds);
        if (channel == null || channel.isBlank()) {
            channel = "graph";
        }
    }
}
