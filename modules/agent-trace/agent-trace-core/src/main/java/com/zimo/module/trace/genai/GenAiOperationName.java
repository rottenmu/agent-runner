package com.zimo.module.trace.genai;

/**
 * GenAI 操作名（gen_ai.operation.name）常量。
 */
public final class GenAiOperationName {

    private GenAiOperationName() {
    }

    public static final String INVOKE_AGENT = "invoke_agent";
    public static final String CHAT = "chat";
    public static final String TEXT_COMPLETION = "text_completion";
    public static final String EMBEDDINGS = "embeddings";
    public static final String EXECUTE_TOOL = "execute_tool";
    public static final String RETRIEVAL = "retrieval";

    /** 从 span kind 推导默认操作名。 */
    public static String fromKind(GenAiSpanKind kind) {
        if (kind == null) {
            return "task";
        }
        switch (kind) {
            case LLM:
                return CHAT;
            case TOOL:
                return EXECUTE_TOOL;
            case RETRIEVER:
                return RETRIEVAL;
            case AGENT:
                return INVOKE_AGENT;
            default:
                return "task";
        }
    }
}
