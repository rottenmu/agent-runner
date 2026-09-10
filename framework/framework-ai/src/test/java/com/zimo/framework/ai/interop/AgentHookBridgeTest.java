package com.zimo.framework.ai.interop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zimo.framework.ai.sandbox.SandboxBackend;
import com.zimo.framework.ai.sandbox.SandboxCommand;
import com.zimo.framework.ai.sandbox.SandboxResult;
import com.zimo.framework.ai.skill.AiSkillResult;
import com.zimo.framework.ai.skill.ToolCallContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * AGENTS.md hook 解析/注册/桥接单测（dsh A8：Claude Code hooks 桥接）。
 */
class AgentHookBridgeTest {

    /* ---------------- AgentHookParser ---------------- */

    @Test
    void parsesClaudeCodeStyleHookDirective() {
        AgentHookParser parser = new AgentHookParser();
        AgentHook hook = parser.parseLine("hook: PreToolUse name=code-review command=review-tool matcher=*");
        assertThat(hook).isNotNull();
        assertThat(hook.trigger()).isEqualTo("PreToolUse");
        assertThat(hook.name()).isEqualTo("code-review");
        assertThat(hook.command()).isEqualTo("review-tool");
        assertThat(hook.matcher()).isEqualTo("*");
        assertThat(hook.isPreToolUse()).isTrue();
    }

    @Test
    void parsesPostToolUseWithQuotedCommandAndMatcher() {
        AgentHookParser parser = new AgentHookParser();
        AgentHook hook = parser.parseLine(
                "hook: PostToolUse name=summarize command=\"echo 'done'\" matcher=echo");
        assertThat(hook).isNotNull();
        assertThat(hook.trigger()).isEqualTo("PostToolUse");
        assertThat(hook.isPostToolUse()).isTrue();
        assertThat(hook.command()).isEqualTo("echo 'done'");
        assertThat(hook.matcher()).isEqualTo("echo");
    }

    @Test
    void skipsNonHookLinesAndMissingCommand() {
        AgentHookParser parser = new AgentHookParser();
        assertThat(parser.parseLine("# 普通规则")).isNull();
        assertThat(parser.parseLine("hook: PreToolUse name=no-command")).isNull();
        assertThat(parser.parseLine(null)).isNull();
        assertThat(parser.parseLine("hook: UnknownTrigger name=x command=cmd")).isNotNull();
    }

    @Test
    void parsesMultipleHooksFromLines() {
        AgentHookParser parser = new AgentHookParser();
        List<AgentHook> hooks = parser.parse(List.of(
                "hook: PreToolUse name=a command=cmd-a matcher=*",
                "hook: PostToolUse name=b command=cmd-b matcher=echo",
                "普通规则行"));
        assertThat(hooks).hasSize(2);
        assertThat(hooks.get(0).name()).isEqualTo("a");
        assertThat(hooks.get(1).name()).isEqualTo("b");
    }

    /* ---------------- AgentHookRegistry ---------------- */

    @Test
    void registersAndMatchesByTriggerAndTool() {
        AgentHookRegistry registry = new AgentHookRegistry();
        registry.register(new AgentHook("PreToolUse", "review", "cmd", "echo"));
        registry.register(new AgentHook("PreToolUse", "all", "cmd2", "*"));
        registry.register(new AgentHook("PostToolUse", "rewrite", "cmd3", "summarize"));

        assertThat(registry.matching("PreToolUse", "echo")).hasSize(2);
        assertThat(registry.matching("PreToolUse", "unknown")).hasSize(1);
        assertThat(registry.matching("PostToolUse", "summarize")).hasSize(1);
        assertThat(registry.all("PreToolUse")).hasSize(2);
        assertThat(registry.size()).isEqualTo(3);
    }

