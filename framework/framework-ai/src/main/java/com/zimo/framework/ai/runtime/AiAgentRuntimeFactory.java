package com.zimo.framework.ai.runtime;

import com.zimo.framework.ai.AiAgentProperties;
import com.zimo.framework.ai.skill.AiSkillRegistry;

/**
 * 创建 AI 智能体运行时状态快照。
 *
 * <p>启动阶段仅验证必要配置和 AgentScope Harness 类型是否存在；具体模型和 HarnessAgent
 * 由注册表按路由结果懒加载，避免启动时创建无法复用的单一实例。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
public class AiAgentRuntimeFactory {

    /**
     * 根据 starter 配置创建运行时状态快照。
     *
     * @param properties starter 配置，不允许为空
     * @param skillRegistry 技能注册表，不允许为空
     * @return 可供服务判断配置状态的不可变运行时快照
     */
    public AiAgentRuntime create(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return runtime(
                    properties,
                    skillRegistry,
                    AiAgentRuntimeStatus.NOT_CONFIGURED,
                    "AI 服务未配置，请配置 ai.agent.api-key 或 DASHSCOPE_API_KEY");
        }
        try {
            Class.forName(
                    harnessAgentClassName(),
                    false,
                    getClass().getClassLoader());
            return runtime(
                    properties,
                    skillRegistry,
                    AiAgentRuntimeStatus.READY,
                    "AI 智能体运行时已就绪，将按路由结果懒加载实例");
        } catch (ClassNotFoundException exception) {
            return runtime(
                    properties,
                    skillRegistry,
                    AiAgentRuntimeStatus.INITIALIZATION_FAILED,
                    "AgentScope Java 依赖未加载，请检查 agentscope-harness 依赖");
        } catch (LinkageError error) {
            return runtime(
                    properties,
                    skillRegistry,
                    AiAgentRuntimeStatus.INITIALIZATION_FAILED,
                    "AgentScope Java 依赖加载失败：" + error.getClass().getSimpleName());
        }
    }

    /**
     * 获取用于依赖检查的 HarnessAgent 类名，测试可覆写模拟依赖缺失。
     *
     * @return HarnessAgent 完整类名
     */
    protected String harnessAgentClassName() {
        return "io.agentscope.harness.agent.HarnessAgent";
    }

    private AiAgentRuntime runtime(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry,
            AiAgentRuntimeStatus status,
            String message) {
        return new AiAgentRuntime(
                properties.getName(),
                properties.getModelName(),
                properties.getModelType(),
                skillRegistry.list(),
                status,
                message);
    }
}
