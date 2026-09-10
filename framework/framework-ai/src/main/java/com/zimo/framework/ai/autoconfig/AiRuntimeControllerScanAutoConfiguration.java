package com.zimo.framework.ai.autoconfig;

import com.zimo.framework.autoconfig.ModuleControllerScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * AI Agent 框架运行时 Controller 组件扫描装配。
 *
 * <p>通过 {@code @ComponentScan} 自动注册 {@code com.zimo.framework.ai} 包下的
 * {@code @RestController}（{@code McpController} / {@code A2aController} /
 * {@code AiMemoryController}，分布于 mcp/a2a/memory 子包），替代手动
 * {@code @Bean} 注册——新增控制器零配置自动生效，杜绝"新增接口 404"。</p>
 *
 * <p>使用 {@code useDefaultFilters = false} + {@code includeFilters} 精确扫描
 * {@code @RestController}，不连带注册 harness/skill/channel/service 等组件，
 * 避免与 {@link AiAgentAutoConfiguration} 手动 Bean 冲突。开关条件
 * {@code ai.agent.enabled} 与原自动装配一致（PARSE 阶段条件，与扫描兼容）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-17
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ai.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
@ModuleControllerScan(basePackage = "com.zimo.framework.ai")
public class AiRuntimeControllerScanAutoConfiguration {
}
