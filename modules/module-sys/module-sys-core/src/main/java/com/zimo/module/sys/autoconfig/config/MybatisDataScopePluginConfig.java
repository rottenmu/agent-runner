package com.zimo.module.sys.autoconfig.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.zimo.module.sys.security.DataScopeMybatisPlugin;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({MybatisPlusInterceptor.class, DataScopeMybatisPlugin.class})
@ConditionalOnProperty(prefix = "manufacture.permission.data-scope", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MybatisDataScopePluginConfig {

    @Bean
    @ConditionalOnMissingBean
    public DataScopeMybatisPlugin dataScopeMybatisPlugin() {
        return new DataScopeMybatisPlugin();
    }

    @Bean
    @ConditionalOnBean(MybatisPlusInterceptor.class)
    public SmartInitializingSingleton dataScopeMybatisPluginRegistrar(
            ObjectProvider<MybatisPlusInterceptor> interceptors,
            DataScopeMybatisPlugin dataScopeMybatisPlugin
    ) {
        return () -> interceptors.forEach(interceptor -> registerDataScopePlugin(interceptor, dataScopeMybatisPlugin));
    }

    private void registerDataScopePlugin(MybatisPlusInterceptor interceptor, DataScopeMybatisPlugin dataScopeMybatisPlugin) {
        List<?> innerInterceptors = interceptor.getInterceptors();
        boolean alreadyRegistered = innerInterceptors.stream().anyMatch(DataScopeMybatisPlugin.class::isInstance);
        if (!alreadyRegistered) {
            interceptor.addInnerInterceptor(dataScopeMybatisPlugin);
        }
    }
}
