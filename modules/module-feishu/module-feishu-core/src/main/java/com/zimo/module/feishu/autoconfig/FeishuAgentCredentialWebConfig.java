package com.zimo.module.feishu.autoconfig;

import com.zimo.module.feishu.agent.FeishuAgentCredentialInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class FeishuAgentCredentialWebConfig implements WebMvcConfigurer {
    private final FeishuAgentCredentialInterceptor interceptor;

    public FeishuAgentCredentialWebConfig(FeishuAgentCredentialInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/biz/feishu/agent/**")
                .excludePathPatterns("/api/biz/feishu/agent/tenant-scan/init");
    }
}
