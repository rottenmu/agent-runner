package com.zimo.framework.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zimo.framework.ai.agent.AiAgentProfile;
import com.zimo.framework.ai.agent.AiAgentRouteRequest;
import com.zimo.framework.ai.agent.AiHarnessAgentRegistry;
import com.zimo.framework.ai.agent.AiHarnessAgentRouter;
import com.zimo.framework.ai.agent.AiHarnessSessionKeyFactory;
import com.zimo.framework.ai.chat.AiChatClient;
import com.zimo.framework.ai.runtime.AiAgentRuntime;
import com.zimo.framework.ai.runtime.AiAgentRuntimeStatus;
import com.zimo.framework.ai.skill.AiSkillRegistry;
import com.zimo.module.agentmemory.engine.MemoryAwarePromptBuilder;
import com.zimo.module.agentmemory.engine.MemoryScope;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

/**
 * 异步回写的<b>接线契约</b>（M3，PRD §7.2）：回写只在「拿到有效回复」之后投递。
 *
 * <p>为什么这组用例必须存在：真机验证里模型调用失败（环境缺 API key，返回 404）时，
 * 审计表不会出现 {@code sync/write/pending} 与 {@code async/write} 行，链路里也只有
 * {@code sync/recall}。若只看真机结果，无法区分下面两种解释：</p>
 *
 * <ul>
 *   <li>(a) 回写接线断了 —— 实现缺陷；</li>
 *   <li>(b) 回写被正确地跳过了 —— 失败回复不该当记忆存。</li>
 * </ul>
 *
 * <p>本类把 (b) 钉成可回归的事实：<b>成功必回写、失败与空回复必不回写</b>。这样真机上
 * 那两条审计断言的失败就能归因到「模型不可用」而不是「回写没接上」。</p>
 *
 * <p>另注：{@code writebackMemory} 必须在 {@code TraceCollector.end()} 之前调用（同步骨架
 * span 只有此刻发出才会被导出），故成功用例同时断言回写发生与回复正常返回。</p>
 */
class AiAgentServiceMemoryWritebackTest {

