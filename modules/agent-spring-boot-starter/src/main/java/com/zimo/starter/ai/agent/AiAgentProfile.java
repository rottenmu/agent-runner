package com.zimo.starter.ai.agent;

import java.util.List;
import cn.hutool.core.util.StrUtil;

/**
 * AI 智能体运行时配置快照。
 *
 * <p>该对象仅承载聊天与渠道路由所需的中立字段，不包含管理端持久化语义。
 * {@code tenantId} 是路由与运行时实例隔离的唯一租户维度。</p>
 *
 * @param id 智能体业务标识，用于会话隔离
 * @param tenantId 智能体所属租户标识，不允许跨租户路由
 * @param name 智能体展示名称，同时作为模型请求中的智能体名称
 * @param modelName 模型名称；为空时使用 starter 默认配置
 * @param systemPrompt 系统提示词；为空时使用 starter 默认配置
 * @param skillIds 智能体允许使用的技能标识列表
 * @param agentType 智能体类型：conversation=普通对话，rag=检索增强，tool=工具调用，plan=规划执行，graph=图任务流
 * @param agentConfig 类型专属配置 JSON（如 rag 知识库路径、plan 迭代次数、graph 子任务声明）
 * @param enabled 是否允许参与运行时路由
 * @author Codex
 * @since 2026-07-23
 */
public record AiAgentProfile(
        String id,
        String tenantId,
        String name,
        String modelName,
        String systemPrompt,
        List<String> skillIds,
        String agentType,
        String agentConfig,
        boolean enabled) {

    /** 默认智能体类型：普通对话。 */
    public static final String TYPE_CONVERSATION = "conversation";
    public static final String TYPE_RAG = "rag";
    public static final String TYPE_TOOL = "tool";
    public static final String TYPE_PLAN = "plan";
    public static final String TYPE_GRAPH = "graph";

    /**
     * 创建不可变运行时配置快照，并将空技能列表规范化为空集合、空类型规范化为普通对话。
     */
    public AiAgentProfile {
        skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
        if (StrUtil.isBlank(agentType)) {
            agentType = TYPE_CONVERSATION;
        }
    }

    /**
     * 创建兼容旧调用方的运行时配置快照。
     *
     * <p>该构造器不提供租户信息，因此配置不能参与租户路由，仅供尚未接入路由器的旧调用链使用。</p>
     *
     * @param id 智能体业务标识
     * @param name 智能体展示名称
     * @param modelName 模型名称
     * @param systemPrompt 系统提示词
     * @param skillIds 允许使用的技能标识列表
     */
    public AiAgentProfile(
            String id,
            String name,
            String modelName,
            String systemPrompt,
            List<String> skillIds) {
        this(id, null, name, modelName, systemPrompt, skillIds, TYPE_CONVERSATION, null, true);
    }

    /**
     * 创建带租户与类型的运行时配置快照（兼容旧调用方，类型默认普通对话）。
     *
     * @param id 智能体业务标识
     * @param tenantId 智能体所属租户标识
     * @param name 智能体展示名称
     * @param modelName 模型名称
     * @param systemPrompt 系统提示词
     * @param skillIds 允许使用的技能标识列表
     * @param enabled 是否启用
     */
    public AiAgentProfile(
            String id,
            String tenantId,
            String name,
            String modelName,
            String systemPrompt,
            List<String> skillIds,
            boolean enabled) {
        this(id, tenantId, name, modelName, systemPrompt, skillIds, TYPE_CONVERSATION, null, enabled);
    }

    /**
     * 创建带类型的运行时配置快照（兼容旧调用方）。
     *
     * @param id 智能体业务标识
     * @param name 智能体展示名称
     * @param modelName 模型名称
     * @param systemPrompt 系统提示词
     * @param skillIds 允许使用的技能标识列表
     * @param agentType 智能体类型
     * @param agentConfig 类型专属配置 JSON
     */
    public AiAgentProfile(
            String id,
            String name,
            String modelName,
            String systemPrompt,
            List<String> skillIds,
            String agentType,
            String agentConfig) {
        this(id, null, name, modelName, systemPrompt, skillIds, agentType, agentConfig, true);
    }
}
