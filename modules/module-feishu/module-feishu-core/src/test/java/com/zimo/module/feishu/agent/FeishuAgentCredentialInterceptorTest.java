package com.zimo.module.feishu.agent;

import com.zimo.module.feishu.config.FeishuConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeishuAgentCredentialInterceptorTest {
    @Test
    void blocksAgentRequestsWhenNoActiveCredentialExists() throws Exception {
        FeishuConfigService configService = mock(FeishuConfigService.class);
        when(configService.getActiveConfig()).thenReturn(null);
        FeishuAgentCredentialInterceptor interceptor = new FeishuAgentCredentialInterceptor(configService, true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/biz/feishu/agent/credentials");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("飞书凭据无效");
    }

    @Test
    void allowsTenantScanInitWithoutCredential() throws Exception {
        FeishuConfigService configService = mock(FeishuConfigService.class);
        FeishuAgentCredentialInterceptor interceptor = new FeishuAgentCredentialInterceptor(configService, true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/biz/feishu/agent/tenant-scan/init");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
    }
}
