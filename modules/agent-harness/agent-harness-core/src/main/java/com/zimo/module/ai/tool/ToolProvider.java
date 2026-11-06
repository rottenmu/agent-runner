package com.zimo.module.ai.tool;

import java.util.Map;

/**
 * 工具执行后端 SPI。
 *
 * <p>所属模块：agent-harness 工具治理层。<b>本接口刻意不实现任何执行能力</b>：
 * 它只是「治理目录」与「运行时」之间的接缝 —— 目录知道某个工具该走哪个后端，
 * 但真正的进程沙箱、MCP 工具桥都在 {@code framework-ai}，harness 不重复造执行器。</p>
 *
 * <p>新增后端只需实现本接口并交给 Spring 管理，{@link ToolRegistryServiceImpl}
 * 会按 {@link #type()} 自动收录；无需改动调用方。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public interface ToolProvider {

    /**
     * 后端类型标识，需与 {@link ToolMeta#getProviderType()} 取值一致。
     *
     * @return 类型标识（如 local / mcp / sandbox）
     */
    String type();

    /**
     * 执行工具。
     *
     * <p>实现约定：<b>执行级失败以结果形式返回</b>而不抛异常，
     * 便于治理层统一记录审计；仅参数非法等调用方错误可抛
     * {@link IllegalArgumentException}。</p>
     *
     * @param meta 工具元数据（含 {@code providerRef}）
     * @param args 调用参数
     * @return 调用结果
     */
    ToolInvokeResult invoke(ToolMeta meta, Map<String, Object> args);
}
