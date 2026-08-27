package com.zimo.framework.autoconfig.apiregistry;

/**
 * API 注册表批量保存统计结果。
 *
 * @param inserted 本次新增的接口数量
 * @param updated 本次更新的接口数量
 * @author Codex
 * @since 2026-07-21
 */
public record ApiRegistrySaveResult(int inserted, int updated) {
}
