package com.zimo.module.ai.management;

import java.time.LocalDateTime;
import java.util.List;
import cn.hutool.core.util.StrUtil;

/**
 * AI 智能体管理模块的领域对象。
 *
 * <p>{@code tenantId} 是运行时路由和数据隔离维度；创建或更新时未显式提供租户标识，
 * 则使用 {@code userId} 作为租户标识，以兼容现有单用户租户数据。</p>
 *
 * @param id 智能体业务主键
 * @param name 智能体名称
 * @param desc 智能体说明
 * @param persona 智能体系统提示词
 * @param model 智能体使用的模型名称
 * @param promptTemplateId 关联的提示词模板主键，允许为空
 * @param skillIds 允许使用的技能标识列表
 * @param agentType 智能体类型：conversation/rag/tool/plan/graph
 * @param agentConfig 类型专属配置 JSON（知识库路径、计划参数、图任务声明等）
 * @param enabled 是否允许参与运行时路由
 * @param userId 创建或维护该智能体的用户标识
 * @param tenantId 智能体所属租户标识，是运行时隔离维度
 * @param userName 创建或维护该智能体的用户名称
 * @param defaultChannels 默认使用该智能体的渠道列表
 * @param createdAt 创建时间（用于前端按创建时间倒序展示）
 * @author Codex
 * @since 2026-07-23
 */
public record AiManagedAgent(
        String id,
        String name,
        String desc,
        String persona,
        String model,
        Long promptTemplateId,
        List<String> skillIds,
        String agentType,
        String agentConfig,
        boolean enabled,
        String userId,
        String tenantId,
        String userName,
        List<String> defaultChannels,
        LocalDateTime createdAt) {

    /**
     * 创建不可变领域对象，并规范化集合和租户标识。
     */
    public AiManagedAgent {
        skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
        defaultChannels = defaultChannels == null ? List.of() : List.copyOf(defaultChannels);
        if (StrUtil.isBlank(agentType)) {
            agentType = "conversation";
        }
        String resolvedTenantId = StrUtil.isBlank(tenantId) ? userId : tenantId;
        tenantId = resolvedTenantId == null ? null : resolvedTenantId.trim();
    }

    /**
     * 创建兼容旧调用方的领域对象，默认使用 {@code userId} 作为 {@code tenantId}。
     *
     * @param id 智能体业务主键
     * @param name 智能体名称
     * @param desc 智能体说明
     * @param persona 智能体系统提示词
     * @param model 智能体使用的模型名称
     * @param promptTemplateId 关联的提示词模板主键
     * @param skillIds 允许使用的技能标识列表
     * @param enabled 是否启用
     * @param userId 用户标识，同时作为默认租户标识
     * @param userName 用户名称
     * @param defaultChannels 默认渠道列表
     */
    public AiManagedAgent(
            String id,
            String name,
            String desc,
            String persona,
            String model,
            Long promptTemplateId,
            List<String> skillIds,
            boolean enabled,
            String userId,
            String userName,
            List<String> defaultChannels) {
        this(
                id,
                name,
                desc,
                persona,
                model,
                promptTemplateId,
                skillIds,
                "conversation",
                null,
                enabled,
                userId,
                userId,
                userName,
                defaultChannels,
                null);
    }

    /**
     * 判断当前智能体是否是指定渠道的默认配置。
     *
     * @param channel 渠道标识；为空时不匹配
     * @return 渠道忽略大小写匹配时返回 {@code true}
     */
    public boolean isDefaultForChannel(String channel) {
        if (channel == null || channel.trim().isEmpty()) {
            return false;
        }
        String normalized = channel.trim();
        return defaultChannels.stream()
                .anyMatch(value -> normalized.equalsIgnoreCase(value));
    }
}
