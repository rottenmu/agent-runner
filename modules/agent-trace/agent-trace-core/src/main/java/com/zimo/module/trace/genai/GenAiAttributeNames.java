package com.zimo.module.trace.genai;

/**
 * OpenTelemetry GenAI 语义约定属性名（gen_ai.* 前缀）。
 *
 * <p>参考：https://opentelemetry.io/docs/specs/semconv/gen-ai/
 * 核心属性：operation.name / provider.name / request.model / usage.input_tokens / output_tokens
 * 会话/用户/智能体归属：session.id / user.id / agent.name / conversation.id</p>
 */
public final class GenAiAttributeNames {

    private GenAiAttributeNames() {
    }

    /** 操作类型：chat / text_completion / embeddings / execute_tool / invoke_agent / retrieval。 */
    public static final String OPERATION_NAME = "gen_ai.operation.name";
    /** span 类型（AGENT/LLM/TOOL/RETRIEVER/TASK/STEP）。 */
    public static final String SPAN_KIND = "gen_ai.span.kind";
    /** 提供商：openai / anthropic / dashscope 等（规范曾用 gen_ai.system）。 */
    public static final String PROVIDER_NAME = "gen_ai.provider.name";
    /** 请求时指定的模型。 */
    public static final String REQUEST_MODEL = "gen_ai.request.model";
    /** 实际响应的模型。 */
    public static final String RESPONSE_MODEL = "gen_ai.response.model";
    /** 输入 token 数。 */
    public static final String USAGE_INPUT_TOKENS = "gen_ai.usage.input_tokens";
    /** 输出 token 数。 */
    public static final String USAGE_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";
    /** 结束原因：stop / length / tool_calls / content_filter。 */
    public static final String FINISH_REASONS = "gen_ai.response.finish_reasons";
    /** 会话 ID。 */
    public static final String SESSION_ID = "gen_ai.session.id";
    /** 端用户 ID。 */
    public static final String USER_ID = "gen_ai.user.id";
    /** 智能体名（成本分组）。 */
    public static final String AGENT_NAME = "gen_ai.agent.name";
    /** 对话/会话归属。 */
    public static final String CONVERSATION_ID = "gen_ai.conversation.id";
    /** 意图。 */
    public static final String INTENT = "gen_ai.intent";
    /** 触发类型。 */
    public static final String TRIGGER_TYPE = "gen_ai.trigger_type";
    /** 链路 ID（traceId）。 */
    public static final String TRACE_ID = "gen_ai.trace_id";

    /** 工具名（tool span）。 */
    public static final String TOOL_NAME = "gen_ai.tool.name";
    /** 工具入参。 */
    public static final String TOOL_INPUT = "gen_ai.tool.input";
    /** 工具输出。 */
    public static final String TOOL_OUTPUT = "gen_ai.tool.output";

    /** 提示内容（可选，采样/脱敏后存储）。 */
    public static final String PROMPT = "gen_ai.prompt";
    /** 完成内容（可选）。 */
    public static final String COMPLETION = "gen_ai.completion";
}
