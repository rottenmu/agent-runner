package com.zimo.module.tools.autoconfig;

import com.zimo.framework.autoconfig.ModuleControllerScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * 内置工具库模块 Controller 组件扫描装配。
 *
 * <p>通过 {@code @ComponentScan} 自动注册 {@code com.zimo.module.tools.controller}
 * 包下的 {@code @RestController}（{@code ToolGovernanceController}），替代手动
 * {@code @Bean} 注册——新增控制器零配置自动生效，杜绝"新增接口 404"。</p>
 *
 * <p>使用 {@code useDefaultFilters = false} + {@code includeFilters} 精确
 * 扫描 {@code @RestController}，不连带注册 service/entity/mapper，避免与
 * {@link ToolsAutoConfiguration} 手动 Bean 冲突。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-17
 */
@AutoConfiguration
@ModuleControllerScan(basePackage = "com.zimo.module.tools.controller")
public class ToolsControllerScanAutoConfiguration {
}
