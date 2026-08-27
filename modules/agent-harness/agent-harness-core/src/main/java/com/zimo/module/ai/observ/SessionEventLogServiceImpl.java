package com.zimo.module.ai.observ;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zimo.starter.ai.observ.TraceCollector;
import com.zimo.starter.ai.observ.TraceObserver;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

/**
 * 追加式会话事件日志实现（append-only）。
 *
 * <p>实现 {@link TraceObserver} 并注册到 {@link TraceCollector}——主链路
 * （AiAgentService）的 begin/step/end 事件零侵入流入本服务：
 * <ul>
 *   <li>onBegin → BEGIN 事件（seq=0，invoke_agent）</li>
 *   <li>onStep → STEP 事件（seq 递增，意图路由/工具调用等）</li>
 *   <li>onEnd → END 事件（seq 最后，status/prompt/response/tokens）</li>
 * </ul>
 * 事件不可修改、按 seq 单调追加，回放与 Token 计量派生自该流。</p>
 */
public class SessionEventLogServiceImpl implements SessionEventLogService, TraceObserver {

    private static final Logger log = LoggerFactory.getLogger(SessionEventLogServiceImpl.class);

    /** 事件字段最大长度（输入/输出截断，防爆表）。 */
    private static final int MAX_TEXT = 4000;

    private final SessionEventLogMapper eventMapper;
    private final JdbcTemplate jdbcTemplate;

    public SessionEventLogServiceImpl(SessionEventLogMapper eventMapper, JdbcTemplate jdbcTemplate) {
        this.eventMapper = eventMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 建表（幂等）+ 注册观测者。 */
    @PostConstruct
    public void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS observ_session_event (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  trace_id VARCHAR(64) NOT NULL,
                  session_id VARCHAR(128),
                  agent_name VARCHAR(128),
                  seq INTEGER NOT NULL DEFAULT 0,
                  event_type VARCHAR(16) NOT NULL,
                  step_type VARCHAR(32),
                  name VARCHAR(255),
                  input_text CLOB,
                  output_text CLOB,
                  status VARCHAR(32),
                  tokens INTEGER DEFAULT 0,
                  latency_ms BIGINT DEFAULT 0,
                  event_ts BIGINT NOT NULL
                )""");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_obs_evt_trace ON observ_session_event(trace_id)");
        TraceCollector.register(this);
        log.info("[session-event-log] SessionEventLogService registered (append-only)");
    }

    /* ================= append-only 写入 ================= */

    @Override
    public void append(SessionEventLog event) {
        if (event == null || !StringUtils.hasText(event.getTraceId())) {
            return;
        }
        event.setSeq(nextSeq(event.getTraceId()));
        event.setEventTs(System.currentTimeMillis());
        eventMapper.insert(event);
    }

    /** 当前链路最大 seq + 1（无事件则 0）。 */
    private int nextSeq(String traceId) {
        SessionEventLog last = eventMapper.selectOne(new LambdaQueryWrapper<SessionEventLog>()
                .eq(SessionEventLog::getTraceId, traceId)
                .orderByDesc(SessionEventLog::getSeq)
                .last("limit 1"));
        Integer max = last == null ? null : last.getSeq();
        return max == null ? 0 : max + 1;
    }

    /* ================= TraceObserver（主链路零侵入接入） ================= */

    @Override
    public void onBegin(String traceId, String sessionId, String agentId, String agentName,
                        String intent, String triggerType) {
        SessionEventLog e = new SessionEventLog();
        e.setTraceId(traceId);
        e.setSessionId(sessionId);
        e.setAgentName(agentName);
        e.setEventType(SessionEventLog.TYPE_BEGIN);
        e.setName("invoke_agent " + (agentName == null ? "" : agentName));
        e.setInputText("{\"intent\":\"" + safe(intent) + "\",\"trigger\":\"" + safe(triggerType) + "\"}");
        e.setStatus("running");
        append(e);
    }

    @Override
    public void onStep(String traceId, int seq, String stepType, String name,
                       String inputJson, String outputJson, long latencyMs, String status) {
        SessionEventLog e = new SessionEventLog();
        e.setTraceId(traceId);
        e.setEventType(SessionEventLog.TYPE_STEP);
        e.setStepType(stepType);
        e.setName(name);
        e.setInputText(truncate(inputJson));
        e.setOutputText(truncate(outputJson));
        e.setStatus(status == null ? "ok" : status);
        e.setLatencyMs(latencyMs);
        append(e);
    }

    @Override
    public void onEnd(String traceId, String status, String prompt, String response,
                      int tokens, long latencyMs) {
        SessionEventLog e = new SessionEventLog();
        e.setTraceId(traceId);
        e.setEventType(SessionEventLog.TYPE_END);
        e.setName("agent_reply");
        e.setInputText(truncate(prompt));
        e.setOutputText(truncate(response));
        e.setStatus(status == null ? "success" : status);
        e.setTokens(tokens);
        e.setLatencyMs(latencyMs);
        append(e);
    }

    /* ================= 查询 / 计量 ================= */

    @Override
    public List<SessionEventLog> listByTraceId(String traceId) {
        return eventMapper.selectList(new LambdaQueryWrapper<SessionEventLog>()
                .eq(SessionEventLog::getTraceId, traceId)
                .orderByAsc(SessionEventLog::getSeq));
    }

    @Override
    public List<SessionEventLog> listBySessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return List.of();
        }
        return eventMapper.selectList(new LambdaQueryWrapper<SessionEventLog>()
                .eq(SessionEventLog::getSessionId, sessionId)
                .orderByAsc(SessionEventLog::getSeq));
    }

    @Override
    public Map<String, Object> tokenSummary(String traceId) {
        Map<String, Object> result = new HashMap<>();
        List<SessionEventLog> events = listByTraceId(traceId);
        int tokens = 0;
        int steps = 0;
        String status = null;
        long latency = 0;
        for (SessionEventLog e : events) {
            tokens += e.getTokens() == null ? 0 : e.getTokens();
            if (SessionEventLog.TYPE_STEP.equals(e.getEventType())) {
                steps++;
            }
            if (SessionEventLog.TYPE_END.equals(e.getEventType())) {
                status = e.getStatus();
                latency = e.getLatencyMs() == null ? 0 : e.getLatencyMs();
            }
        }
        result.put("traceId", traceId);
        result.put("events", events.size());
        result.put("steps", steps);
        result.put("tokens", tokens);
        result.put("status", status);
        result.put("latencyMs", latency);
        return result;
    }

    /* ================= 工具方法 ================= */

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= MAX_TEXT ? s : s.substring(0, MAX_TEXT) + "…";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
