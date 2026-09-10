package com.zimo.framework.ai.agent;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.core.util.StrUtil;
import com.zimo.framework.ai.AiAgentProperties;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * HarnessAgent 注册表的不可变缓存键。
 *
 * <p>租户和智能体标识定义隔离边界，配置指纹保证影响实例构造的配置变化后不会继续复用旧实例。</p>
 *
 * @param tenantId 智能体所属租户标识
 * @param agentId 智能体业务标识
 * @param profileFingerprint 智能体配置的 SHA-256 指纹
 * @author Codex
 * @since 2026-07-25
 */
public record AiHarnessAgentKey(
        String tenantId,
        String agentId,
        String profileFingerprint) {

    /**
     * 根据运行时配置和 starter 配置创建缓存键。
     *
     * @param profile 已通过租户和启用状态校验的智能体配置，不允许为空
     * @param properties starter 配置，不允许为空
     * @return 包含租户、智能体和完整构造指纹的缓存键
     * @throws IllegalArgumentException 当租户或智能体标识为空时抛出
     */
    public static AiHarnessAgentKey from(
            AiAgentProfile profile,
            AiAgentProperties properties) {
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        String tenantId = required(profile.tenantId(), "tenantId");
        String agentId = required(profile.id(), "agentId");
        return new AiHarnessAgentKey(
                tenantId,
                agentId,
                fingerprint(profile, properties));
    }

    private static String fingerprint(
            AiAgentProfile profile,
            AiAgentProperties properties) {
        AiAgentProperties.ContextCompressionSettings compression =
                properties.getEffectiveContextCompressionSettings();
        String skillIds = profile.skillIds().stream()
                .map(AiHarnessAgentKey::framed)
                .collect(Collectors.joining());
        String value = framed(profile.name())
                + framed(profile.modelName())
                + framed(profile.systemPrompt())
                + framed(skillIds)
                + framed(properties.getName())
                + framed(properties.getModelName())
                + framed(properties.getModelType())
                + framed(properties.getBaseUrl())
                + framed(properties.getApiKey())
                + framed(String.valueOf(properties.getTemperature()))
                + framed(String.valueOf(properties.getMaxTokens()))
                + framed(properties.getSystemPrompt())
                + framed(properties.getHarnessWorkspaceRoot())
                + framed(properties.getHarnessWorkspaceVersion())
                + framed(String.valueOf(properties.getMaxIters()))
                + framed(String.valueOf(compression.enabled()))
                + framed(String.valueOf(compression.triggerMessages()))
                + framed(String.valueOf(compression.recentMessages()))
                + framed(String.valueOf(compression.summaryMaxCharacters()));
        // hutool SecureUtil.sha256：生成 64 位小写十六进制指纹
        return SecureUtil.sha256(value);
    }

    private static String framed(String value) {
        String normalized = value == null ? "" : value;
        return normalized.length() + ":" + normalized;
    }

    private static String required(String value, String fieldName) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
