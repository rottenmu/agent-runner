package com.zimo.framework.autoconfig.apiregistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Objects;

/**
 * API 注册表启动同步任务。
 *
 * <p>服务器完成 Spring 容器初始化后，依次检查数据表、扫描当前业务插件接口并持久化。
 * 任务只记录数量统计，不打印请求内容、数据库凭据或其他敏感配置。</p>
 *
 * @author Codex
 * @since 2026-07-21
 */
public class ApiRegistryStartupRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiRegistryStartupRunner.class);

    private final ApiRegistryProperties properties;
    private final ApiRegistrySchemaInitializer schemaInitializer;
    private final ApiEndpointScanner endpointScanner;
    private final ApiRegistryRepository repository;
    private final RequestMappingHandlerMapping handlerMapping;

    /**
     * 创建 API 注册表启动同步任务。
     * @param properties API 注册表配置
     * @param schemaInitializer MySQL 表结构初始化器
     * @param endpointScanner Spring MVC 接口扫描器
     * @param repository API 注册表仓储
     * @param handlerMapping 当前 Web 应用请求映射集合
     */
    public ApiRegistryStartupRunner(ApiRegistryProperties properties,
                                    ApiRegistrySchemaInitializer schemaInitializer,
                                    ApiEndpointScanner endpointScanner,
                                    ApiRegistryRepository repository,
                                    RequestMappingHandlerMapping handlerMapping) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.schemaInitializer = Objects.requireNonNull(schemaInitializer, "schemaInitializer must not be null");
        this.endpointScanner = Objects.requireNonNull(endpointScanner, "endpointScanner must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.handlerMapping = Objects.requireNonNull(handlerMapping, "handlerMapping must not be null");
    }

    /**
     * 执行 API 注册表启动同步。
     * @param args Spring Boot 启动参数，本任务不读取其中内容
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        if (properties.isInitializeSchema()) {
            schemaInitializer.initializeIfNecessary();
        }
        List<ApiEndpointMetadata> endpoints = endpointScanner.scan(handlerMapping.getHandlerMethods());
        ApiRegistrySaveResult result = repository.saveAll(endpoints);
        LOGGER.info("API registry synchronized: scanned={}, inserted={}, updated={}",
                endpoints.size(), result.inserted(), result.updated());
    }
}
