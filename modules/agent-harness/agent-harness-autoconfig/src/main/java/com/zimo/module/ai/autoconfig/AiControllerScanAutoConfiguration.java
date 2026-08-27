package com.zimo.module.ai.autoconfig;

import com.zimo.framework.autoconfig.ModuleControllerScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

/**
 * 模块 Controller 组件扫描装配。
 *
 * <p>通过 {@code @ComponentScan} 自动注册 {@code com.zimo.module.ai.controller}
 * 包下全部 {@code @RestController}（14 个），替代在各 AutoConfiguration 中
 * 手写 {@code @Bean} 注册——新控制器零配置自动生效，杜绝"新增接口 404"。</p>
 *
 * <p>开关条件与 {@link AiSkillAdminAutoConfiguration} 对齐：{@code plugin.ai.enabled}
 * 为模块总开关，{@code ai.agent.enabled} 为 Agent 运行时开关（controller 依赖的
 * Service/Mapper 由 {@code AiSkillAdminAutoConfiguration} 按这两个条件 + 
 * {@code @ConditionalOnBean(AiSkillRegistry.class)} 注册）。两个 PARSE 阶段条件
 * 与扫描兼容，可安全透传——任一关闭时 controller 不注册，防止依赖缺失导致
 * 启动失败。</p>
 *
 * <p>注意：仅扫描 controller 子包（该包除 {@code AiAdminControllerSupport}
 * 辅助类外均为控制器，无其他组件），collab/observ 包保持手动注册以避免
 * 与手动 Bean 冲突。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-17
 */
@AutoConfiguration
@ConditionalOnExpression("${plugin.ai.enabled:true} && ${ai.agent.enabled:true}")
@ModuleControllerScan(basePackage = "com.zimo.module.ai.controller")
public class AiControllerScanAutoConfiguration {
}
