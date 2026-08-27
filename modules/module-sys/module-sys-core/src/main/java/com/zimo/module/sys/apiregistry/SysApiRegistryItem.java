package com.zimo.module.sys.apiregistry;

import java.util.List;
import java.util.Map;

/**
 * 系统管理模块 API 注册表单条接口展示对象。
 *
 * <p>数据来源于 {@code api_registry} 表，仅包含未逻辑删除记录；JSON 列解析失败时使用安全回退值。</p>
 *
 * @param id API 注册表主键 ID
 * @param moduleCode 业务模块标识
 * @param moduleName 业务模块显示名称
 * @param moduleBasePath 业务模块基础请求路径
 * @param moduleDesc 业务模块说明
 * @param controllerDesc Controller 说明
 * @param method HTTP 请求方法
 * @param path API 完整请求路径
 * @param apiName Controller 方法名称
 * @param summary API 摘要
 * @param description API 详细说明
 * @param tags API 标签列表；无数据或解析失败时为空列表
 * @param requestParams 请求参数元数据；无数据或解析失败时为空列表
 * @param responseExample 响应示例 JSON 值；无数据或解析失败时为 {@code null}
 * @param authRequired 是否需要鉴权
 * @param deprecated 是否已经废弃
 * @param version API 版本
 * @param status 注册表状态；{@code 0} 表示停用，{@code 1} 表示启用，{@code 2} 表示草稿
 * @param sort 同模块内排序值，数值越小越靠前
 * @author Codex
 * @since 2026-07-21
 */
public record SysApiRegistryItem(
        Long id,
        String moduleCode,
        String moduleName,
        String moduleBasePath,
        String moduleDesc,
        String controllerDesc,
        String method,
        String path,
        String apiName,
        String summary,
        String description,
        List<String> tags,
        List<Map<String, Object>> requestParams,
        Object responseExample,
        boolean authRequired,
        boolean deprecated,
        String version,
        Integer status,
        Integer sort) {
}
