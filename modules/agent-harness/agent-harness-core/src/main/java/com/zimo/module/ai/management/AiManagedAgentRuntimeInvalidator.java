package com.zimo.module.ai.management;

/**
 * 智能体管理变更后的运行时实例失效端口。
 *
 * <p>管理服务仅传递内部租户和智能体标识，不依赖具体 HarnessAgent 注册表实现。
 * 运行时模块可据此回收该智能体的全部历史配置实例。</p>
 *
 * @author Codex
 * @since 2026-07-27
 */
@FunctionalInterface
public interface AiManagedAgentRuntimeInvalidator {

    /**
     * 失效指定内部租户下智能体的全部运行时实例。
     *
     * @param tenantId 内部租户标识，是智能体隔离维度，不允许为空白
     * @param agentId 智能体业务标识，不允许为空白
     */
    void invalidate(String tenantId, String agentId);
}
