package com.zimo.framework.ai.agent;

/**
 * 会话隔离键的解析工具（M4-3）。
 *
 * <p>{@link AiHarnessSessionKeyFactory} 产出的键是 5 个「长度:内容」字段的顺序拼接：
 * {@code tenantId | agentId | channel | conversationId | userId}。本类做它的逆运算。</p>
 *
 * <h2>为什么需要反解</h2>
 * <p>AgentScope 的 {@code ReActAgent} 存 state 时用 {@code SlotRef} 作分区键，
 * 而 {@code SlotRef.parse} 只按<b>最后一个</b> {@code /} 切分 —— 左边全部当 userId、
 * 右边当 sessionId。本仓的键里冒号是分隔符而 {@code /} 只可能出现在内容中，
 * 因此实际存储布局是 {@code astate/{userId}/{整个 sessionKey}/agent_state}。
 * 要按会话去查/清 AgentState，就必须把 userId 从键里取出来。</p>
 *
 * <h2>为什么不用 split(":")</h2>
 * <p>内容里可能含冒号（如 conversationId 是 {@code m4:sess}），直接 split 会切错。
 * 必须按「长度前缀」逐步推进游标，这也是工厂侧编码的对称实现。</p>
 *
 * @author WorkBuddy
 * @since 2026-09-17
 */
public final class AiHarnessSessionKey {

    /** 解析失败时的兜底段值，与 {@code RocksdbAgentStateStore.safe()} 的空值形态一致。 */
    public static final String PLACEHOLDER = "_";

    private AiHarnessSessionKey() {
    }

    /**
     * 取出键的最后一个字段（即 userId）。
     *
     * <p>解析中途遇到非法长度前缀时<b>停止推进</b>并返回已解析到的最后一段 ——
     * 而不是抛异常：调用方是收尾清理路径，宁可拿到一个可能不准的 userId
     * （后果只是清理不生效并留下日志），也不该让会话主流程因收尾失败而报错。</p>
     *
     * @param sessionKey 会话隔离键，允许为空
     * @return 最后一段内容；为空或无法解析时返回 {@link #PLACEHOLDER}
     */
    public static String userIdOf(String sessionKey) {
        return segmentOf(sessionKey, -1);
    }

    /**
     * 取出键的第一个字段（即 tenantId）。
     *
     * @param sessionKey 会话隔离键，允许为空
     * @return 第一段内容；为空或无法解析时返回 {@link #PLACEHOLDER}
     */
    public static String tenantIdOf(String sessionKey) {
        return segmentOf(sessionKey, 0);
    }

    /**
     * 按索引取段。
     *
     * @param sessionKey 会话隔离键
     * @param wanted     目标段下标；小于 0 表示取最后一段
     * @return 目标段；取不到时返回 {@link #PLACEHOLDER}
     */
    public static String segmentOf(String sessionKey, int wanted) {
        if (sessionKey == null || sessionKey.isEmpty()) {
            return PLACEHOLDER;
        }
        String last = null;
        String first = null;
        int cursor = 0;
        int index = 0;
        while (cursor < sessionKey.length()) {
            int colon = sessionKey.indexOf(':', cursor);
            if (colon < 0) {
                break;
            }
            int length;
            try {
                length = Integer.parseInt(sessionKey.substring(cursor, colon));
            } catch (NumberFormatException e) {
                break;
            }
            if (length < 0) {
                break;
            }
            int start = colon + 1;
            int end = Math.min(start + length, sessionKey.length());
            String value = sessionKey.substring(start, end);
            if (index == 0) {
                first = value;
            }
            last = value;
            if (wanted >= 0 && index == wanted) {
                return text(value);
            }
            cursor = end;
            index++;
        }
        if (wanted == 0) {
            return text(first);
        }
        return text(last);
    }

    private static String text(String value) {
        return value == null || value.trim().isEmpty() ? PLACEHOLDER : value;
    }
}
