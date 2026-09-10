package com.zimo.framework.ai.interop;

import com.zimo.framework.ai.skill.AiSkill;
import com.zimo.framework.ai.skill.AiSkillResult;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 互操作规则内存技能：把 AGENTS.md / CLAUDE.md 规则作为可调用技能暴露（dsh A8）。
 *
 * <p>技能名固定 {@code interop_instructions}，无参数或 {@code topic} 过滤。启用时
 * agent 可在需要时检索仓库规则，无需把规则常驻系统提示词。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-24
 */
public class InstructionFileSkill implements AiSkill {

    public static final String NAME = "interop_instructions";

    private final AgentInteropService interopService;
    private final int maxChars;

    public InstructionFileSkill(AgentInteropService interopService, int maxChars) {
        this.interopService = interopService == null
                ? new AgentInteropService(null)
                : interopService;
        this.maxChars = maxChars > 0 ? maxChars : 8000;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "读取仓库互操作规则文件（AGENTS.md / CLAUDE.md）内容，供开发任务遵循约定。"
                + "参数 topic（可选）：按关键字过滤规则行。";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public AiSkillResult call(Map<String, Object> arguments) {
        String block = interopService.instructionBlock(maxChars);
        if (block.isBlank()) {
            return AiSkillResult.ok("未发现互操作规则文件（AGENTS.md / CLAUDE.md）。");
        }
        Object topicValue = arguments == null ? null : arguments.get("topic");
        if (topicValue instanceof String topic && !topic.isBlank()) {
            Pattern pattern = Pattern.compile(
                    Pattern.quote(topic.trim()), Pattern.CASE_INSENSITIVE);
            String filtered = block.lines()
                    .filter(line -> pattern.matcher(line).find())
                    .collect(java.util.stream.Collectors.joining("\n"));
            if (filtered.isBlank()) {
                return AiSkillResult.ok("规则文件中未找到与「" + topic + "」相关的内容。");
            }
            return AiSkillResult.ok(filtered);
        }
        return AiSkillResult.ok(block);
    }

    @Override
    public List<String> inputParameters() {
        return List.of("topic");
    }
}