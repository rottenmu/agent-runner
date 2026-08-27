package com.zimo.module.ai.observ;

import java.util.List;
import java.util.Map;

/**
 * 追加式会话事件日志服务。
 *
 * <p>链路事件以 append-only 语义持久化：BEGIN/STEP/END 按 seq 单调追加，
 * 供回放、Token 计量与后续 fork/resume 派生使用。</p>
 */
public interface SessionEventLogService {

    /** 追加一条事件（seq 自动取当前链路最大 +1）。 */
    void append(SessionEventLog event);

    /** 按链路读取完整事件流（seq 升序）。 */
    List<SessionEventLog> listByTraceId(String traceId);

    /** 按会话读取完整事件流（seq 升序，跨 trace 聚合同一次会话）。 */
    List<SessionEventLog> listBySessionId(String sessionId);

    /** Token 计量：按链路统计（总 token / 事件数 / 状态）。 */
    Map<String, Object> tokenSummary(String traceId);
}
