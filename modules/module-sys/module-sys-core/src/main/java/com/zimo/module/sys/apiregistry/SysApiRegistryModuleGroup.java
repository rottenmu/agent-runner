package com.zimo.module.sys.apiregistry;

import java.util.List;

/**
 * 系统管理模块 API 注册表的模块分组展示对象。
 *
 * <p>分组内接口保持 Repository 返回顺序，便于前端按注册表排序值稳定展示。</p>
 *
 * @param moduleCode 业务模块标识
 * @param moduleName 业务模块显示名称
 * @param moduleBasePath 业务模块基础请求路径
 * @param moduleDesc 业务模块说明
 * @param apis 当前业务模块下的 API 列表
 * @author Codex
 * @since 2026-07-21
 */
public record SysApiRegistryModuleGroup(
        String moduleCode,
        String moduleName,
        String moduleBasePath,
        String moduleDesc,
        List<SysApiRegistryItem> apis) {
}
