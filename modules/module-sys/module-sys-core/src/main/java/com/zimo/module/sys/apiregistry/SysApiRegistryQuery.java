package com.zimo.module.sys.apiregistry;

/**
 * 系统管理模块 API 注册表查询条件。
 *
 * <p>所有条件均为可选项；HTTP 方法由数据访问层统一转换为大写后查询。</p>
 *
 * @param moduleCode 业务模块标识，允许为空
 * @param method HTTP 请求方法，允许为空且大小写不敏感
 * @param status 注册表状态，允许为空；{@code 0} 表示停用，{@code 1} 表示启用，{@code 2} 表示草稿
 * @param keyword 接口路径、名称、摘要或 Controller 名称关键字，允许为空
 * @author Codex
 * @since 2026-07-21
 */
public record SysApiRegistryQuery(
        String moduleCode,
        String method,
        Integer status,
        String keyword) {
}
