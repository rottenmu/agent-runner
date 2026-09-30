package com.zimo.module.trace.genai;

/**
 * GenAI span 类型（对应 gen_ai.span.kind）。
 *
 * <p>对齐 OTel GenAI 语义：AGENT=智能体调用 / LLM=模型调用 / TOOL=工具调用 /
 * RETRIEVER=检索 / TASK=任务 / STEP=推理回合。</p>
 */
public enum GenAiSpanKind {

    AGENT("AGENT"),
    LLM("LLM"),
    TOOL("TOOL"),
    RETRIEVER("RETRIEVER"),
    TASK("TASK"),
    STEP("STEP");

    private final String value;

    GenAiSpanKind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * 从观测中心 stepType 映射到 span 类型。
     *
     * <p>⚠️ 实际映射逻辑已收敛到 {@link GenAiStepTypes#toSpanKind(String)} —— 那里维护
     * 三套历史词汇（{@code model_call} / {@code tool} / {@code knowledge_retrieval} …）的
     * 别名表。本方法只做委托，<b>不要再往这里加 case</b>：早期版本只认
     * {@code tool/agent/plan/retriever/rag}，而中间件实际写的是
     * {@code model_call/reasoning/tool_call}，交集只有 {@code agent}，
     * 于是绝大多数步骤被静默归成 TASK，agent-trace 产出为零却不报错。</p>
     *
     * @param stepType 原始步骤类型（任意一套历史词汇）
     * @return span 类型；未知归 {@link #TASK}
     */
    public static GenAiSpanKind fromStepType(String stepType) {
        return GenAiStepTypes.toSpanKind(stepType);
    }
}
