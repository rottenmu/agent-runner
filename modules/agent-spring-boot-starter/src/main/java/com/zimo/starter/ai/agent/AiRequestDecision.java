package com.zimo.starter.ai.agent;

/**
 * 请求拦截决策（对应 dsh agent/pre-step 权威决策点）。
 *
 * <p>三种语义：<ul>
 *   <li>{@link Action#PASS}：放行原消息；</li>
 *   <li>{@link Action#REWRITE}：改写消息（改写结果进入后续拦截器与模型）；</li>
 *   <li>{@link Action#DENY}：拒绝请求（请求终止，原因返回调用方）。</li>
 * </ul></p>
 */
public final class AiRequestDecision {

    public enum Action { PASS, REWRITE, DENY }

    private final Action action;
    private final String message;

    private AiRequestDecision(Action action, String message) {
        this.action = action;
        this.message = message;
    }

    /** 放行。 */
    public static AiRequestDecision pass() {
        return new AiRequestDecision(Action.PASS, null);
    }

    /** 改写：以新消息继续。 */
    public static AiRequestDecision rewrite(String newMessage) {
        return new AiRequestDecision(Action.REWRITE, newMessage);
    }

    /** 拒绝：以 reason 终止请求。 */
    public static AiRequestDecision deny(String reason) {
        return new AiRequestDecision(Action.DENY, reason);
    }

    public Action action() {
        return action;
    }

    /** REWRITE 时为新消息；DENY 时为拒绝原因。 */
    public String message() {
        return message;
    }
}
