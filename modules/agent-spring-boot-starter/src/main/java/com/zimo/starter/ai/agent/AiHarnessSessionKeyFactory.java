package com.zimo.starter.ai.agent;

import cn.hutool.core.util.StrUtil;

/**
 * 创建按租户、智能体与渠道隔离的 HarnessAgent 会话键。
 *
 * <p>每个维度使用“字符长度:内容”编码后顺序拼接，避免冒号、空值占位符等用户输入造成键碰撞。</p>
 *
 * @author Codex
 * @since 2026-07-25
 */
public class AiHarnessSessionKeyFactory {

    /**
     * 生成租户、智能体、渠道、会话和用户固定顺序的无歧义会话键。
     *
     * @param request 路由请求，允许为空；缺失维度按空字符串编码
     * @param profile 已路由的智能体配置，允许为空；缺失标识按空字符串编码
     * @return 五个长度前缀字段顺序拼接的会话键
     */
    public String create(AiAgentRouteRequest request, AiAgentProfile profile) {
        return framed(request == null ? null : request.tenantId())
                + framed(profile == null ? null : profile.id())
                + framed(request == null ? null : request.channel())
                + framed(request == null ? null : request.conversationId())
                + framed(request == null ? null : request.userId());
    }

    private String framed(String value) {
        String normalized = StrUtil.isBlank(value) ? "" : value.trim();
        return normalized.length() + ":" + normalized;
    }
}