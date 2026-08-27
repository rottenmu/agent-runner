package com.zimo.module.sys.apiregistry;

/**
 * 系统管理模块 API 注册状态修改入参。
 *
 * <p>状态合法性由业务服务统一校验，本对象仅承载接口请求数据。</p>
 *
 * @param status 目标状态；{@code 0} 表示停用，{@code 1} 表示启用，不允许为空
 * @author Codex
 * @since 2026-07-21
 */
public record SysApiRegistryStatusUpdateRequest(Integer status) {
}
