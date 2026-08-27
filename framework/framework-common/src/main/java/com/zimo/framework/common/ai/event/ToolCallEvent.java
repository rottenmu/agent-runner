package com.zimo.framework.common.ai.event;

/**
 * 工具事件（能力域）：工具调用前后（对应 dsh tools/* 流水线）。
 *
 * @param toolName 工具名
 * @param phase    pre / post / error
 * @param params   调用参数
 * @param result   调用结果（post）
 * @param error    错误信息（error）
 * @param ts       时间戳（ms）
 */
public record ToolCallEvent(
        String toolName,
        String phase,
        Object params,
        Object result,
        String error,
        long ts) {
}
