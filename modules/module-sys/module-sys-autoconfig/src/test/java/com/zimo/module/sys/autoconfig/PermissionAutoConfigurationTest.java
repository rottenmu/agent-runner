package com.zimo.module.sys.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.zimo.module.sys.security.DataScopeMybatisPlugin;
import com.zimo.module.sys.security.OperLogInterceptor;
import com.zimo.module.sys.security.PermissionInterceptor;
import com.zimo.module.sys.service.DataScopeService;
import com.zimo.module.sys.service.FieldMaskService;
import com.zimo.module.sys.service.OperLogService;
import com.zimo.module.sys.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class PermissionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PermissionAutoConfiguration.class));

    @Test
    void createsPermissionBeansByDefault() {
        contextRunner
                .withUserConfiguration(MybatisPlusUserConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(PermissionService.class);
                    assertThat(context).hasSingleBean(DataScopeService.class);
                    assertThat(context).hasSingleBean(FieldMaskService.class);
                    assertThat(context).hasSingleBean(OperLogService.class);
                    assertThat(context).hasSingleBean(PermissionInterceptor.class);
                    assertThat(context).hasSingleBean(OperLogInterceptor.class);
                    assertThat(context).hasSingleBean(DataScopeMybatisPlugin.class);
                    assertThat(context.getBean(MybatisPlusInterceptor.class).getInterceptors())
                            .anyMatch(DataScopeMybatisPlugin.class::isInstance);
                });
    }

    @Test
    void backsOffWhenPermissionSwitchDisabled() {
        contextRunner
                .withPropertyValues("manufacture.permission.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(PermissionService.class);
                    assertThat(context).doesNotHaveBean(PermissionInterceptor.class);
                    assertThat(context).doesNotHaveBean(DataScopeMybatisPlugin.class);
                });
    }

    @Test
    void respectsFeatureLevelSwitches() {
        contextRunner
                .withUserConfiguration(MybatisPlusUserConfiguration.class)
                .withPropertyValues(
                        "manufacture.permission.data-scope.enabled=false",
                        "manufacture.permission.field-mask.enabled=false",
                        "manufacture.permission.operation-log.enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(PermissionService.class);
                    assertThat(context).doesNotHaveBean(DataScopeMybatisPlugin.class);
                    assertThat(context).doesNotHaveBean(FieldMaskService.class);
                    assertThat(context).doesNotHaveBean(OperLogInterceptor.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class MybatisPlusUserConfiguration {

        @Bean
        MybatisPlusInterceptor mybatisPlusInterceptor() {
            return new MybatisPlusInterceptor();
        }
    }
}
