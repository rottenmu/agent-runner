package com.zimo.module.auth.support;

/**
 * 当前登录用户快照，用于在业务模块之间传递认证用户的基础身份信息。
 *
 * @param userId 当前登录用户 ID，来源于认证会话，不允许为空
 * @param username 当前登录用户名，用于账号识别和审计展示
 * @param nickname 当前登录用户昵称，用于页面展示，为空时 displayName 返回 username
 * @param department 当前登录用户所属部门，用于页面展示和业务隔离提示
 * @param avatar 当前登录用户头像地址，为空表示未配置头像
 * @since 2026-07-21
 */
public record CurrentLoginUser(
        Long userId,
        String username,
        String nickname,
        String department,
        String avatar) {
    /**
     * 返回适合页面展示的用户名称，优先使用昵称，昵称为空时使用账号名。
     *
     * @return 页面展示名称；昵称和账号都为空时返回空字符串
     */
    public String displayName() {
        if (nickname != null && !nickname.trim().isEmpty()) {
            return nickname.trim();
        }
        return username == null ? "" : username.trim();
    }
}