package com.zimo.module.sys.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.module.auth.entity.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class SysUserPasswordPolicyTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void encodePlainPasswordWhenCreatingUser() {
        SysUser user = new SysUser();
        user.setUsername("demo");
        user.setPassword("demo123");

        SysUserPasswordPolicy.prepareForCreate(user);

        assertThat(user.getPassword()).startsWith("$2");
        assertThat(encoder.matches("demo123", user.getPassword())).isTrue();
        assertThat(user.getStatus()).isEqualTo(1);
        assertThat(user.getDeleted()).isEqualTo(0);
    }

    @Test
    void clearBlankPasswordWhenUpdatingUser() {
        SysUser user = new SysUser();
        user.setPassword(" ");

        SysUserPasswordPolicy.prepareForUpdate(user);

        assertThat(user.getPassword()).isNull();
    }
}
