package com.zimo.starter.ai.mcp;

import com.zimo.starter.ai.skill.AiSkillResult;
import java.util.List;
import java.util.Map;

/**
 * 动态工具桥接：允许模块（如插件市场/自定义工具）将动态工具合并进 MCP 工具列表与调用。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public interface ToolBridge {

    /**
     * 动态工具描述列表（并入 MCP tools/list）。
     *
     * @return 工具描述 Map 列表（含 name/description/readOnly）
     */
    List<Map<String, Object>> listTools();

    /**
     * 调用动态工具（已内置治理：限流/熔断/日志）。
     *
     * @param name 工具名
     * @param arguments 参数
     * @return 调用结果
     */
    AiSkillResult call(String name, Map<String, Object> arguments);
}
