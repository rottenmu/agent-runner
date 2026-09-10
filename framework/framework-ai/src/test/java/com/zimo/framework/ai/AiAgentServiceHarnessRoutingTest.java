package com.zimo.framework.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class AiAgentServiceHarnessRoutingTest {

    private AiAgentProperties properties;
    private AiHarnessAgentRouter router;
    private AiHarnessAgentRegistry registry;
    private AiHarnessSessionKeyFactory sessionKeyFactory;
    private AiAgentService service;

    @BeforeEach
    void setUp() {
        properties = new AiAgentProperties();
        properties.setApiKey("test-secret");
        router = mock(AiHarnessAgentRouter.class);
        registry = mock(AiHarnessAgentRegistry.class);
        sessionKeyFactory = mock(AiHarnessSessionKeyFactory.class);
        AiChatClient fallbackClient = request -> {
            throw new AssertionError("routed HarnessAgent calls must not use AiChatClient fallback");
        };
        service = new AiAgentService(
                properties,
                new AiSkillRegistry(List.of()),
                runtime(),
                fallbackClient,
                router,
                registry,
                sessionKeyFactory,
                null,
                null,
                null,
                null,
                java.util.List.of() /* middlewares */);
    }

    @Test
    void invokesRoutedHarnessAgentWithIsolatedRuntimeContext() {
        AiAgentProfile profile = profile();
        AiAgentRouteRequest request = request(profile);
        HarnessAgent agent = mock(HarnessAgent.class);
        when(router.route(request)).thenReturn(Optional.of(profile));
        when(sessionKeyFactory.create(request, profile))
                .thenReturn("tenant-a:agent-a:feishu:chat-a:user-a");
        when(registry.withAgent(eq(profile), any())).thenAnswer(invocation -> {
            Function<HarnessAgent, ?> action = invocation.getArgument(1);
            return action.apply(agent);
        });
        when(agent.call(any(Msg.class), any(RuntimeContext.class)))
                .thenReturn(Mono.just(Msg.builder()
                        .name("agent-a")
                        .role(MsgRole.ASSISTANT)
                        .textContent("harness reply")
                        .build()));

        AiAgentReply reply = service.chat("hello", request);

        assertThat(reply.agent()).isEqualTo("Agent A");
        assertThat(reply.content()).isEqualTo("harness reply");
        ArgumentCaptor<Msg> messageCaptor = ArgumentCaptor.forClass(Msg.class);
        ArgumentCaptor<RuntimeContext> contextCaptor =
                ArgumentCaptor.forClass(RuntimeContext.class);
        verify(agent).call(messageCaptor.capture(), contextCaptor.capture());
        assertThat(messageCaptor.getValue().getRole()).isEqualTo(MsgRole.USER);
        assertThat(messageCaptor.getValue().getTextContent()).isEqualTo("hello");
        assertThat(contextCaptor.getValue().getSessionId())
                .isEqualTo("tenant-a:agent-a:feishu:chat-a:user-a");
        assertThat(contextCaptor.getValue().getUserId()).isEqualTo("user-a");
        assertThat((String) contextCaptor.getValue().get("tenantId")).isEqualTo("tenant-a");
    }

    @Test
    void returnsRedactedErrorWhenHarnessAgentCreationFails() {
        AiAgentProfile profile = profile();
        AiAgentRouteRequest request = request(profile);
        when(router.route(request)).thenReturn(Optional.of(profile));
        when(sessionKeyFactory.create(request, profile)).thenReturn("session-a");
        when(registry.withAgent(eq(profile), any()))
                .thenThrow(new IllegalStateException("provider echoed test-secret"));

        AiAgentReply reply = service.chat("hello", request);

        assertThat(reply.content()).contains("AI 智能体调用失败");
        assertThat(reply.content()).contains("[redacted]");
        assertThat(reply.content()).doesNotContain("test-secret");
    }

    @Test
    void returnsRouteFailureWithoutAccessingRegistry() {
        AiAgentRouteRequest request = request(null);
        when(router.route(request)).thenReturn(Optional.empty());

        AiAgentReply reply = service.chat("hello", request);

        assertThat(reply.content()).contains("未找到可用智能体");
        verifyNoInteractions(registry);
    }

    private AiAgentProfile profile() {
        return new AiAgentProfile(
                "agent-a",
                "tenant-a",
                "Agent A",
                "model-a",
                "system-a",
                List.of(),
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
                List.of(),
                AiAgentRuntimeStatus.READY,
                "ready");
    }
}
