package com.zimo.module.sys.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.sys.apiregistry.SysApiRegistryController;
import com.zimo.module.sys.apiregistry.SysApiRegistryRepository;
import com.zimo.module.sys.apiregistry.SysApiRegistryService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * 系统管理模块 API 注册信息自动装配配置。
 *
 * <p>本配置在框架 API 注册表完成装配后生效。仅当容器存在名为
 * {@code apiRegistryJdbcOperations} 的 JDBC 操作对象时，才显式创建 API 注册表
 * Repository、Service 和 Controller；框架 API 注册能力关闭时不会暴露管理接口。</p>
 *
 * @author Codex
 * @since 2026-07-21
 */
@AutoConfiguration(afterName = "com.zimo.framework.autoconfig.FrameworkApiRegistryAutoConfiguration")
@ConditionalOnBean(name = "apiRegistryJdbcOperations")
@ConditionalOnProperty(prefix = "plugin.sys", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SysApiRegistryAutoConfiguration {

    /**
     * 创建系统模块 API 注册表数据访问组件。
     *
     * @param jdbcOperations API 注册表专用 JDBC 操作对象，必须指向框架扫描使用的 MySQL 数据源
     * @param objectMapper JSON 元数据解析器，不允许为 {@code null}
     * @return API 注册表数据访问组件；已有同类型 Bean 时不重复创建
     */
    @Bean
    @ConditionalOnMissingBean
    public SysApiRegistryRepository sysApiRegistryRepository(
            @Qualifier("apiRegistryJdbcOperations") JdbcOperations jdbcOperations,
            ObjectMapper objectMapper) {
        return new SysApiRegistryRepository(jdbcOperations, objectMapper);
    }

    /**
     * 创建系统模块 API 注册信息业务服务。
     *
     * @param repository API 注册表数据访问组件，不允许为 {@code null}
     * @return API 注册信息业务服务；已有同类型 Bean 时不重复创建
     */
    @Bean
    @ConditionalOnMissingBean
    public SysApiRegistryService sysApiRegistryService(SysApiRegistryRepository repository) {
        return new SysApiRegistryService(repository);
    }

    /**
     * 创建系统模块 API 注册信息管理控制器。
     *
     * <p>说明：本控制器依赖 {@link SysApiRegistryService}（按
     * {@code apiRegistryJdbcOperations} bean 条件注册），因 Spring 不允许
     * {@code @ComponentScan} 配置类与 {@code @ConditionalOnBean} 同用
     * （REGISTER_BEAN 阶段限制），故保留手动 {@code @Bean} 注册，
     * 属已确认的框架限制例外。</p>
     *
     * @param service API 注册信息业务服务，不允许为 {@code null}
     * @return API 注册信息管理控制器；已有同类型 Bean 时不重复创建
     */
    @Bean
    @ConditionalOnMissingBean
    public SysApiRegistryController sysApiRegistryController(SysApiRegistryService service) {
        return new SysApiRegistryController(service);
    }
}
