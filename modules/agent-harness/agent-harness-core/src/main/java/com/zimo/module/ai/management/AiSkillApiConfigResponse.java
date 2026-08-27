package com.zimo.module.ai.management;

import java.util.Map;

/**
 * AI API 技能远程接口配置响应体，敏感请求头会被脱敏后返回。
 *
 * @param enabled 启用状态
 * @param baseUrl 远程 API 基础地址
 * @param path 远程 API 路径
 * @param method HTTP 方法
 * @param headers 已脱敏的请求头
 * @param timeoutMillis 请求超时时间配置
 * @param apiRegistryId 关联的 API 注册表 ID
 */
public record AiSkillApiConfigResponse(
        boolean enabled,
        String baseUrl,
        String path,
        String method,
        Map<String, String> headers,
        int timeoutMillis,
        Long apiRegistryId) {
}
