package com.zimo.framework.ai.interop;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AGENTS.md hook 指令解析器（dsh A8：Claude Code hooks 桥接）。
 *
 * <p>从规则文件指令行中提取 {@code hook:} 前缀行并解析为 {@link AgentHook}。
 * 支持语法（Claude Code 风格）：
 * <blockquote><pre>
 * hook: PreToolUse name=code-review command=review-tool matcher=*
 * hook: PostToolUse name=summarize command=summarizer matcher=echo
 * </pre></blockquote>
 * 解析规则：{@code hook:} 前缀（大小写不敏感）后跟触发点 token，其余为
 * {@code key=value} 键值对（引号可包含空格）。无法解析的行静默跳过。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-24
 */
public class AgentHookParser {

    private static final Pattern HOOK_LINE = Pattern.compile(
            "^\\s*hook\\s*:\\s*(\\S+)\\s*(.*)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern KEY_VALUE = Pattern.compile(
            "(\\w+)=(\"[^\"]*\"|'[^']*'|\\S+)");

    /** 从规则指令行中解析全部 hook。 */
    public List<AgentHook> parse(List<String> lines) {
        List<AgentHook> hooks = new ArrayList<>();
        if (lines == null) {
            return hooks;
        }
        for (String line : lines) {
            AgentHook hook = parseLine(line);
            if (hook != null) {
                hooks.add(hook);
            }
        }
        return hooks;
    }

    /** 从单个指令行解析 hook；非 hook 行或非法返回 null。 */
    public AgentHook parseLine(String line) {
        if (line == null) {
            return null;
        }
        Matcher matcher = HOOK_LINE.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        String trigger = matcher.group(1);
        String attrs = matcher.group(2) == null ? "" : matcher.group(2);
        String name = null;
        String command = null;
        String toolMatcher = null;
        Matcher kv = KEY_VALUE.matcher(attrs);
        while (kv.find()) {
            String key = kv.group(1);
            String value = unquote(kv.group(2));
            switch (key) {
                case "name" -> name = value;
                case "command" -> command = value;
                case "matcher" -> toolMatcher = value;
                default -> { /* 忽略未知属性 */ }
            }
        }
        if (command == null) {
            return null;
        }
        return new AgentHook(trigger, name, command, toolMatcher);
    }

    private static String unquote(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}