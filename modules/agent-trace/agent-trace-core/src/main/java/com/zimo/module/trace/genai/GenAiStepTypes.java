package com.zimo.module.trace.genai;

import java.util.Map;

/**
 * 步骤类型词汇表（观测中心的统一口径）。
 *
 * <p><b>为什么需要这个类</b>：项目里历史上有 <b>三套互不认识的 stepType 词汇</b>，
 * 导致 agent-trace 注册成功却一条数据都导不出来：</p>
 *
 * <table border="1">
 *   <caption>三套词汇的历史来源</caption>
 *   <tr><th>来源</th><th>使用的词</th></tr>
 *   <tr><td>{@code HarnessTraceMiddleware} 实际写入</td>
 *       <td>{@code intent} / {@code agent} / {@code model_call} / {@code reasoning} / {@code tool_call}</td></tr>
 *   <tr><td>{@code GenAiSpanKind.fromStepType} 早期只认</td>
 *       <td>{@code tool} / {@code agent} / {@code plan} / {@code retriever} / {@code rag}</td></tr>
 *   <tr><td>{@code TraceObserver.onStep} 的 Javadoc 声称</td>
 *       <td>{@code intent} / {@code knowledge_retrieval} / {@code tool_call} / {@code prompt} / {@code generation}</td></tr>
 * </table>
 *
 * <p>三者交集只有 {@code agent} 一个，于是 {@code model_call} / {@code reasoning} /
 * {@code tool_call} 全部落进 {@code default} 分支被静默归成 TASK，而
 * {@code GenAiTraceObserver} 又把整批 span 一起导出 —— 表面"注册成功"，实际产出为零，
 * 且因为 {@code TraceCollector} 用 {@code catch (Exception ignored)} 包裹回调，
 * <b>不工作也不报错</b>。</p>
 *
 * <p>本类把三套词汇收敛为一份<b>带别名表</b>的规范化映射：写入方无论用哪一套词，
 * 都能归一到同一个规范词。新增步骤类型时<b>只改这里</b>，不要再去改
 * {@code GenAiSpanKind} 的 switch。</p>
 */
public final class GenAiStepTypes {

    private GenAiStepTypes() {
    }

    /** 意图路由（链路入口，发生在智能体执行之前）。 */
    public static final String INTENT = "intent";
    /** 智能体执行整体（最外层容器步骤）。 */
    public static final String AGENT = "agent";
    /** 大模型调用。 */
    public static final String MODEL_CALL = "model_call";
    /** 推理轮次（ReAct 的一轮思考）。 */
    public static final String REASONING = "reasoning";
    /** 工具调用。 */
    public static final String TOOL_CALL = "tool_call";
    /** 知识检索。 */
    public static final String RETRIEVAL = "retrieval";
    /** 最终回复生成。 */
    public static final String GENERATION = "generation";
    /**
     * 记忆操作（写入 / 更新 / 删除 / 抽取 / 合并 / 淘汰）。
     *
     * <p><b>为什么不复用 {@link #RETRIEVAL}</b>：检索语义只覆盖「读」，把写入/淘汰塞进
     * {@code retrieval} 会让「召回命中率」这类指标被写操作污染。记忆的<b>召回</b>仍走
     * {@link #RETRIEVAL}（自动映射 {@code RETRIEVER} span，前端零改动即可显示），
     * 其余动作走本词并映射 {@code STEP}，由属性 {@code memory.operation} 区分具体动作。</p>
     *
     * <p>降级安全性：即便消费方不认识本词，{@link #normalize(String)} 也不会抛异常，
     * span 仍会以 {@code STEP} 正常显示（不会崩、不会丢）。</p>
     */
    public static final String MEMORY = "memory";
    /** 无法识别的兜底类型。 */
    public static final String UNKNOWN = "unknown";

