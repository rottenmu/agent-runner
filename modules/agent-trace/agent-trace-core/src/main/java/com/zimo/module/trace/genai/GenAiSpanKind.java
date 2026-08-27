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

    /** 从观测中心 stepType 映射：tool→TOOL、agent→STEP、plan→STEP、其余→TASK。 */
    public static GenAiSpanKind fromStepType(String stepType) {
        if (stepType == null) {
            return TASK;
        }
        switch (stepType) {
            case "tool":
                return TOOL;
            case "agent":
            case "plan":
                return STEP;
            case "retriever":
            case "rag":
                return RETRIEVER;
            default:
                return TASK;
        }
    }
}
