package com.zimo.module.sys.autoconfig;

import com.zimo.module.sys.autoconfig.config.MybatisDataScopePluginConfig;
import com.zimo.module.sys.autoconfig.config.WebInterceptorConfig;
import com.zimo.module.sys.mapper.SysOperLogMapper;
import com.zimo.module.sys.service.DataScopeService;
import com.zimo.module.sys.service.FieldMaskService;
import com.zimo.module.sys.service.OperLogService;
import com.zimo.module.sys.service.PermissionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration(after = SysAutoConfiguration.class)
@ConditionalOnClass(PermissionService.class)
@ConditionalOnProperty(prefix = "manufacture.permission", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PermissionProperties.class)
@Import({WebInterceptorConfig.class, MybatisDataScopePluginConfig.class})
public class PermissionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PermissionService permissionService() {
        return new PermissionService();
    }

    @Bean
    @ConditionalOnMissingBean
    public DataScopeService dataScopeService() {
        return new DataScopeService();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "manufacture.permission.field-mask", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FieldMaskService fieldMaskService() {
        return new FieldMaskService(true);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SysOperLogMapper.class)
    @ConditionalOnProperty(prefix = "manufacture.permission.operation-log", name = "enabled", havingValue = "true", matchIfMissing = true)
    public OperLogService.Repository operLogRepository(SysOperLogMapper mapper) {
        return new OperLogService.MybatisRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "manufacture.permission.operation-log", name = "enabled", havingValue = "true", matchIfMissing = true)
    public OperLogService operLogService(ObjectProvider<OperLogService.Repository> repository) {
        return new OperLogService(repository.getIfAvailable(OperLogService.InMemoryRepository::new));
    }
}