    @Test
    void sameNameInSameTriggerReplaces() {
        AgentHookRegistry registry = new AgentHookRegistry();
        registry.register(new AgentHook("PreToolUse", "h", "old", "*"));
        registry.register(new AgentHook("PreToolUse", "h", "new", "*"));
        assertThat(registry.all("PreToolUse")).hasSize(1);
        assertThat(registry.all("PreToolUse").get(0).command()).isEqualTo("new");
    }

    /* ---------------- AgentHookBridge ---------------- */

    @Test
    void preRejectsWhenHookOutputsReject() {
        SandboxBackend sandbox = mock(SandboxBackend.class);
        when(sandbox.execute(any(SandboxCommand.class)))
                .thenReturn(SandboxResult.ok(0, "REJECT: 敏感操作", ""));
        AgentHookRegistry registry = new AgentHookRegistry();
        registry.register(new AgentHook("PreToolUse", "guard", "guard-cmd", "rm"));
        AgentHookBridge bridge = new AgentHookBridge(registry, sandbox);

        Optional<String> denied = bridge.pre(
                ToolCallContext.of("t1"), "rm", Map.of("path", "/tmp"));
        assertThat(denied).isPresent();
        assertThat(denied.get()).contains("敏感操作");
    }

    @Test
    void preAllowsWhenHookOutputsApprove() {
        SandboxBackend sandbox = mock(SandboxBackend.class);
        when(sandbox.execute(any(SandboxCommand.class)))
                .thenReturn(SandboxResult.ok(0, "approve", ""));
        AgentHookRegistry registry = new AgentHookRegistry();
        registry.register(new AgentHook("PreToolUse", "guard", "guard-cmd", "*"));
        AgentHookBridge bridge = new AgentHookBridge(registry, sandbox);

        assertThat(bridge.pre(ToolCallContext.of("t1"), "echo", Map.of()))
                .isEmpty();
    }

    @Test
    void postRewritesResultWhenHookOutputsRewrite() {
        SandboxBackend sandbox = mock(SandboxBackend.class);
        when(sandbox.execute(any(SandboxCommand.class)))
                .thenReturn(SandboxResult.ok(0, "REWRITE: 已脱敏", ""));
        AgentHookRegistry registry = new AgentHookRegistry();
        registry.register(new AgentHook("PostToolUse", "mask", "mask-cmd", "*"));
        AgentHookBridge bridge = new AgentHookBridge(registry, sandbox);

        AiSkillResult result = bridge.post(ToolCallContext.of("t1"), "echo",
                Map.of(), AiSkillResult.ok("原始内容"));
        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("已脱敏");
    }

    @Test
    void bridgeSkipsExecutionWhenBackendUnavailableOrFails() {
        AgentHookRegistry registry = new AgentHookRegistry();
        registry.register(new AgentHook("PreToolUse", "guard", "guard-cmd", "*"));
        // 无沙箱后端：跳过执行放行
        AgentHookBridge noSandbox = new AgentHookBridge(registry, null);
        assertThat(noSandbox.pre(ToolCallContext.of("t1"), "echo", Map.of())).isEmpty();

        // 后端返回错误：放行
        SandboxBackend failing = mock(SandboxBackend.class);
        when(failing.execute(any(SandboxCommand.class)))
                .thenReturn(SandboxResult.failed("远程沙箱不可达"));
        AgentHookBridge bridge = new AgentHookBridge(registry, failing);
        assertThat(bridge.pre(ToolCallContext.of("t1"), "echo", Map.of())).isEmpty();
    }

    @Test
    void bridgeIgnoresNonMatchingHook() {
        SandboxBackend sandbox = mock(SandboxBackend.class);
        AgentHookRegistry registry = new AgentHookRegistry();
        registry.register(new AgentHook("PreToolUse", "guard", "guard-cmd", "rm"));
        AgentHookBridge bridge = new AgentHookBridge(registry, sandbox);
        // matcher=rm，调用 echo → 不匹配 → 不执行沙箱
        assertThat(bridge.pre(ToolCallContext.of("t1"), "echo", Map.of())).isEmpty();
    }

