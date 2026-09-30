package com.zimo.module.trace.genai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可观测性自检（观测通道的"仪表盘"）。
 *
 * <p><b>解决什么问题</b>：观测埋点是旁路能力，且 {@code TraceCollector} 用
 * {@code catch (Exception ignored)} 包裹每个 observer 回调 —— 这意味着
 * <b>观测通道坏掉时既不报错也不影响业务</b>。本项目就真实发生过：
 * agent-trace 装配齐全、启动日志打印了"registered"，但实际导出 0 条 span，
 * 持续数周无人察觉，直到手工去日志里 grep 才发现。</p>
 *
 * <p>本类提供三项能力，让这种静默失败重新可见：</p>
 * <ol>
 *   <li><b>计数</b>：累计 begin / step / end / export 次数，随时可查；</li>
 *   <li><b>一致性校验</b>：{@link #health()} 对比 begin 与 end 是否配平，
 *       以及 step 是否被成功映射（早期 stepType 词汇错配时 step 有值但 export 为 0）；</li>
 *   <li><b>泄漏检测</b>：报告"已 begin 但未 end"的活跃链路数（嵌套链路未用
 *       {@code endFor} 精确收尾时会持续累积）。</li>
 * </ol>
 *
 * <p>实现为无状态静态计数器（{@code AtomicLong}），不持有业务数据，可安全长期驻留。</p>
 */
public final class GenAiTraceDiagnostics {

    private GenAiTraceDiagnostics() {
    }

    private static final AtomicLong BEGIN_COUNT = new AtomicLong();
    private static final AtomicLong STEP_COUNT = new AtomicLong();
    private static final AtomicLong END_COUNT = new AtomicLong();
    private static final AtomicLong EXPORT_COUNT = new AtomicLong();
    private static final AtomicLong EXPORTED_SPAN_COUNT = new AtomicLong();
    private static final AtomicLong LAST_EXPORT_AT = new AtomicLong();
    private static final AtomicLong DROPPED_STEP_COUNT = new AtomicLong();

    /** 活跃链路（已 begin 未 end）：traceId → begin 时刻。 */
    private static final Map<String, Long> ACTIVE = new java.util.concurrent.ConcurrentHashMap<>();

    /** 记录一次 begin。 */
    static void recordBegin(String traceId) {
        BEGIN_COUNT.incrementAndGet();
        if (traceId != null) {
            ACTIVE.put(traceId, System.currentTimeMillis());
        }
    }

    /** 记录一次 step。 */
    static void recordStep() {
        STEP_COUNT.incrementAndGet();
    }

    /**
     * 记录一次被丢弃的 step。
     *
     * <p>正常情况下不应发生；若计数持续增长，说明存在本 observer 无法识别的
     * stepType 或空 traceId 路径，是词汇错配的早期信号。</p>
     */
    static void recordDroppedStep() {
        DROPPED_STEP_COUNT.incrementAndGet();
    }

    /** 记录一次 end。 */
    static void recordEnd(String traceId) {
        END_COUNT.incrementAndGet();
        if (traceId != null) {
            ACTIVE.remove(traceId);
        }
    }

    /** 记录一次导出。 */
    static void recordExport(int spanCount) {
        EXPORT_COUNT.incrementAndGet();
        EXPORTED_SPAN_COUNT.addAndGet(spanCount);
        LAST_EXPORT_AT.set(System.currentTimeMillis());
    }

    /**
     * 观测通道健康快照。
     *
     * @return 含计数、配平状态、活跃链路数与告警清单的只读 Map
     */
    public static Map<String, Object> health() {
        long begun = BEGIN_COUNT.get();
        long stepped = STEP_COUNT.get();
        long ended = END_COUNT.get();
        long exported = EXPORT_COUNT.get();
        long dropped = DROPPED_STEP_COUNT.get();

        List<String> warnings = new ArrayList<>();
        if (begun > 0 && exported == 0) {
            warnings.add("已开始 " + begun + " 条链路但导出 0 次：导出器未生效或 stepType 词汇错配");
        }
        if (stepped > 0 && exported == 0) {
            warnings.add("已记录 " + stepped + " 个步骤但从未导出：GenAiSpanExporter 可能未装配");
        }
        if (begun > ended) {
            warnings.add("begin/end 不配平（差 " + (begun - ended)
                    + "）：嵌套链路未用 endFor 精确收尾，聚合容器将泄漏");
        }
        if (dropped > 0) {
            warnings.add("有 " + dropped + " 个步骤因类型无法识别被丢弃：请核对 GenAiStepTypes 别名表");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("beginCount", begun);
        result.put("stepCount", stepped);
        result.put("endCount", ended);
        result.put("exportCount", exported);
        result.put("exportedSpanCount", EXPORTED_SPAN_COUNT.get());
        result.put("droppedStepCount", dropped);
        result.put("activeTraceCount", ACTIVE.size());
        result.put("lastExportAt", LAST_EXPORT_AT.get() == 0 ? null : LAST_EXPORT_AT.get());
        result.put("healthy", warnings.isEmpty());
        result.put("warnings", warnings);
        return result;
    }

    /** 活跃（已 begin 未 end）链路的 traceId 快照，按开始时间升序。 */
    public static List<String> activeTraceIds() {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(ACTIVE.entrySet());
        entries.sort(Map.Entry.comparingByValue());
        List<String> ids = new ArrayList<>(entries.size());
        for (Map.Entry<String, Long> e : entries) {
            ids.add(e.getKey());
        }
        return ids;
    }

    /** 重置全部计数（仅供单测使用）。 */
    static void reset() {
        BEGIN_COUNT.set(0);
        STEP_COUNT.set(0);
        END_COUNT.set(0);
        EXPORT_COUNT.set(0);
        EXPORTED_SPAN_COUNT.set(0);
        LAST_EXPORT_AT.set(0);
        DROPPED_STEP_COUNT.set(0);
        ACTIVE.clear();
    }
}
