package com.zimo.module.auth.autoconfig;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.zimo.module.auth.mapper.SysUserMapper;
import com.zimo.module.auth.security.SysStpInterface;
import com.zimo.module.auth.service.SysRbacService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration(afterName = "com.zimo.module.sys.autoconfig.SysAutoConfiguration")
@ConditionalOnProperty(prefix = "plugin.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AuthProperties.class)
@ComponentScan(
        basePackages = "com.zimo.module.auth",
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
@MapperScan(basePackageClasses = SysUserMapper.class)
public class AuthAutoConfiguration implements WebMvcConfigurer {

    private final AuthProperties properties;

    public AuthAutoConfiguration(AuthProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnBean(SysRbacService.class)
    @ConditionalOnMissingBean(StpInterface.class)
    public SysStpInterface sysStpInterface(SysRbacService rbacService) {
        return new SysStpInterface(rbacService);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        AuthProperties.Interceptor interceptor = properties.getInterceptor();
        if (!interceptor.isEnabled()) {
            return;
        }
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns(interceptor.getIncludePaths())
                .excludePathPatterns(interceptor.getExcludePaths());
    }
}
