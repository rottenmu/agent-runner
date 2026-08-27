package com.zimo.module.feishu.config;

/**
 * 提供当前启用的飞书运行时配置及其关联信息。
 */
public interface FeishuConfigProvider {
    /**
     * 获取当前启用的飞书运行时配置。
     *
     * @return 运行时配置，无可用配置时允许返回 {@code null}
     */
    FeishuRuntimeConfig getActiveConfig();

    /**
     * 获取当前启用配置绑定的智能体 ID。
     *
     * @return 智能体 ID，未绑定时返回 {@code null}
     */
    default String getActiveAgentId() {
        return null;
    }

    /**
     * 获取与指定飞书事件租户匹配的活动配置绑定智能体。
     *
     * <p>实现必须在服务端核对活动配置的 {@code tenantKey}；不匹配或未绑定时返回空，
     * 禁止仅按全局活动配置返回其他飞书租户的绑定。</p>
     *
     * @param tenantKey 当前事件携带的飞书租户标识，允许为空
     * @return 经租户核验的智能体 ID；未匹配时返回 {@code null}
     */
    default String getActiveAgentId(String tenantKey) {
        return null;
    }
}