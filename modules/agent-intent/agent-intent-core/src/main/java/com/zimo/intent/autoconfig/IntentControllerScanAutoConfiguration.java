package com.zimo.intent.autoconfig;

import com.zimo.framework.autoconfig.ModuleControllerScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 意图识别模块 Controller 组件扫描装配。
 *
 * <p>通过 {@code @ComponentScan} 自动注册 {@code com.zimo.intent.controller}
 * 包下的 {@code @RestController}（{@code IntentController}），替代手动
 * {@code @Bean} 注册——新增控制器零配置自动生效，杜绝"新增接口 404"。</p>
 *
 * <p>使用 {@code useDefaultFilters = false} + {@code includeFilters} 精确
 * 扫描 {@code @RestController}，不连带注册 service/entity/mapper，避免与
 * {@link IntentAutoConfiguration} 手动 Bean 冲突；开关条件
 * {@code plugin.intent.enabled} 与原自动装配保持一致（关闭时 controller
 * 同样不注册，防止 Service 缺失导致启动失败）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-17
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "plugin.intent", name = "enabled", havingValue = "true", matchIfMissing = true)
@ModuleControllerScan(basePackage = "com.zimo.intent.controller")
public class IntentControllerScanAutoConfiguration {
}
