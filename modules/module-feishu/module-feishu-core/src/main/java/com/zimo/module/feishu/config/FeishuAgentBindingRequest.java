package com.zimo.module.feishu.config;

/**
 * 飞书模块中用于更新渠道配置智能体绑定的接口入参。
 *
 * <p>智能体 ID 由 module-ai 管理，本对象仅传递不透明标识；空值或空白字符串表示解除绑定。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
public class FeishuAgentBindingRequest {
    /**
     * 待绑定的智能体 ID；为空或空白时解除现有绑定。
     */
    private String agentId;

    /**
     * 获取请求中的智能体 ID。
     *
     * @return 智能体 ID，解除绑定时允许为 {@code null} 或空白字符串
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * 设置请求中的智能体 ID。
     *
     * @param agentId 智能体 ID，允许为 {@code null} 或空白字符串
     */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }
}