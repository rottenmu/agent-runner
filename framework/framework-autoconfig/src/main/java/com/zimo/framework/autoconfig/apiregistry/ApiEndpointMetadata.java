package com.zimo.framework.autoconfig.apiregistry;

/**
 * API 注册扫描生成的接口元数据。
 *
 * <p>该记录承载框架扫描结果并写入 MySQL {@code api_registry} 表。JSON 字段保存标准 JSON
 * 字符串，可选响应示例允许为 {@code null}。</p>
 *
 * @param moduleCode 业务插件唯一编码
 * @param moduleName 业务插件显示名称
 * @param moduleBasePath 业务插件 API 基础路径
 * @param moduleDescription 业务插件说明，缺失时为空字符串
 * @param controllerDescription Controller 业务说明，缺失时为空字符串
 * @param method HTTP 请求方法
 * @param path 接口完整请求路径
 * @param apiName 接口方法名称
 * @param summary 接口摘要
 * @param description 接口详细说明
 * @param tags 接口标签 JSON
 * @param requestParams 请求参数元数据 JSON
 * @param responseExample 响应示例 JSON，缺失时为 {@code null}
 * @param authRequired 是否要求登录鉴权
 * @param deprecated 是否已废弃
 * @param hash 接口唯一签名 MD5
 * @param version 接口版本
 * @author Codex
 * @since 2026-07-21
 */
public record ApiEndpointMetadata(
        String moduleCode,
        String moduleName,
        String moduleBasePath,
        String moduleDescription,
        String controllerDescription,
        String method,
        String path,
        String apiName,
        String summary,
        String description,
        String tags,
        String requestParams,
        String responseExample,
        boolean authRequired,
        boolean deprecated,
        String hash,
        String version) {
}
