package com.zimo.module.ai.observ;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zimo.framework.common.validation.ValidationUtil;
import com.zimo.framework.ai.observ.TraceCollector;
import com.zimo.framework.ai.observ.TraceObserver;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 执行链路持久化与告警检测：实现 {@link TraceObserver} 写库，
 * 并检测 Agent 报错、超时、敏感词、Token 过载触发告警。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class ObservTraceService implements TraceObserver {

    private final ObservTraceMapper traceMapper;
    private final ObservTraceStepMapper stepMapper;
    private final AlertService alertService;

    public ObservTraceService(ObservTraceMapper traceMapper,
                              ObservTraceStepMapper stepMapper,
                              AlertService alertService) {
        this.traceMapper = traceMapper;
        this.stepMapper = stepMapper;
        this.alertService = alertService;
        TraceCollector.register(this);
    }

    @Override
    public void onBegin(String traceId, String sessionId, String agentId, String agentName,
                        String intent, String triggerType) {
        try {
            ObservTrace trace = new ObservTrace();
            trace.setTraceKey(traceId);
            trace.setSessionId(sessionId);
            trace.setAgentId(agentId);
            trace.setAgentName(agentName);
            trace.setIntent(intent);
            trace.setTriggerType(triggerType);
            trace.setStatus("running");
            trace.setStartedAt(LocalDateTime.now().toString());
            trace.setCreatedAt(LocalDateTime.now());
            traceMapper.insert(trace);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onStep(String traceId, int seq, String stepType, String name,
                       String inputJson, String outputJson, long latencyMs, String status) {
        try {
            ObservTrace trace = findByTraceId(traceId);
            if (trace == null) {
                return;
            }
            ObservTraceStep step = new ObservTraceStep();
            step.setTraceId(trace.getId());
            step.setSeq(seq);
            step.setStepType(stepType);
            step.setName(name);
            step.setInputJson(truncate(inputJson, 2000));
            step.setOutputJson(truncate(outputJson, 3000));
            step.setLatencyMs(latencyMs);
            step.setStatus(status);
            step.setCreatedAt(LocalDateTime.now());
            stepMapper.insert(step);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onEnd(String traceId, String status, String prompt, String response, int tokens, long latencyMs) {
        try {
            ObservTrace trace = findByTraceId(traceId);
            if (trace == null) {
                return;
            }
            trace.setStatus(status);
            trace.setPrompt(truncate(prompt, 2000));
            trace.setResponse(truncate(response, 4000));
            trace.setTokens(tokens);
            trace.setLatencyMs(latencyMs);
            trace.setEndedAt(LocalDateTime.now().toString());
            traceMapper.updateById(trace);

            // 告警检测
            detectAlerts(trace, status, response, tokens, latencyMs);
        } catch (Exception ignored) {
        }
    }

    private void detectAlerts(ObservTrace trace, String status, String response, int tokens, long latencyMs) {
        String agentId = trace.getAgentId();
        if ("failed".equals(status) || (response != null && response.startsWith("AI 智能体调用失败"))) {
            alertService.report("agent_error", "Agent 调用报错: " + truncate(response, 200), agentId);
        }
        if (latencyMs > 30_000) {
            alertService.report("timeout", "Agent 调用超时: " + latencyMs + "ms", agentId);
        }
        if (tokens > 20_000) {
            alertService.report("token_overload", "Token 消耗过载: " + tokens + " tokens", agentId);
        }
        String checkText = "";
        if (trace.getPrompt() != null) {
            checkText += trace.getPrompt();
        }
        if (response != null) {
            checkText += response;
        }
        String word = alertService.findSensitiveWord(checkText);
        if (word != null) {
            alertService.report("sensitive_word", "触发敏感词「" + word + "」", agentId);
        }
    }

    /* ---------------- 查询 ---------------- */

    /** Trace 列表。 */
    public List<ObservTrace> listTraces(String agentId, String status, String traceKey, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return traceMapper.selectList(Wrappers.<ObservTrace>lambdaQuery()
                .eq(StringUtils.hasText(agentId), ObservTrace::getAgentId, agentId)
                .eq(StringUtils.hasText(status), ObservTrace::getStatus, status)
                .likeRight(StringUtils.hasText(traceKey), ObservTrace::getTraceKey, traceKey)
                .orderByDesc(ObservTrace::getId)
                .last("LIMIT " + safeLimit));
    }

    /** 按链路 ID（traceKey）精确查询，支持完整 32 位 traceId 定位。 */
    public ObservTrace findByTraceKey(String traceKey) {
        if (!StringUtils.hasText(traceKey)) {
            return null;
        }
        return traceMapper.selectOne(Wrappers.<ObservTrace>lambdaQuery()
                .eq(ObservTrace::getTraceKey, traceKey)
                .last("LIMIT 1"));
    }

    /** 按链路 ID 查详情（含步骤时间线）。 */
    public Map<String, Object> traceDetailByKey(String traceKey) {
        ObservTrace trace = findByTraceKey(traceKey);
        ValidationUtil.requireNotNull(trace, "Trace 不存在: " + traceKey);
        return traceDetail(trace.getId());
    }

    /** Trace 详情（含步骤时间线）。 */
    public Map<String, Object> traceDetail(Long traceId) {
        ObservTrace trace = traceMapper.selectById(traceId);
        ValidationUtil.requireNotNull(trace, "Trace 不存在: ");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trace", trace);
        result.put("steps", stepMapper.selectList(Wrappers.<ObservTraceStep>lambdaQuery()
                .eq(ObservTraceStep::getTraceId, traceId)
                .orderByAsc(ObservTraceStep::getSeq)));
        return result;
    }

    /** 会话回放：按 trace 列表回放链路（步骤序列）。 */
    public Map<String, Object> replay(Long traceId) {
        return traceDetail(traceId);
    }

    private ObservTrace findByTraceId(String traceId) {
        return traceMapper.selectOne(Wrappers.<ObservTrace>lambdaQuery()
                .eq(ObservTrace::getTraceKey, traceId));
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }
}
