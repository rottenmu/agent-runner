package com.zimo.framework.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.autoconfig.apiregistry.ApiEndpointScanner;
import com.zimo.framework.autoconfig.apiregistry.ApiRegistryProperties;
import com.zimo.framework.autoconfig.apiregistry.ApiRegistryRepository;
import com.zimo.framework.autoconfig.apiregistry.ApiRegistrySchemaInitializer;
import com.zimo.framework.autoconfig.apiregistry.ApiRegistryStartupRunner;
import com.zimo.framework.common.PluginRegister;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.sql.DataSource;
import java.util.List;

/**
 * API 注册表自动装配配置。
 *
 * <p>Web 应用启用 {@code framework.api-registry.enabled} 后装配扫描、MySQL 初始化、持久化和
 * 启动同步组件。多数据源应用通过 {@code data-source-bean-name} 明确注册表所在数据库。</p>
 *
 * @author Codex
 * @since 2026-07-21
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "com.zimo.module.feishu.autoconfig.FeishuAutoConfiguration"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({DataSource.class, JdbcOperations.class, RequestMappingHandlerMapping.class})
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "framework.api-registry", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ApiRegistryProperties.class)
public class FrameworkApiRegistryAutoConfiguration {

    /**
     * 创建业务插件接口扫描器。
     * @param plugins 当前加载的插件注册信息
     * @param objectMapperProvider 容器 JSON 序列化器，可为空
     * @param properties API 注册表配置
     * @return API 接口扫描器
     */
    @Bean
    public ApiEndpointScanner apiEndpointScanner(List<PluginRegister> plugins,
                                                 ObjectProvider<ObjectMapper> objectMapperProvider,
                                                 ApiRegistryProperties properties) {
        return new ApiEndpointScanner(plugins, objectMapperProvider.getIfAvailable(ObjectMapper::new),
                properties.getVersion());
    }

    /**
     * 创建 API 注册表专用 JDBC 操作对象。
     * @param beanFactory 用于按名称或唯一候选解析数据源
     * @param properties API 注册表配置
     * @return 指向目标 MySQL 数据源的 JDBC 操作对象
     */
    @Bean(name = "apiRegistryJdbcOperations")
    public JdbcOperations apiRegistryJdbcOperations(ListableBeanFactory beanFactory,
                                                     ApiRegistryProperties properties) {
        return new JdbcTemplate(resolveDataSource(beanFactory, properties));
    }

    /**
     * 创建 API 注册表结构初始化器。
     * @param jdbcOperations 注册表专用 JDBC 操作对象
     * @return MySQL 表结构初始化器
     */
    @Bean
    public ApiRegistrySchemaInitializer apiRegistrySchemaInitializer(
            @Qualifier("apiRegistryJdbcOperations") JdbcOperations jdbcOperations) {
        return new ApiRegistrySchemaInitializer(jdbcOperations,
                new ClassPathResource("db/framework/api_registry.sql"));
    }

    /**
     * 创建 API 注册表仓储。
     * @param jdbcOperations 注册表专用 JDBC 操作对象
     * @return API 注册表仓储
     */
    @Bean
    public ApiRegistryRepository apiRegistryRepository(
            @Qualifier("apiRegistryJdbcOperations") JdbcOperations jdbcOperations) {
        return new ApiRegistryRepository(jdbcOperations);
    }

    /**
     * 创建服务器启动后的 API 同步任务。
     * @param properties API 注册表配置
     * @param initializer MySQL 表结构初始化器
     * @param scanner 业务插件接口扫描器
     * @param repository API 注册表仓储
     * @param handlerMapping Spring MVC 请求映射集合
     * @return API 注册表启动任务
     */
    @Bean
    public ApiRegistryStartupRunner apiRegistryStartupRunner(
            ApiRegistryProperties properties,
            ApiRegistrySchemaInitializer initializer,
            ApiEndpointScanner scanner,
            ApiRegistryRepository repository,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        return new ApiRegistryStartupRunner(properties, initializer, scanner, repository, handlerMapping);
    }

    private DataSource resolveDataSource(ListableBeanFactory beanFactory, ApiRegistryProperties properties) {
        String configuredName = properties.getDataSourceBeanName();
        if (StringUtils.hasText(configuredName)) {
            return beanFactory.getBean(configuredName, DataSource.class);
        }
String[] candidates = beanFactory.getBeanNamesForType(DataSource.class);
        if (candidates.length == 1) {
            return beanFactory.getBean(candidates[0], DataSource.class);
        }
        throw new IllegalStateException(
                "存在多个或不存在数据源，请配置 framework.api-registry.data-source-bean-name");
    }
}
