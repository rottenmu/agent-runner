package com.zimo.starter.ai.interop;

import com.zimo.starter.ai.sandbox.SandboxBackend;
import com.zimo.starter.ai.sandbox.SandboxCommand;
import com.zimo.starter.ai.sandbox.SandboxResult;
import com.zimo.starter.ai.skill.AiSkillResult;
import com.zimo.starter.ai.skill.ToolCallContext;
import com.zimo.starter.ai.skill.ToolHook;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AGENTS.md hook → ToolPipeline 桥接器（dsh A8：Claude Code hooks 桥接）。
 *
 * <p>把 {@link AgentHookRegistry} 中的 hook 映射为 {@link ToolHook}：
 * <ul>
 *   <li><b>PreToolUse</b>：工具执行前执行 hook 命令，命令输出含
 *       {@code REJECT[: 原因]} 或 {@code deny} 则拒绝执行（短路）；其余放行。</li>
 *   <li><b>PostToolUse</b>：工具执行后执行 hook 命令，输出含
 *       {@code REWRITE: 内容} 则重写结果；含 {@code REJECT} 则标记失败。</li>
 * </ul>
 * hook 命令经 {@link SandboxBackend#execute(SandboxCommand)} 执行（本地/远程一致），
 * 命令拆分为 shell 词元，超时默认 10s。任一 hook 执行异常按「放行 + 日志」处理，不阻断流水线。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-24
 */
public class AgentHookBridge implements ToolHook {

    private static final Logger log = LoggerFactory.getLogger(AgentHookBridge.class);

    /** JSON 解析器（hook 决策协议）。 */
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** 命令默认超时（秒）。 */
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    private final AgentHookRegistry registry;
    private final SandboxBackend sandboxBackend;

    public AgentHookBridge(AgentHookRegistry registry, SandboxBackend sandboxBackend) {
        this.registry = registry == null ? new AgentHookRegistry() : registry;
        this.sandboxBackend = sandboxBackend;
    }

    @Override
    public Optional<String> pre(ToolCallContext context, String toolName,
                                Map<String, Object> arguments) {
        List<AgentHook> hooks = registry.matching(AgentHook.TRIGGER_PRE_TOOL_USE, toolName);
        if (hooks.isEmpty()) {
            return Optional.empty();
        }
        for (AgentHook hook : hooks) {
            String output = run(hook, toolName, arguments);
            if (output == null) {
                continue;
            }
            String decision = parseDecision(output);
            if (decision.startsWith("REJECT")) {
                String reason = decision.length() > 7 ? decision.substring(7).trim() : "被 AGENTS.md hook 拒绝";
                return Optional.of(reason);
            }
        }
        return Optional.empty();
    }

    @Override
    public AiSkillResult post(ToolCallContext context, String toolName,
                              Map<String, Object> arguments, AiSkillResult result) {
        List<AgentHook> hooks = registry.matching(AgentHook.TRIGGER_POST_TOOL_USE, toolName);
        if (hooks.isEmpty()) {
            return result;
        }
        AiSkillResult current = result;
        for (AgentHook hook : hooks) {
            String output = run(hook, toolName, arguments);
            if (output == null) {
                continue;
            }
            String decision = parseDecision(output);
            if (decision.startsWith("REWRITE")) {
                String rewritten = decision.length() > 8
                        ? decision.substring(8).trim()
                        : "";
                current = AiSkillResult.ok(rewritten.isEmpty() ? current.content() : rewritten);
            } else if (decision.startsWith("REJECT")) {
                current = AiSkillResult.fail("AGENTS.md hook 拒绝结果：" + current.content());
            }
        }
        return current;
    }

    /** 执行 hook 命令并返回 stdout（trim）；执行失败返回 null。 */
    private String run(AgentHook hook, String toolName, Map<String, Object> arguments) {
        if (hook == null || hook.command() == null || hook.command().isBlank()) {
            return null;
        }
        if (sandboxBackend == null) {
            log.warn("AGENTS.md hook 未装配沙箱后端，跳过执行: {}", hook.name());
            return null;
        }
        try {
            SandboxResult result = sandboxBackend.execute(new SandboxCommand(
                    tokenize(hook.command()),
                    Map.of("AGENT_HOOK_TOOL", toolName == null ? "" : toolName),
                    toJson(arguments),
                    DEFAULT_TIMEOUT_SECONDS,
                    null));
            if (result.error() != null) {
                log.warn("AGENTS.md hook 执行错误: {} {}", hook.name(), result.error());
                return null;
            }
            if (!result.succeeded()) {
                log.warn("AGENTS.md hook 非零退出: {} exit={} stderr={}",
                        hook.name(), result.exitCode(), result.stderr());
                return null;
            }
            return result.stdout() == null ? "" : result.stdout().trim();
        } catch (Exception e) {
            log.warn("AGENTS.md hook 执行异常: {} {}", hook.name(), e.getMessage());
            return null;
        }
    }

    /**
     * 从命令输出解析决策（Claude Code JSON 协议 + 纯文本兼容）。
     *
     * <p>支持 JSON：{@code {"decision":"approve"}} / {@code {"decision":"reject","reason":"..."}}
     * / {@code {"decision":"rewrite","updatedInput":"..."}} / {@code {"decision":"ask"}}；
     * 也支持纯文本 {@code REJECT[: 原因]} / {@code REWRITE[: 内容]}。</p>
     */
    private String parseDecision(String output) {
        String line = output;
        int newline = output.indexOf('\n');
        if (newline >= 0) {
            line = output.substring(0, newline);
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return parseJsonDecision(trimmed);
        }
        String upper = trimmed.toUpperCase();
        if (upper.startsWith("REJECT")) {
            return trimmed;
        }
        if (upper.startsWith("REWRITE")) {
            return trimmed;
        }
        if (upper.startsWith("DENY") || upper.equals("NO")) {
            return "REJECT: " + trimmed;
        }
        return trimmed;
    }

    /** 解析 Claude Code JSON 决策输出。 */
    private String parseJsonDecision(String json) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = JSON.readTree(json);
            String decision = node.path("decision").asText("approve").trim();
            switch (decision) {
                case "reject" -> {
                    String reason = node.path("reason").asText("被 AGENTS.md hook 拒绝");
                    return "REJECT: " + reason;
                }
                case "rewrite" -> {
                    String updated = node.path("updatedInput").asText("");
                    return "REWRITE: " + updated;
                }
                case "ask" -> {
                    String reason = node.path("reason").asText("需要人工确认");
                    return "REJECT: " + reason + "（ask，需人工确认）";
                }
                default -> {
                    return "APPROVE";
                }
            }
        } catch (Exception e) {
            log.warn("AGENTS.md hook JSON 决策解析失败，按放行处理: {}", e.getMessage());
            return "APPROVE";
        }
    }

    /** 工具入参序列化为 stdin JSON（hook 可读取）。 */
    private String toJson(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(arguments);
        } catch (Exception e) {
            return String.valueOf(arguments);
        }
    }

    /** 简易 shell 词元切分（引号保留空格）。 */
    private List<String> tokenize(String command) {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (inQuote) {
                if (c == quoteChar) {
                    inQuote = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                inQuote = true;
                quoteChar = c;
            } else if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens.isEmpty() ? List.of("true") : tokens;
    }
}