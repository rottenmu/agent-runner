package com.zimo.module.sys.security;

import com.zimo.framework.common.BizException;
import com.zimo.module.auth.entity.SysUser;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class SysUserPasswordPolicy {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private SysUserPasswordPolicy() {
    }

    public static void prepareForCreate(SysUser user) {
        if (user == null) {
            throw new BizException(400, "用户信息不能为空");
        }
        if (isBlank(user.getUsername())) {
            throw new BizException(400, "用户名不能为空");
        }
        if (isBlank(user.getPassword())) {
            throw new BizException(400, "密码不能为空");
        }
        user.setPassword(encodeIfPlain(user.getPassword()));
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        if (user.getDeleted() == null) {
            user.setDeleted(0);
        }
    }

    public static void prepareForUpdate(SysUser user) {
        if (user == null) {
            throw new BizException(400, "用户信息不能为空");
        }
        if (isBlank(user.getPassword())) {
            user.setPassword(null);
            return;
        }
        user.setPassword(encodeIfPlain(user.getPassword()));
    }

    private static String encodeIfPlain(String password) {
        if (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$")) {
            return password;
        }
        return ENCODER.encode(password);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
