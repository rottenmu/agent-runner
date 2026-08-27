package com.zimo.module.tools.govern;

import java.util.Map;

/**
 * 动态工具执行器（插件工具 / Python 脚本 / Java SDK 注册）。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public interface ToolExecutor {

    /** 工具名称（唯一，进入 MCP 工具列表）。 */
    String name();

    /** 工具描述（提供给模型的说明）。 */
    String description();

    /** 是否只读（只读工具可安全重试）。 */
    boolean readOnly();

    /**
     * 执行工具。
     *
     * @param arguments 参数
     * @return 结果
     */
    String execute(Map<String, Object> arguments) throws Exception;
}
