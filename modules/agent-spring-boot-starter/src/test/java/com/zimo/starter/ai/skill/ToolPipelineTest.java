package com.zimo.starter.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * ToolPipeline 单元测试：pre hook / guard / HITL 审批 / 重试 / post hook / 打点短路。
 */
class ToolPipelineTest {

    private final ToolCallContext context = ToolCallContext.of("trace-1");

    @Test
    void emptyPipelineExecutesDirectly() {
        AiSkillResult result = ToolPipeline.empty().execute(
                context, "echo", Map.of("text", "hi"),
                args -> AiSkillResult.ok("hi"));

        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("hi");
    }

    @Test
    void preHookCanDenyExecution() {
        ToolHook deny = new ToolHook() {
            @Override
            public Optional<String> pre(ToolCallContext ctx, String toolName, Map<String, Object> arguments) {
                return Optional.of("not allowed now");
            }
        };
        ToolPipeline pipeline = new ToolPipeline(List.of(deny), List.of(), null, 0);

        AiSkillResult result = pipeline.execute(context, "echo", Map.of(), args -> AiSkillResult.ok("never"));

        assertThat(result.success()).isFalse();
        assertThat(result.content()).contains("pre-hook 拒绝");
        assertThat(result.content()).contains("not allowed now");
    }

    @Test
    void guardCanBlockExecution() {
        ToolGuard guard = new ToolGuard() {
            @Override
            public Optional<String> check(ToolCallContext ctx, String toolName, Map<String, Object> arguments) {
                return Optional.of("rate limited");
            }
        };
        ToolPipeline pipeline = new ToolPipeline(List.of(), List.of(guard), null, 0);

        AiSkillResult result = pipeline.execute(context, "echo", Map.of(), args -> AiSkillResult.ok("never"));

        assertThat(result.success()).isFalse();
        assertThat(result.content()).contains("守卫拦截");
        assertThat(result.content()).contains("rate limited");
    }

    @Test
    void approvalRejectedBlocksExecution() {
        ToolApprovalHandler handler = new ToolApprovalHandler() {
            @Override
            public Status approve(ToolCallContext ctx, String toolName, Map<String, Object> arguments) {
                return Status.REJECTED;
            }
        };
        ToolPipeline pipeline = new ToolPipeline(List.of(), List.of(), handler, 0);

        AiSkillResult result = pipeline.execute(context, "echo", Map.of(), args -> AiSkillResult.ok("never"));

        assertThat(result.success()).isFalse();
        assertThat(result.content()).contains("未通过审批");
    }

    @Test
    void approvalPendingDenyOnPendingBlocksExecution() {
        ToolApprovalHandler handler = new ToolApprovalHandler() {
            @Override
            public Status approve(ToolCallContext ctx, String toolName, Map<String, Object> arguments) {
                return Status.PENDING;
            }

            @Override
            public boolean denyOnPending() {
                return true;
            }
        };
        ToolPipeline pipeline = new ToolPipeline(List.of(), List.of(), handler, 0);

        AiSkillResult result = pipeline.execute(context, "echo", Map.of(), args -> AiSkillResult.ok("never"));

        assertThat(result.success()).isFalse();
        assertThat(result.content()).contains("未通过审批");
    }

    @Test
    void approvalApprovedExecutes() {
        ToolApprovalHandler handler = new ToolApprovalHandler() {
            @Override
            public Status approve(ToolCallContext ctx, String toolName, Map<String, Object> arguments) {
                return Status.APPROVED;
            }
        };
        ToolPipeline pipeline = new ToolPipeline(List.of(), List.of(), handler, 0);

        AiSkillResult result = pipeline.execute(context, "echo", Map.of("text", "ok"),
                args -> AiSkillResult.ok(String.valueOf(args.get("text"))));

        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("ok");
    }

    @Test
    void retryOnFailureUntilRetriesExhausted() {
        int[] attempts = {0};
        ToolPipeline pipeline = new ToolPipeline(List.of(), List.of(), null, 2);

        AiSkillResult result = pipeline.execute(context, "echo", Map.of(), args -> {
            attempts[0]++;
            if (attempts[0] < 3) {
                throw new IllegalStateException("transient boom");
            }
            return AiSkillResult.ok("recovered");
        });

        assertThat(attempts[0]).isEqualTo(3);
        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("recovered");
    }

    @Test
    void retryExhaustionReturnsFailure() {
        ToolPipeline pipeline = new ToolPipeline(List.of(), List.of(), null, 1);

        AiSkillResult result = pipeline.execute(context, "echo", Map.of(),
                args -> {
                    throw new IllegalStateException("always boom");
                });

        assertThat(result.success()).isFalse();
        assertThat(result.content()).contains("已重试");
        assertThat(result.content()).contains("always boom");
    }

    @Test
    void postHookCanRewriteResult() {
        ToolHook rewrite = new ToolHook() {
            @Override
            public AiSkillResult post(ToolCallContext ctx, String toolName, Map<String, Object> arguments,
                                      AiSkillResult result) {
                return AiSkillResult.ok(result.content() + "!!");
            }
        };
        ToolPipeline pipeline = new ToolPipeline(List.of(rewrite), List.of(), null, 0);

        AiSkillResult result = pipeline.execute(context, "echo", Map.of(), args -> AiSkillResult.ok("hello"));

        assertThat(result.success()).isTrue();
        assertThat(result.content()).isEqualTo("hello!!");
    }

    @Test
    void shortCircuitSkipsExecutorAndHooks() {
        ToolHook spy = new ToolHook() {
            @Override
            public Optional<String> pre(ToolCallContext ctx, String toolName, Map<String, Object> arguments) {
                return Optional.of("denied");
            }
        };
        AtomicFlag flag = new AtomicFlag();
        ToolPipeline pipeline = new ToolPipeline(List.of(spy), List.of(), null, 0);

        AiSkillResult result = pipeline.execute(context, "echo", Map.of(), args -> {
            flag.mark();
            return AiSkillResult.ok("never");
        });

        assertThat(result.success()).isFalse();
        assertThat(flag.called()).isFalse();
    }

    /** 轻量布尔标识，避免测试依赖 AtomicBoolean 语义混淆。 */
    private static final class AtomicFlag {
        private boolean value;

        private void mark() {
            this.value = true;
        }

        private boolean called() {
            return value;
        }
    }
}