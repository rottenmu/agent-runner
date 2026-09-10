package com.zimo.framework.ai.skill;

import java.util.Map;
import java.util.Optional;

/**
 * 工具流水线钩子（对齐 DeepSeek Harness tool pipeline pre/post hooks）。
 *
 * <p>{@link #pre} 在工具执行前调用：返回非空拒绝原因则短路（不执行工具）；
 * {@link #post} 在工具执行后调用：可重写返回结果（脱敏/格式化/注入上下文）。</p>
 */
public interface ToolHook {

    /** 执行前钩子：返回非空 = 拒绝执行（原因）。默认放行。 */
    default Optional<String> pre(ToolCallContext context, String toolName,
                                 Map<String, Object> arguments) {
        return Optional.empty();
    }

    /** 执行后钩子：可重写结果（默认原样返回）。 */
    default AiSkillResult post(ToolCallContext context, String toolName,
                               Map<String, Object> arguments, AiSkillResult result) {
        return result;
    }
}
