package com.zimo.module.sys.autoconfig.config;

import com.zimo.module.sys.autoconfig.PermissionProperties;
import com.zimo.module.sys.security.OperLogInterceptor;
import com.zimo.module.sys.security.PermissionInterceptor;
import com.zimo.module.sys.service.OperLogService;
import com.zimo.module.sys.service.PermissionService;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({WebMvcConfigurer.class, HandlerInterceptor.class})
public class WebInterceptorConfig implements WebMvcConfigurer {

    private final PermissionProperties properties;
    private final ObjectProvider<PermissionInterceptor> permissionInterceptor;
    private final ObjectProvider<OperLogInterceptor> operLogInterceptor;

    public WebInterceptorConfig(
            PermissionProperties properties,
            ObjectProvider<PermissionInterceptor> permissionInterceptor,
            ObjectProvider<OperLogInterceptor> operLogInterceptor
    ) {
        this.properties = properties;
        this.permissionInterceptor = permissionInterceptor;
        this.operLogInterceptor = operLogInterceptor;
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionInterceptor permissionInterceptor(PermissionProperties properties, PermissionService permissionService) {
        return new PermissionInterceptor(properties.isEnabled(), whitelistPaths(properties), permissionService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "manufacture.permission.operation-log", name = "enabled", havingValue = "true", matchIfMissing = true)
    public OperLogInterceptor operLogInterceptor(OperLogService operLogService, PermissionProperties properties) {
        return new OperLogInterceptor(
                operLogService,
                properties.getOperationLog().isEnabled(),
                safePaths(properties.getOperationLog().getExcludePaths())
        );
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Temporarily disable RBAC permission interceptor registration.
        // permissionInterceptor.ifAvailable(interceptor -> registry.addInterceptor(interceptor)
        //         .addPathPatterns("/**")
        //         .excludePathPatterns(whitelistPaths(properties)));
        operLogInterceptor.ifAvailable(interceptor -> registry.addInterceptor(interceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(safePaths(properties.getOperationLog().getExcludePaths())));
    }

    private List<String> whitelistPaths(PermissionProperties properties) {
        if (properties.getWhitelist() == null || !properties.getWhitelist().isEnabled()) {
            return Collections.emptyList();
        }
        return safePaths(properties.getWhitelist().getPaths());
    }

    private List<String> safePaths(List<String> paths) {
        return paths == null ? Collections.emptyList() : paths;
    }
}
