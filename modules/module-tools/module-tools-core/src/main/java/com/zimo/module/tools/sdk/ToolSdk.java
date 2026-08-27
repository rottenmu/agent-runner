package com.zimo.module.tools.sdk;

import com.zimo.module.tools.govern.ToolExecutor;
import com.zimo.module.tools.govern.ToolRegistry;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 自定义工具 SDK：Java 代码可直接注册自定义工具。
 *
 * <p>用法：
 * <pre>
 * ToolSdk.register("my_business_api", "查询我的业务数据", true,
 *         args -> { /* 处理参数 *&#47; return "结果"; });
 * </pre>
 * 注册的工具自动进入 MCP 工具列表与智能体能力集，并受治理（权限/限流/重试/熔断/日志）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public final class ToolSdk {

    private static volatile ToolRegistry registry;

    private ToolSdk() {
    }

    /** 由自动配置注入注册表（内部）。 */
    public static void attach(ToolRegistry toolRegistry) {
        registry = toolRegistry;
    }

    /**
     * 注册自定义工具。
     *
     * @param name 工具名称（唯一）
     * @param description 工具描述
     * @param readOnly 是否只读
     * @param function 执行函数（接收参数 Map，返回结果文本）
     * @return 是否注册成功（重名返回 false）
     */
    public static boolean register(String name, String description, boolean readOnly, ToolFunction function) {
        if (registry == null || !StringUtils.hasText(name) || function == null) {
            return false;
        }
        registry.register(new ToolExecutor() {
            @Override
            public String name() {
                return name.trim();
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public boolean readOnly() {
                return readOnly;
            }

            @Override
            public String execute(Map<String, Object> arguments) throws Exception {
                return function.apply(arguments);
            }
        });
        return true;
    }

    /** 注册内置式执行器。 */
    public static boolean register(ToolExecutor executor) {
        if (registry == null || executor == null) {
            return false;
        }
        registry.register(executor);
        return true;
    }

    /** 注销工具。 */
    public static void unregister(String name) {
        if (registry != null) {
            registry.unregister(name);
        }
    }

    /**
     * 工具执行函数。
     */
    @FunctionalInterface
    public interface ToolFunction {
        /**
         * 执行工具。
         *
         * @param arguments 参数
         * @return 结果文本
         */
        String apply(Map<String, Object> arguments) throws Exception;
    }
}
