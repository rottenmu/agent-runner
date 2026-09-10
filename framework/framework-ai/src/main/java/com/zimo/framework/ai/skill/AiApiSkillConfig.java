package com.zimo.framework.ai.skill;

import java.util.Map;

/**
 * AI API 技能配置，用于把一个远程 HTTP API 注册为可调用的 AI 技能。
 *
 * @param name 技能名称，在注册表内唯一，调用 MCP tools/call 时使用该值
 * @param description 技能说明，用于管理端和工具清单展示
 * @param readOnly 只读标记，true 表示调用技能不会修改业务数据
 * @param enabled 启用状态，false 时保留配置但拒绝运行时调用
 * @param baseUrl 远程 API 基础地址，例如 https://api.example.com
 * @param path 远程 API 路径，例如 /query
 * @param method HTTP 方法，支持 GET、POST、PUT、PATCH、DELETE
 * @param headers 调用远程 API 时附加的请求头，可能包含鉴权信息
 * @param timeoutMillis 请求超时时间配置，当前作为管理配置保留
 */
public record AiApiSkillConfig(
        String name,
        String description,
        boolean readOnly,
        boolean enabled,
        String baseUrl,
        String path,
        String method,
        Map<String, String> headers,
        int timeoutMillis) {
}
