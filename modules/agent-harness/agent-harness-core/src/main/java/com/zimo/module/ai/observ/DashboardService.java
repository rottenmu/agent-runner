package com.zimo.module.ai.observ;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zimo.module.ai.collab.AgentTask;
import com.zimo.module.ai.collab.AgentTaskMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据大盘服务：调用量、成功率、平均耗时、知识库命中率、业务完成率指标聚合。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class DashboardService {

    private final ObservTraceMapper traceMapper;
    private final ObservTraceStepMapper stepMapper;
    private final AgentTaskMapper agentTaskMapper;

    public DashboardService(ObservTraceMapper traceMapper,
                            ObservTraceStepMapper stepMapper,
                            AgentTaskMapper agentTaskMapper) {
        this.traceMapper = traceMapper;
        this.stepMapper = stepMapper;
        this.agentTaskMapper = agentTaskMapper;
    }

    /** 指标总览。 */
    public Map<String, Object> summary() {
        Long total = traceMapper.selectCount(null);
        Long ok = traceMapper.selectCount(Wrappers.<ObservTrace>lambdaQuery().eq(ObservTrace::getStatus, "ok"));
        Long failed = traceMapper.selectCount(Wrappers.<ObservTrace>lambdaQuery().eq(ObservTrace::getStatus, "failed"));

        long latencySum = 0;
        int latencyCount = 0;
        for (ObservTrace trace : traceMapper.selectList(Wrappers.<ObservTrace>lambdaQuery()
                .isNotNull(ObservTrace::getLatencyMs))) {
            latencySum += trace.getLatencyMs() == null ? 0 : trace.getLatencyMs();
            latencyCount++;
        }

        // 知识库命中率：knowledge_retrieval 步骤中 hits>0 的比例
        Long retrievalSteps = stepMapper.selectCount(Wrappers.<ObservTraceStep>lambdaQuery()
                .eq(ObservTraceStep::getStepType, "knowledge_retrieval"));
        Long hitSteps = stepMapper.selectCount(Wrappers.<ObservTraceStep>lambdaQuery()
                .eq(ObservTraceStep::getStepType, "knowledge_retrieval")
                .like(ObservTraceStep::getOutputJson, "\"hits\":")
                .notLike(ObservTraceStep::getOutputJson, "\"hits\":0"));

        // 业务完成率：协同任务中 done/approved 的比例
        Long taskTotal = agentTaskMapper.selectCount(null);
        Long taskDone = agentTaskMapper.selectCount(Wrappers.<AgentTask>lambdaQuery()
                .in(AgentTask::getStatus, "done", "approved"));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCalls", total);
        summary.put("successRate", percent(ok, total));
        summary.put("failCount", failed);
        summary.put("avgLatencyMs", latencyCount == 0 ? 0 : Math.round(latencySum * 1.0 / latencyCount));
        summary.put("kbHitRate", percent(hitSteps, retrievalSteps));
        summary.put("retrievalCount", retrievalSteps);
        summary.put("businessCompletionRate", percent(taskDone, taskTotal));
        summary.put("taskTotal", taskTotal);
        return summary;
    }

    /** 近 N 天趋势（调用量/成功率）。 */
    public Map<String, Object> trend(int days) {
        int safeDays = Math.min(Math.max(days, 1), 30);
        Map<String, Map<String, Long>> byDay = new LinkedHashMap<>();
        List<ObservTrace> traces = traceMapper.selectList(Wrappers.<ObservTrace>lambdaQuery()
                .last("LIMIT 5000"));
        for (ObservTrace trace : traces) {
            String day = trace.getCreatedAt() == null ? "unknown"
                    : trace.getCreatedAt().toString().substring(0, 10);
            Map<String, Long> bucket = byDay.computeIfAbsent(day, k -> {
                Map<String, Long> map = new LinkedHashMap<>();
                map.put("total", 0L);
                map.put("ok", 0L);
                return map;
            });
            bucket.put("total", bucket.get("total") + 1);
            if ("ok".equals(trace.getStatus())) {
                bucket.put("ok", bucket.get("ok") + 1);
            }
        }
        List<Map.Entry<String, Map<String, Long>>> entries = new ArrayList<>(byDay.entrySet());
        java.util.Collections.reverse(entries);
        List<Map<String, Object>> daysList = new ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> entry : entries) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", entry.getKey());
            item.put("value", entry.getValue());
            daysList.add(item);
        }
        if (daysList.size() > safeDays) {
            daysList = daysList.subList(0, safeDays);
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map<String, Object> entry : daysList) {
            Map<String, Long> bucket = (Map<String, Long>) entry.get("value");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", entry.get("key"));
            item.put("calls", bucket.get("total"));
            item.put("successRate", percent(bucket.get("ok"), bucket.get("total")));
            trend.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", trend);
        return result;
    }

    /** 按 Agent 统计调用量。 */
    public List<Map<String, Object>> byAgent(int limit) {
        Map<String, long[]> stats = new LinkedHashMap<>();
        for (ObservTrace trace : traceMapper.selectList(Wrappers.<ObservTrace>lambdaQuery()
                .isNotNull(ObservTrace::getAgentName)
                .last("LIMIT 3000"))) {
            String name = trace.getAgentName();
            long[] bucket = stats.computeIfAbsent(name, k -> new long[]{0, 0});
            bucket[0]++;
            if ("ok".equals(trace.getStatus())) {
                bucket[1]++;
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        stats.forEach((name, bucket) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("agentName", name);
            item.put("calls", bucket[0]);
            item.put("successRate", percent(bucket[1], bucket[0]));
            result.add(item);
        });
        result.sort((a, b) -> Long.compare((Long) b.get("calls"), (Long) a.get("calls")));
        return result.subList(0, Math.min(result.size(), Math.max(limit, 1)));
    }

    private double percent(long part, long total) {
        if (total <= 0) {
            return 0;
        }
        return Math.round(part * 10000.0 / total) / 100.0;
    }
}
