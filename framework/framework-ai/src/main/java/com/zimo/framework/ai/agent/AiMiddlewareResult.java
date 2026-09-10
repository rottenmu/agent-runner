package com.zimo.framework.ai.agent;

import com.zimo.framework.ai.AiAgentReply;

/**
 * around-middleware 瀑布统一结果（对齐 DeepSeek Harness waterfall 决策语义）。
 *
 * <ul>
 *   <li>{@link Kind#CONTINUE}：消息改写/原样，继续交给链中下一环（等价旧 REWRITE/PASS）</li>
 *   <li>{@link Kind#DENY}：短路拒绝，返回原因（等价旧 DENY）</li>
 *   <li>{@link Kind#REPLY}：短路直接回复（middleware 完全接管，不再调用模型）</li>
 * </ul>
 */
public final class AiMiddlewareResult {

    public enum Kind {
        /** 继续链：message 为改写后消息（null 表示原样）。 */
        CONTINUE,
        /** 短路拒绝：message 为拒绝原因。 */
        DENY,
        /** 短路回复：reply 为最终回复。 */
        REPLY
    }

    private final Kind kind;
    private final String message;
    private final AiAgentReply reply;

    private AiMiddlewareResult(Kind kind, String message, AiAgentReply reply) {
        this.kind = kind;
        this.message = message;
        this.reply = reply;
    }

    /** 原样继续（不改写）。 */
    public static AiMiddlewareResult pass() {
        return new AiMiddlewareResult(Kind.CONTINUE, null, null);
    }

    /** 改写消息后继续。 */
    public static AiMiddlewareResult continueWith(String newMessage) {
        return new AiMiddlewareResult(Kind.CONTINUE, newMessage, null);
    }

    /** 短路拒绝。 */
    public static AiMiddlewareResult deny(String reason) {
        return new AiMiddlewareResult(Kind.DENY, reason, null);
    }

    /** 短路直接回复（不进入模型调用）。 */
    public static AiMiddlewareResult reply(AiAgentReply reply) {
        return new AiMiddlewareResult(Kind.REPLY, null, reply);
    }

    public Kind kind() {
        return kind;
    }

    public String message() {
        return message;
    }

    public AiAgentReply reply() {
        return reply;
    }
}
