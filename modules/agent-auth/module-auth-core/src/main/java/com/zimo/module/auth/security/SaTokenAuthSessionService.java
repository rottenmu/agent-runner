package com.zimo.module.auth.security;

import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SaTokenAuthSessionService implements AuthSessionService {
    @Override
    public void login(Long userId) {
        StpUtil.login(userId);
    }

    @Override
    public String getTokenValue() {
        return StpUtil.getTokenValue();
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public long getLoginIdAsLong() {
        return StpUtil.getLoginIdAsLong();
    }

    @Override
    public List<String> getPermissions() {
        if (!StpUtil.isLogin()) {
            return List.of();
        }
        return List.copyOf(StpUtil.getPermissionList());
    }
}
