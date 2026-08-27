package com.zimo.module.auth.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

    @Test
    void defaultInterceptorAllowsPublicAuthEntryPointsWithoutAuthentication() {
        AuthProperties properties = new AuthProperties();

        assertThat(properties.getInterceptor().getIncludePaths()).containsExactly("/api/**");
        assertThat(properties.getInterceptor().getExcludePaths())
                .containsExactly(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/plugins",
                        "/api/channel/inbound",
                        "/api/channel/sdk.js",
                        "/api/mobile/wms/stock/inbound",
                        "/api/mobile/wms/stock/outbound",
                        "/api/v1/**");
    }
}
