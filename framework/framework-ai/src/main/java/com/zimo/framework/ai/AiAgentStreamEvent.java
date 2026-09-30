package com.zimo.framework.ai;

/**
 * 流式轮次事件：framework 层的协议，与具体传输（SSE / WebSocket）无关。
 *
 * <p>刻意只定义三种语义，不把 SSE 的帧结构泄漏到 framework 层 ——
 * 传输通道换了（例如以后改 WebSocket），这里不用动。</p>
 *
 * <p><b>为什么 {@link Kind#DONE} 还要带全文</b>：前端增量拼接一旦有一块丢失，
 * 累积结果就与后端不一致，且这种偏差极难发现（用户只看到少几个字）。
 * 收尾时把权威全文一并送达，前端可据此校正 —— 便宜且能兜住这类静默损坏。</p>
 *
 * @param kind      事件语义
 * @param text      增量块（DELTA）/ 累计全文（DONE）/ 脱敏错误说明（ERROR）
 * @param elapsedMs 轮次耗时；仅 DONE / ERROR 有意义
 * @author WorkBuddy
 * @since 2026-09-18
 */
public record AiAgentStreamEvent(Kind kind, String text, long elapsedMs) {

    /** 事件语义 */
    public enum Kind {
        /** 增量文本块（逐字/逐块），调用方应追加而非替换 */
        DELTA,
        /** 轮次正常结束，{@code text} 为累计全文 */
        DONE,
        /** 轮次失败，{@code text} 为脱敏后的错误说明（已含归因与处置建议） */
        ERROR
    }

    public static AiAgentStreamEvent delta(String text) {
        return new AiAgentStreamEvent(Kind.DELTA, text == null ? "" : text, 0L);
    }

    public static AiAgentStreamEvent done(String fullText, long elapsedMs) {
        return new AiAgentStreamEvent(Kind.DONE, fullText == null ? "" : fullText, elapsedMs);
    }

    public static AiAgentStreamEvent error(String message, long elapsedMs) {
        return new AiAgentStreamEvent(Kind.ERROR, message == null ? "" : message, elapsedMs);
    }
}