    private AiAgentProperties properties;
    private AiHarnessAgentRouter router;
    private AiHarnessAgentRegistry registry;
    private AiHarnessSessionKeyFactory sessionKeyFactory;
    private MemoryAwarePromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        properties = new AiAgentProperties();
        properties.setApiKey("test-secret");
        router = mock(AiHarnessAgentRouter.class);
        registry = mock(AiHarnessAgentRegistry.class);
        sessionKeyFactory = mock(AiHarnessSessionKeyFactory.class);
        promptBuilder = mock(MemoryAwarePromptBuilder.class);
    }

    @Test
    void writesBackMemoryWhenReplyIsValid() {
        AiAgentProfile profile = profile();
        AiAgentRouteRequest request = request(profile);
        HarnessAgent agent = agentReturning("harness reply");
        wireRoute(request, profile, agent);
        AiAgentService service = service(promptBuilder);

        AiAgentReply reply = service.chat("hello", request);

        assertThat(reply.content()).isEqualTo("harness reply");
        ArgumentCaptor<String> traceCaptor = ArgumentCaptor.forClass(String.class);
        verify(promptBuilder).afterReply(
                eq(MemoryScope.of("tenant-a", "user-a", "chat-a")),
                traceCaptor.capture(),
                eq("hello"),
                eq("harness reply"));
        // 回写必须带得回链路：traceId 为空会让审计行无法归因到任何一条链路
        assertThat(traceCaptor.getValue()).isNotBlank();
    }

    @Test
    void skipsWritebackWhenModelCallFails() {
        AiAgentProfile profile = profile();
        AiAgentRouteRequest request = request(profile);
        HarnessAgent agent = mock(HarnessAgent.class);
        // 复刻真机故障：模型侧 HTTP 404
        when(agent.call(any(Msg.class), any(RuntimeContext.class)))
                .thenReturn(Mono.error(new RuntimeException("HTTP request failed with status 404")));
        wireRoute(request, profile, agent);
        AiAgentService service = service(promptBuilder);

        AiAgentReply reply = service.chat("hello", request);

        assertThat(reply.content()).contains("AI 智能体调用失败");
        verify(promptBuilder, never()).afterReply(any(), any(), any(), any());
    }

    @Test
    void skipsWritebackWhenReplyIsBlank() {
        AiAgentProfile profile = profile();
        AiAgentRouteRequest request = request(profile);
        HarnessAgent agent = mock(HarnessAgent.class);
        when(agent.call(any(Msg.class), any(RuntimeContext.class)))
                .thenReturn(Mono.just(Msg.builder()
                        .name("agent-a")
                        .role(MsgRole.ASSISTANT)
                        .textContent("   ")
                        .build()));
        wireRoute(request, profile, agent);
        AiAgentService service = service(promptBuilder);

        AiAgentReply reply = service.chat("hello", request);

        assertThat(reply.content()).contains("未返回有效内容");
        verify(promptBuilder, never()).afterReply(any(), any(), any(), any());
    }

    @Test
    void repliesNormallyWhenMemoryEnhancerIsAbsent() {
        AiAgentProfile profile = profile();
        AiAgentRouteRequest request = request(profile);
        wireRoute(request, profile, agentReturning("harness reply"));
        // 记忆未启用时装配层传 null：记忆是增强，缺席不得影响对话本身
        AiAgentService service = service(null);

        assertThatCode(() -> {
            AiAgentReply reply = service.chat("hello", request);
            assertThat(reply.content()).isEqualTo("harness reply");
        }).doesNotThrowAnyException();
    }

    private HarnessAgent agentReturning(String text) {
        HarnessAgent agent = mock(HarnessAgent.class);
        when(agent.call(any(Msg.class), any(RuntimeContext.class)))
                .thenReturn(Mono.just(Msg.builder()
                        .name("agent-a")
                        .role(MsgRole.ASSISTANT)
                        .textContent(text)
                        .build()));
        return agent;
    }

    private void wireRoute(
            AiAgentRouteRequest request, AiAgentProfile profile, HarnessAgent agent) {
        when(router.route(request)).thenReturn(Optional.of(profile));
        when(sessionKeyFactory.create(request, profile))
                .thenReturn("tenant-a:agent-a:feishu:chat-a:user-a");
        when(registry.withAgent(eq(profile), any())).thenAnswer(invocation -> {
            Function<HarnessAgent, ?> action = invocation.getArgument(1);
            return action.apply(agent);
        });
    }

    private AiAgentService service(MemoryAwarePromptBuilder builder) {
        AiChatClient fallbackClient = request -> {
            throw new AssertionError("routed HarnessAgent calls must not use AiChatClient fallback");
        };
        return new AiAgentService(
                properties,
                new AiSkillRegistry(java.util.List.of()),
                runtime(),
                fallbackClient,
                router,
                registry,
                sessionKeyFactory,
                null,
                null,
                null,
                null,
                java.util.List.of() /* middlewares */,
                null /* pluginEventBus */,
                builder);
    }

    private AiAgentProfile profile() {
        return new AiAgentProfile(
                "agent-a",
                "tenant-a",
                "Agent A",
                "model-a",
                "system-a",
                java.util.List.of(),
                true);
    }

    private AiAgentRouteRequest request(AiAgentProfile profile) {
        return new AiAgentRouteRequest(
                "tenant-a",
                "feishu",
                "user-a",
                "chat-a",
                profile,
                null);
    }

    private AiAgentRuntime runtime() {
        return new AiAgentRuntime(
                "ai-agent",
                "qwen-plus",
                "dashscope_chat",
                java.util.List.of(),
                AiAgentRuntimeStatus.READY,
                "ready");
    }
}
