package com.zimo.module.feishu.admin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAdminPermissionGuardTest {

    @Test
    void shouldAllowWhenApiTokenIsBlankForLocalDebugging() {
        FeishuAdminPermissionGuard guard = new FeishuAdminPermissionGuard(" ");

        assertThat(guard.isAllowed(null)).isTrue();
        assertThat(guard.isAllowed("any-token")).isTrue();
    }

    @Test
    void shouldAllowWhenRequestTokenMatchesConfiguredToken() {
        FeishuAdminPermissionGuard guard = new FeishuAdminPermissionGuard("secret-token");

        assertThat(guard.isAllowed("secret-token")).isTrue();
    }

    @Test
    void shouldRejectWhenRequestTokenDoesNotMatchConfiguredToken() {
        FeishuAdminPermissionGuard guard = new FeishuAdminPermissionGuard("secret-token");

        assertThat(guard.isAllowed(null)).isFalse();
        assertThat(guard.isAllowed("wrong-token")).isFalse();
    }
}