    /**
     * 别名 → 规范词。
     *
     * <p>键统一小写；查询前会做 trim + lowercase + 连字符转下划线。</p>
     */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            // 规范词自身（幂等）
            Map.entry(INTENT, INTENT),
            Map.entry(AGENT, AGENT),
            Map.entry(MODEL_CALL, MODEL_CALL),
            Map.entry(REASONING, REASONING),
            Map.entry(TOOL_CALL, TOOL_CALL),
            Map.entry(RETRIEVAL, RETRIEVAL),
            Map.entry(GENERATION, GENERATION),
            Map.entry(MEMORY, MEMORY),
            // GenAiSpanKind 早期词汇
            Map.entry("tool", TOOL_CALL),
            Map.entry("plan", REASONING),
            Map.entry("task", REASONING),
            Map.entry("rag", RETRIEVAL),
            Map.entry("retriever", RETRIEVAL),
            Map.entry("knowledge_retrieval", RETRIEVAL),
            // TraceObserver Javadoc 声称的词汇
            Map.entry("prompt", MODEL_CALL),
            Map.entry("completion", GENERATION),
            Map.entry("llm", MODEL_CALL),
            Map.entry("llm_call", MODEL_CALL),
            Map.entry("chat", MODEL_CALL),
            Map.entry("model", MODEL_CALL),
            // 其它可能写法
            Map.entry("thinking", REASONING),
            Map.entry("thought", REASONING),
            Map.entry("invoke_agent", AGENT),
            Map.entry("agent_reply", GENERATION),
            Map.entry("generation_step", GENERATION),
            // 记忆动作：写入类归 memory，检索类归 retrieval（保持「读」「写」两类语义不混）
            Map.entry("memory_write", MEMORY),
            Map.entry("memory_update", MEMORY),
            Map.entry("memory_delete", MEMORY),
            Map.entry("memory_extract", MEMORY),
            Map.entry("memory_merge", MEMORY),
            Map.entry("memory_evict", MEMORY),
            Map.entry("memory_op", MEMORY),
            Map.entry("memory_recall", RETRIEVAL),
            Map.entry("memory_search", RETRIEVAL));

    /**
     * 归一化步骤类型：三套词汇 → 规范词。
     *
     * @param stepType 原始步骤类型（任意一套词汇，可为 null）
     * @return 规范词；无法识别时返回 {@link #UNKNOWN}（不抛异常，保证观测链路不反噬主流程）
     */
    public static String normalize(String stepType) {
        if (stepType == null) {
            return UNKNOWN;
        }
        String key = stepType.trim().toLowerCase().replace('-', '_');
        return ALIASES.getOrDefault(key, UNKNOWN);
    }

    /**
     * 映射到 GenAI span 类型。
     *
     * <p>这是规范词到 {@link GenAiSpanKind} 的<b>唯一映射点</b>，
     * {@link GenAiSpanKind#fromStepType(String)} 委托到本方法。</p>
     *
     * @param stepType 原始步骤类型（任意一套词汇）
     * @return span 类型，未知类型归 {@link GenAiSpanKind#TASK}
     */
    public static GenAiSpanKind toSpanKind(String stepType) {
        switch (normalize(stepType)) {
            case INTENT:
                return GenAiSpanKind.TASK;
            case AGENT:
                return GenAiSpanKind.STEP;
            case MODEL_CALL:
                return GenAiSpanKind.LLM;
            case REASONING:
                return GenAiSpanKind.STEP;
            case TOOL_CALL:
                return GenAiSpanKind.TOOL;
            case RETRIEVAL:
                return GenAiSpanKind.RETRIEVER;
            case GENERATION:
                return GenAiSpanKind.AGENT;
            case MEMORY:
                return GenAiSpanKind.STEP;
            default:
                return GenAiSpanKind.TASK;
        }
    }

    /** 当前的规范词集合（自检接口与前端过滤下拉用，避免消费方各自硬编码一份）。 */
    public static java.util.Set<String> canonicalTypes() {
        return java.util.Set.of(INTENT, AGENT, MODEL_CALL, REASONING, TOOL_CALL,
                RETRIEVAL, GENERATION, MEMORY, UNKNOWN);
    }

    /**
     * 是否为容器型步骤（其 span 语义上应排在子步骤之后被收尾）。
     *
     * <p>容器型步骤由 {@code HarnessTraceMiddleware} 在 {@code doOnComplete} 里写入，
     * 完成时刻最晚，因此 seq 最大 —— 消费方不能按 seq 顺序推断父子关系。</p>
     *
     * @param stepType 原始步骤类型
     * @return true 表示容器型（agent / generation）
     */
    public static boolean isContainer(String stepType) {
        String kind = normalize(stepType);
        return AGENT.equals(kind) || GENERATION.equals(kind);
    }

    /**
     * 是否为链路入口步骤（发生在智能体执行之前）。
     *
     * @param stepType 原始步骤类型
     * @return true 表示入口型（intent）
     */
    public static boolean isEntry(String stepType) {
        return INTENT.equals(normalize(stepType));
    }

    /**
     * 给 span 起一个符合 GenAI 语义约定的 span 名。
     *
     * @param stepType 原始步骤类型
     * @param rawName  步骤原始名（可为 null）
     * @return 规范 span 名，如 {@code execute_tool query_datasource} / {@code chat DashScopeChatModel}
     */
    public static String spanName(String stepType, String rawName) {
        String name = (rawName == null || rawName.isBlank()) ? normalize(stepType) : rawName.trim();
        switch (toSpanKind(stepType)) {
            case TOOL:
                return GenAiOperationName.EXECUTE_TOOL + " " + name;
            case LLM:
                return GenAiOperationName.CHAT + " " + name;
            case RETRIEVER:
                return GenAiOperationName.RETRIEVAL + " " + name;
            case AGENT:
                return GenAiOperationName.INVOKE_AGENT + " " + name;
            default:
                return name;
        }
    }
}
