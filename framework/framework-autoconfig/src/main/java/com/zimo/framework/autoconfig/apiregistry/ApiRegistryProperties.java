package com.zimo.framework.autoconfig.apiregistry;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API 注册表全局配置属性。
 *
 * <p>统一绑定 {@code framework.api-registry} 前缀，控制启动扫描、建表初始化、接口版本和
 * 多数据源选择。多数据源应用应显式配置数据源 Bean 名称。</p>
 *
 * @author Codex
 * @since 2026-07-21
 */
@ConfigurationProperties(prefix = "framework.api-registry")
public class ApiRegistryProperties {

    /** 是否启用 API 启动扫描和持久化。 */
    private boolean enabled = true;

    /** 是否在表缺失时执行 MySQL 建表脚本。 */
    private boolean initializeSchema = true;

    /** 扫描接口登记的版本号。 */
    private String version = "1.0.0";

    /** 目标数据源 Bean 名称；多数据源应用必须配置。 */
    private String dataSourceBeanName = "";

    /**
     * 判断是否启用 API 注册表。
     * @return {@code true} 表示服务器启动时执行接口扫描
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 API 注册表总开关。
     * @param enabled 是否启用启动扫描
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 判断是否允许自动创建缺失的数据表。
     * @return {@code true} 表示表缺失时执行 MySQL DDL
     */
    public boolean isInitializeSchema() {
        return initializeSchema;
    }

    /**
     * 设置数据表初始化开关。
     * @param initializeSchema 是否允许执行建表脚本
     */
    public void setInitializeSchema(boolean initializeSchema) {
        this.initializeSchema = initializeSchema;
    }

    /**
     * 获取接口登记版本。
     * @return 非空版本字符串
     */
    public String getVersion() {
        return version;
    }

    /**
     * 设置接口登记版本。
     * @param version 写入注册表的版本号
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * 获取目标数据源 Bean 名称。
     * @return Bean 名称；空字符串表示按主数据源或唯一数据源推导
     */
    public String getDataSourceBeanName() {
        return dataSourceBeanName;
    }

    /**
     * 设置目标数据源 Bean 名称。
     * @param dataSourceBeanName 多数据源环境中的目标 Bean 名称
     */
    public void setDataSourceBeanName(String dataSourceBeanName) {
        this.dataSourceBeanName = dataSourceBeanName;
    }
}