    /* ---------------- Stage2：Claude Code JSON 决策协议 ---------------- */

    @Test
    void preRejectsOnJsonDecision() {
        SandboxBackend sandbox = mock(SandboxBackend.class);
        when(sandbox.execute(any(SandboxCommand.class)))
                .thenReturn(SandboxResult.ok(0,
                        "{\"decision\":\"reject\",\"reason\":\"协议不允许删除\"}", ""));
        AgentHookRegistry registry = new AgentHookRegistry();
        registry.register(new AgentHook("PreToolUse", "guard", "guard-cmd", "*"));
        AgentHookBridge bridge = new AgentHookBridge(registry, sandbox);

        Optional<String> denied = bridge.pre(ToolCallContext.of("t1"), "rm", Map.of());
        assertThat(denied).isPresent();
        assertThat(denied.get()).contains("协议不允许删除");
    }

    @Test
    void preAllowsOnJsonApprove() {
        SandboxBackend sandbox = mock(SandboxBackend.class);
        when(sandbox.execute(any(SandboxCommand.class)))
                .thenReturn(SandboxResult.ok(0, "{\"decision\":\"approve\"}", ""));
        AgentHookRegistry registry = new AgentHookRegistry();
        registry.register(new AgentHook("PreToolUse", "guard", "guard-cmd", "*"));
        AgentHookBridge bridge = new AgentHookBridge(registry, sandbox);

        assertThat(bridge.pre(ToolCallContext.of("t1"), "rm", Map.of())).isEmpty();
    }

    @Test
    void preRejectsOnJsonAsk() {
        SandboxBackend sandbox = mock(SandboxBackend.class);
        when(sandbox.execute(any(SandboxCommand.class)))
                .thenReturn(SandboxResult.ok(0,
                        "{\"decision\":\"ask\",\"reason\":\"高危操作\"}", ""));
        AgentHookRegistry registry = new AgentHookRegistry();
        registry.register(new AgentHook("PreToolUse", "guard", "guard-cmd", "*"));
        AgentHookBridge bridge = new AgentHookBridge(registry, sandbox);

        Optional<String> denied = bridge.pre(ToolCallContext.of("t1"), "rm", Map.of());
        assertThat(denied).isPresent();
        assertThat(denied.get()).contains("高危操作");
    }

    @Test
    void postRewritesOnJsonUpdatedInput() {
        SandboxBackend sandbox = mock(SandboxBackend.class);
        when(sandbox.execute(any(SandboxCommand.class)))
                .thenReturn(SandboxResult.ok(0,
                        "{\"decision\":\"rewrite\",\"updatedInput\":\"脱敏后内容\"}", ""));
        AgentHookRegistry registry = new AgentHookRegistry();
        registry.register(new AgentHook("PostToolUse", "mask", "mask-cmd", "*"));
        AgentHookBridge bridge = new AgentHookBridge(registry, sandbox);

        AiSkillResult result = bridge.post(ToolCallContext.of("t1"), "echo",
                Map.of(), AiSkillResult.ok("原始"));
        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("脱敏后内容");
    }

    @Test
    void stdinCarriesToolArgumentsJson() {
        SandboxBackend sandbox = mock(SandboxBackend.class);
        when(sandbox.execute(any(SandboxCommand.class))).thenAnswer(invocation -> {
            SandboxCommand command = invocation.getArgument(0);
            assertThat(command.stdin()).contains("path").contains("/tmp/x");
            return SandboxResult.ok(0, "approve", "");
        });
        AgentHookRegistry registry = new AgentHookRegistry();
        registry.register(new AgentHook("PreToolUse", "guard", "guard-cmd", "*"));
        AgentHookBridge bridge = new AgentHookBridge(registry, sandbox);

        assertThat(bridge.pre(ToolCallContext.of("t1"), "rm",
                Map.of("path", "/tmp/x"))).isEmpty();
    }
}