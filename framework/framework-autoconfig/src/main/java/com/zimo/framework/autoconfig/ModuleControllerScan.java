package com.zimo.framework.autoconfig;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * 模块 Controller 组件扫描元注解。
 *
 * <p>统一各模块 {@code @RestController} 扫描样板：固定
 * {@code useDefaultFilters=false} + {@code @RestController} includeFilters +
 * 全限定类名 bean 名（等价于
 * {@code FullyQualifiedAnnotationBeanNameGenerator}），杜绝连带注册非
 * controller 组件导致与手动 {@code @Bean} 冲突。</p>
 *
 * <p>用法（各模块 autoconfig 中）：</p>
 * <pre>{@code
 * @AutoConfiguration
 * @ConditionalOnProperty(prefix = "plugin.xxx", name = "enabled", ...) // 可选
 * @ModuleControllerScan(basePackage = "com.zimo.module.xxx")
 * public class XxxControllerScanAutoConfiguration {
 * }
 * }</pre>
 *
 * <p>注意：与 {@code @ConditionalOnBean} 同用受 Spring 框架限制
 * （REGISTER_BEAN 阶段评估），沿用既有约束——凡 controller 依赖按 bean
 * 条件注册的 Service 的模块，保持手动 {@code @Bean}。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-17
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ModuleControllerScanRegistrar.class)
public @interface ModuleControllerScan {

    /**
     * 待扫描的 controller 包（如 {@code com.zimo.module.rag.controller}）。
     */
    String basePackage();
}
