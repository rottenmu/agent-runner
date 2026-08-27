package com.zimo.module.auth.security;

import java.util.List;

public interface AuthSessionService {
    void login(Long userId);

    String getTokenValue();

    void logout();

    long getLoginIdAsLong();

    /**
     * 读取当前登录会话拥有的权限标识。
     *
     * @return 当前会话的只读权限集合；无权限时返回空集合
     */
    List<String> getPermissions();
}
