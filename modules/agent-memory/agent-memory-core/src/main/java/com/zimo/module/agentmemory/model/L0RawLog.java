package com.zimo.module.agentmemory.model;

/**
 * L0 原始对话日志：金字塔最底层，承载可溯源的事件流（Trajectory 仅追加日志）。
 *
 * <p>每条 {@code trace_id} 唯一标识一次智能体交互（含工具调用链），上层
 * L1/L2/L3 记忆均携带该 ID，可反向定位到原始日志（溯源）。{@code id} 为
 * H2 自增主键，供 ETL 增量同步游标使用（插入时由数据库生成，查询映射填充）。</p>
 *
 * <p>{@code source} 为事件来源分类（Trajectory 视图按来源查看的依据）：
 * user_message / assistant_message / system_prompt / chain_of_thought /
 * tool_call / tool_result / sub_agent / context_injection / system_event；
 * 与 {@code role}（谁说的）互补，{@code source} 表达"这是什么"。历史数据为 null，
 * 视为普通消息类。</p>
 *
 * <p><b>仅追加约束</b>：本表为 append-only 设计，仓储层不提供删除入口，保障审计可信。</p>
 *
 * @param id         H2 自增主键（插入时为 0，查询映射为真实值）
 * @param traceId    溯源 ID（会话内唯一交互标识）
 * @param sessionId  会话标识
 * @param userId     用户标识
 * @param ts         事件时间戳（毫秒）
 * @param role       角色：user / assistant / tool / system
 * @param content    消息内容
 * @param tokens     token 消耗（估算）
 * @param metaJson   附加元数据 JSON（模型名、渠道、耗时、工具名、参数等）
 * @param source     事件来源分类（可空，见 Trajectory 枚举）
 */
public record L0RawLog(
        long id,
        String traceId,
        String sessionId,
        String userId,
        long ts,
        String role,
        String content,
        Integer tokens,
        String metaJson,
        String source) {

    /** 供写入使用的便捷构造（id 由数据库自增生成，source 为 null）。 */
    public static L0RawLog forInsert(
            String traceId,
            String sessionId,
            String userId,
            long ts,
            String role,
            String content,
            Integer tokens,
            String metaJson) {
        return forInsert(traceId, sessionId, userId, ts, role, content, tokens, metaJson, null);
    }

    /** 供写入使用的完整构造（id 由数据库自增生成）。 */
    public static L0RawLog forInsert(
            String traceId,
            String sessionId,
            String userId,
            long ts,
            String role,
            String content,
            Integer tokens,
            String metaJson,
            String source) {
        return new L0RawLog(0L, traceId, sessionId, userId, ts, role, content, tokens, metaJson, source);
    }

    /** 兼容旧 8 参构造（source 为 null）。 */
    public L0RawLog(
            long id,
            String traceId,
            String sessionId,
            String userId,
            long ts,
            String role,
            String content,
            Integer tokens,
            String metaJson) {
        this(id, traceId, sessionId, userId, ts, role, content, tokens, metaJson, null);
    }
}
