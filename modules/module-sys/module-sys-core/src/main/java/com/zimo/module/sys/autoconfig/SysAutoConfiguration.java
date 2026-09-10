package com.zimo.module.sys.autoconfig;

import com.zimo.module.sys.SysPluginRegister;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 系统管理插件主自动装配配置。
 *
 * <p>负责扫描系统管理通用组件、Mapper 和配置属性，并注册插件身份。API 注册信息管理包
 * 由专用条件自动配置显式装配，避免在注册表 JDBC 组件缺失时提前创建 Controller。</p>
 *
 * @author Codex
 * @since 2026-07-21
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "plugin.sys", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({SysProperties.class, PermissionProperties.class})
@ComponentScan(
        basePackages = "com.zimo.module.sys",
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.zimo\\.module\\.sys\\.apiregistry\\..*"))
@MapperScan({"com.zimo.module.sys.mapper"})
public class SysAutoConfiguration {
    /**
     * 创建系统管理插件注册信息。
     *
     * @return 系统管理插件注册对象
     */
    @Bean
    public SysPluginRegister sysPluginRegister() {
        return new SysPluginRegister();
    }
}
