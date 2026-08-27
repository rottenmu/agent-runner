package com.zimo.module.feishu.agent;

import com.zimo.module.feishu.config.FeishuConfigService;
import com.zimo.module.feishu.config.FeishuRuntimeConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

public class FeishuAgentCredentialInterceptor implements HandlerInterceptor {
    private static final String TENANT_SCAN_INIT_PATH = "/api/biz/feishu/agent/tenant-scan/init";

    private final FeishuConfigService configService;
    private final boolean validateBeforeAgentCall;

    public FeishuAgentCredentialInterceptor(FeishuConfigService configService, boolean validateBeforeAgentCall) {
        this.configService = configService;
        this.validateBeforeAgentCall = validateBeforeAgentCall;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!validateBeforeAgentCall || TENANT_SCAN_INIT_PATH.equals(request.getRequestURI())) {
            return true;
        }
        FeishuRuntimeConfig activeConfig = configService.getActiveConfig();
        if (activeConfig != null
                && StringUtils.hasText(activeConfig.getAppId())
                && StringUtils.hasText(activeConfig.getAppSecret())) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":503,\"msg\":\"飞书凭据无效或未启用\"}");
        return false;
    }
}
