package com.zimo.framework.ai.interop;

/**
 * AGENTS.md hook 指令模型（dsh A8：Claude Code hooks 桥接）。
 *
 * <p>对应 Claude Code hook 指令格式（AGENTS.md 中形如
 * {@code hook: PreToolUse name=code-review command=claude-code-review}）：</p>
 * <ul>
 *   <li>{@code trigger} 触发点：PreToolUse（执行前可拒绝）/ PostToolUse（执行后可重写）/ Notification（通知）</li>
 *   <li>{@code name} 钩子名（同触发点下唯一，可空回退触发点+序号）</li>
 *   <li>{@code command} 钩子命令（经沙箱执行，可空表示仅声明不执行）</li>
 *   <li>{@code matcher} 工具名匹配（空=全部工具；支持 {@code *} 前缀/后缀通配）</li>
 * </ul>
 *
 * @param trigger 触发点
 * @param name    钩子名
 * @param command 钩子命令
 * @param matcher 工具名匹配（空=全部）
 */
public record AgentHook(
        String trigger,
        String name,
        String command,
        String matcher) {

    public static final String TRIGGER_PRE_TOOL_USE = "PreToolUse";
    public static final String TRIGGER_POST_TOOL_USE = "PostToolUse";
    public static final String TRIGGER_NOTIFICATION = "Notification";

    public AgentHook {
        trigger = trigger == null ? TRIGGER_PRE_TOOL_USE : trigger;
        name = name == null || name.isBlank() ? trigger : name.trim();
        command = command == null ? "" : command.trim();
        matcher = matcher == null ? "" : matcher.trim();
    }

    public boolean isPreToolUse() {
        return TRIGGER_PRE_TOOL_USE.equals(trigger);
    }

    public boolean isPostToolUse() {
        return TRIGGER_POST_TOOL_USE.equals(trigger);
    }

    /** 判断是否匹配指定工具名（空 matcher 或通配匹配）。 */
    public boolean matches(String toolName) {
        if (toolName == null || matcher.isEmpty() || "*".equals(matcher)) {
            return true;
        }
        if (matcher.startsWith("*")) {
            return toolName.endsWith(matcher.substring(1));
        }
        if (matcher.endsWith("*")) {
            return toolName.startsWith(matcher.substring(0, matcher.length() - 1));
        }
        return matcher.equals(toolName);
    }
}