package com.zimo.module.ds.autoconfig;

import com.zimo.framework.autoconfig.ModuleControllerScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * 多数据源模块 Controller 组件扫描装配。
 *
 * <p>通过 {@code @ComponentScan} 自动注册 {@code com.zimo.module.ds} 包下的
 * {@code @RestController}（{@code DsDataSourceController}），替代手动
 * {@code @Bean} 注册——新增控制器零配置自动生效，杜绝"新增接口 404"。</p>
 *
 * <p>使用 {@code useDefaultFilters = false} + {@code includeFilters} 精确扫描
 * {@code @RestController}，不连带注册 service/entity/mapper，避免与
 * {@link DsAutoConfiguration} 手动 Bean 冲突。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-17
 */
@AutoConfiguration
@ModuleControllerScan(basePackage = "com.zimo.module.ds.controller")
public class DsControllerScanAutoConfiguration {
}
