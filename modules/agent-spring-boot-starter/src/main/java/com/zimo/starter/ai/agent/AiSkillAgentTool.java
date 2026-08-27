package com.zimo.starter.ai.agent;

import com.zimo.starter.ai.skill.AiSkillDescriptor;
import com.zimo.starter.ai.skill.AiSkillRegistry;
import com.zimo.starter.ai.skill.AiSkillResult;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * 将 starter 管理技能适配为 AgentScope 可调用工具。
 *
 * <p>模型参数以开放 JSON 对象传给 {@link AiSkillRegistry}；技能自身继续负责业务参数校验。
 * 只读属性会传递给 AgentScope 权限引擎，失败结果转换为工具错误而不是中断智能体循环。</p>
 *
 * @author Codex
 * @since 2026-07-27
 */
final class AiSkillAgentTool extends ToolBase {

    private static final Map<String, Object> OPEN_OBJECT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(),
            "additionalProperties", true);

    private final AiSkillRegistry registry;
    /** 工具执行钩子（可空）：onPreExecute 可拒绝，onPostExecute/onError 审计。 */
    private final ToolExecutionListener listener;

    AiSkillAgentTool(
            AiSkillDescriptor descriptor,
            AiSkillRegistry registry) {
        this(descriptor, registry, null);
    }

    AiSkillAgentTool(
            AiSkillDescriptor descriptor,
            AiSkillRegistry registry,
            ToolExecutionListener listener) {
        super(ToolBase.builder()
                .name(Objects.requireNonNull(descriptor, "descriptor must not be null").name())
                .description(descriptor.description())
                .inputSchema(OPEN_OBJECT_SCHEMA)
                .readOnly(descriptor.readOnly())
                .concurrencySafe(false));
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.listener = listener;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        Map<String, Object> arguments = param == null ? Map.of() : param.getInput();
        String toolName = getName();
        if (listener != null && !listener.onPreExecute(toolName, arguments)) {
            return Mono.just(ToolResultBlock.error("工具被策略拒绝：" + toolName));
        }
        return Mono.fromSupplier(() -> registry.call(toolName, arguments))
                .map(result -> {
                    if (listener != null) {
                        listener.onPostExecute(toolName, arguments, result);
                    }
                    return toToolResult(result);
                })
                .onErrorResume(exception -> {
                    if (listener != null) {
                        listener.onError(toolName, arguments, exception);
                    }
                    return Mono.just(ToolResultBlock.error(
                            "技能调用失败：" + safeMessage(exception)));
                });
    }

    private ToolResultBlock toToolResult(AiSkillResult result) {
        if (result == null) {
            return ToolResultBlock.error("技能未返回结果");
        }
        return result.success()
                ? ToolResultBlock.text(result.content())
                : ToolResultBlock.error(result.content());
    }

    private String safeMessage(Throwable exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
