package com.zimo.module.ai.tool;

/**
 * 工具调用结果（治理层统一视图）。
 *
 * @param success 调用是否成功
 * @param content 成功时为结果文本，失败时为空串
 * @param error 失败原因；成功时为 {@code null}
 * @author WorkBuddy
 * @since 2026-10-20
 */
public record ToolInvokeResult(boolean success, String content, String error) {

    /**
     * 构造成功结果。
     *
     * @param content 结果文本，可为 {@code null}（归一化为空串）
     * @return 成功结果
     */
    public static ToolInvokeResult ok(String content) {
        return new ToolInvokeResult(true, content == null ? "" : content, null);
    }

    /**
     * 构造失败结果。
     *
     * @param error 失败原因
     * @return 失败结果
     */
    public static ToolInvokeResult fail(String error) {
        return new ToolInvokeResult(false, "", error == null ? "执行失败" : error);
    }
}
