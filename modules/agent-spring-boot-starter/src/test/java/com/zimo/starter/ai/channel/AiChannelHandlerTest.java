package com.zimo.starter.ai.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zimo.starter.ai.AiAgentProperties;
import com.zimo.starter.ai.AiAgentReply;
import com.zimo.starter.ai.AiAgentService;
import com.zimo.starter.ai.chat.AiChatClient;
import com.zimo.starter.ai.chat.AiChatRequest;
import com.zimo.starter.ai.chat.AiChatResponse;
import com.zimo.starter.ai.agent.AiAgentProfile;
import com.zimo.starter.ai.agent.AiAgentProfileResolver;
import com.zimo.starter.ai.agent.AiAgentRouteRequest;
import com.zimo.starter.ai.agent.AiHarnessAgentRegistry;
import com.zimo.starter.ai.agent.AiHarnessAgentRouter;
import com.zimo.starter.ai.agent.AiHarnessSessionKeyFactory;
import com.zimo.starter.ai.runtime.AiAgentRuntime;
import com.zimo.starter.ai.runtime.AiAgentRuntimeStatus;
import com.zimo.starter.ai.skill.AiSkill;
import com.zimo.starter.ai.skill.AiSkillRegistry;
import com.zimo.starter.ai.skill.AiSkillResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.harness.agent.HarnessAgent;
import reactor.core.publisher.Mono;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiChannelHandlerTest {
    @Test
    void returnsPromptWhenMessageTextIsBlank() {
        AiChannelHandler handler = new AiChannelHandler(agentService("not used"), new AiSkillRegistry(List.of()));

        AiChannelReply reply = handler.handle(AiChannelMessage.of(
                "feishu", "tenant_1", "user_1", "chat_1", "msg_1", "   "));

        assertThat(reply.type()).isEqualTo(AiChannelReply.Type.TEXT);
        assertThat(reply.content()).contains("\u6d88\u606f\u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a");
    }

    @Test
    void sendsNormalMessageToAgentChat() {
        AtomicReference<String> capturedSession = new AtomicReference<>();
        AiAgentService service = agentService("model reply", capturedSession);
        AiChannelHandler handler = new AiChannelHandler(service, new AiSkillRegistry(List.of()));

        AiChannelReply reply = handler.handle(AiChannelMessage.of(
                "feishu", "tenant_1", "user_1", "chat_1", "msg_1", "hello"));

        assertThat(reply.content()).isEqualTo("model reply");
        assertThat(capturedSession).hasValue("8:tenant_113:openclaw-lite6:feishu6:chat_16:user_1");
    }

    @Test
    void invokesRegisteredSkillWhenMessageUsesSkillCommand() {
        AiSkillRegistry registry = new AiSkillRegistry(List.of(new EchoSkill()));
        AiChannelHandler handler = new AiChannelHandler(
                agentService("not used"),
                registry,
                resolverForSkills("echo"));

        AiChannelReply reply = handler.handle(AiChannelMessage.of(
                "feishu", "tenant_1", "user_1", "chat_1", "msg_1",
                "\u6280\u80fd echo text=hello source=feishu"));

        assertThat(reply.content()).isEqualTo("hello / feishu");
    }

    @Test
    void invokesSkillWithJsonArguments() {
        AiSkillRegistry registry = new AiSkillRegistry(List.of(new EchoSkill()));
        AiChannelHandler handler = new AiChannelHandler(
                agentService("not used"),
                registry,
                resolverForSkills("echo"));

        AiChannelReply reply = handler.handle(AiChannelMessage.of(
                "feishu", "tenant_1", "user_1", "chat_1", "msg_1",
                "\u6280\u80fd echo {\"text\":\"hello world\",\"source\":\"feishu\"}"));

        assertThat(reply.content()).isEqualTo("hello world / feishu");
    }

    @Test
    void rejectsSkillCommandWhenResolvedAgentDoesNotBindSkill() {
        AiSkillRegistry registry = new AiSkillRegistry(List.of(new EchoSkill()));
        AiChannelHandler handler = new AiChannelHandler(
                agentService("not used"),
                registry,
                resolverForSkills());

        AiChannelReply reply = handler.handle(AiChannelMessage.of(
                "feishu",
                "tenant_1",
                "user_1",
                "chat_1",
                "msg_1",
                "技能 echo text=hello"));

        assertThat(reply.content()).contains("未绑定技能").contains("echo");
    }

    @Test
    void delegatesToRegisteredChannelIntentHandlerBeforeNormalChat() {
        AiSkillRegistry registry = new AiSkillRegistry(List.of());
        AiAgentProfile agent = agent("test-agent", List.of("demo_skill"), List.of("feishu"));
        AiAgentProfileResolver resolver = channel -> java.util.Optional.of(agent);
        AtomicReference<AiChatRequest> capturedRequest = new AtomicReference<>();
        AiAgentService service = service(runtime(AiAgentRuntimeStatus.READY, "ready"), request -> {
            capturedRequest.set(request);
            return AiChatResponse.ok("chat reply");
        });
        AiChannelHandler handler = new AiChannelHandler(
                service,
                registry,
                resolver,
                List.of((message, defaultAgent) -> AiChannelReply.text(
                        defaultAgent.id() + ":" + message.text())));

        AiChannelReply reply = handler.handle(AiChannelMessage.of(
                "feishu", "tenant_1", "user_1", "chat_1", "msg_1", "route me"));

        assertThat(reply.content()).isEqualTo("test-agent:route me");
        assertThat(capturedRequest.get()).isNull();
    }

    @Test
    void usesDefaultChannelAgentForNormalChatWhenNoIntentHandlerMatches() {
        AtomicReference<AiChatRequest> capturedRequest = new AtomicReference<>();
        AiSkillRegistry registry = new AiSkillRegistry(List.of());
        AiAgentProfile agent = agent("test-agent", List.of("summarize"), List.of("feishu"));
        AiAgentProfileResolver resolver = channel -> java.util.Optional.of(agent);
        AiAgentService service = service(runtime(AiAgentRuntimeStatus.READY, "ready"), request -> {
            capturedRequest.set(request);
            return AiChatResponse.ok("agent reply");
        });
        AiChannelHandler handler = new AiChannelHandler(
                service,
                registry,
                resolver,
                List.of((message, defaultAgent) -> null));

        AiChannelReply reply = handler.handle(AiChannelMessage.of(
                "feishu", "tenant_1", "user_1", "chat_1", "msg_1", "hello"));

        assertThat(reply.content()).isEqualTo("agent reply");
        assertThat(capturedRequest.get().agentName()).isEqualTo("test-agent-name");
        assertThat(capturedRequest.get().sessionId()).isEqualTo("8:tenant_110:test-agent6:feishu6:chat_16:user_1");
    }

    @Test
    void resolvesAgentWithCompleteChannelMessage() {
        AtomicReference<AiChannelMessage> resolvedMessage = new AtomicReference<>();
        AiAgentProfileResolver resolver = new AiAgentProfileResolver() {
            @Override
            public Optional<AiAgentProfile> resolveDefaultForChannel(String channel) {
                return Optional.empty();
            }

            @Override
            public Optional<AiAgentProfile> resolveForMessage(AiChannelMessage message) {
                resolvedMessage.set(message);
                return Optional.of(agent("a-bound", List.of(), List.of("feishu")));
            }
        };
        AiChannelHandler handler = new AiChannelHandler(
                agentService("ok"),
                new AiSkillRegistry(List.of()),
                resolver);
        AiChannelMessage message = AiChannelMessage.of(
                "feishu",
                "tenant_1",
                "user_1",
                "chat_1",
                "msg_1",
                "hello",
                Map.of("agentId", "a-bound"));

        handler.handle(message);

        assertThat(resolvedMessage).hasValue(message);
    }

    @Test
    void usesManagedTenantForTrustedFeishuAgentBinding() {
        AiAgentProfile boundAgent = new AiAgentProfile(
                "a-bound",
                "owner-user",
                "bound-agent",
                "qwen-plus",
                "persona",
                List.of(),
                true);
        AiAgentProfileResolver resolver = new AiAgentProfileResolver() {
            @Override
            public Optional<AiAgentProfile> resolveDefaultForChannel(String channel) {
                return Optional.empty();
            }

            @Override
            public Optional<AiAgentProfile> resolveForMessage(AiChannelMessage message) {
                return Optional.of(boundAgent);
            }
        };
        AiAgentService service = mock(AiAgentService.class);
        when(service.chat(eq("hello"), any(AiAgentRouteRequest.class)))
                .thenReturn(new AiAgentReply("a-bound", "ok"));
        AiChannelHandler handler = new AiChannelHandler(
                service,
                new AiSkillRegistry(List.of()),
                resolver);
        AiChannelMessage message = AiChannelMessage.of(
                "feishu",
                "feishu-tenant-key",
                "open-id",
                "chat-id",
                "message-id",
                "hello",
                Map.of(
                        AiChannelAgentBinding.ATTRIBUTE_NAME,
                        new AiChannelAgentBinding("feishu-tenant-key", "a-bound")));

        handler.handle(message);

        ArgumentCaptor<AiAgentRouteRequest> routeCaptor =
                ArgumentCaptor.forClass(AiAgentRouteRequest.class);
        verify(service).chat(eq("hello"), routeCaptor.capture());
        assertThat(routeCaptor.getValue().tenantId()).isEqualTo("owner-user");
        assertThat(routeCaptor.getValue().explicitProfile()).isEqualTo(boundAgent);
    }

    @Test
    void doesNotTrustRawFeishuAgentIdForCrossTenantMapping() {
        AiAgentProfile boundAgent = new AiAgentProfile(
                "a-bound",
                "owner-user",
                "bound-agent",
                "qwen-plus",
                "persona",
                List.of(),
                true);
        AiAgentProfileResolver resolver = new AiAgentProfileResolver() {
            @Override
            public Optional<AiAgentProfile> resolveDefaultForChannel(String channel) {
                return Optional.empty();
            }

            @Override
            public Optional<AiAgentProfile> resolveForMessage(AiChannelMessage message) {
                return Optional.of(boundAgent);
            }
        };
        AiAgentService service = mock(AiAgentService.class);
        when(service.chat(eq("hello"), any(AiAgentRouteRequest.class)))
                .thenReturn(new AiAgentReply("ai-agent", "ok"));
        AiChannelHandler handler = new AiChannelHandler(
                service,
                new AiSkillRegistry(List.of()),
                resolver);

        handler.handle(AiChannelMessage.of(
                "feishu",
                "feishu-tenant-key",
                "open-id",
                "chat-id",
                "message-id",
                "hello",
                Map.of("agentId", "a-bound")));

        ArgumentCaptor<AiAgentRouteRequest> routeCaptor =
                ArgumentCaptor.forClass(AiAgentRouteRequest.class);
        verify(service).chat(eq("hello"), routeCaptor.capture());
        assertThat(routeCaptor.getValue().tenantId()).isEqualTo("feishu-tenant-key");
        assertThat(routeCaptor.getValue().explicitProfile()).isNull();
    }

    @Test
    void returnsEmptyWhenResolvingNullMessageByDefault() {
        AiAgentProfileResolver resolver = channel -> Optional.of(
                agent("default-agent", List.of(), List.of(channel)));

        assertThat(resolver.resolveForMessage(null)).isEmpty();
    }
    private static AiAgentProfileResolver resolverForSkills(String... skillIds) {
        AiAgentProfile profile = agent("skill-agent", List.of(skillIds), List.of("feishu"));
        return channel -> Optional.of(profile);
    }

    private static AiAgentProfile agent(String id, List<String> skillIds, List<String> defaultChannels) {
        return new AiAgentProfile(
                id, "tenant_1", id + "-name", "qwen-plus", "persona", skillIds, true);
    }

    private AiAgentService agentService(String response) {
        return agentService(response, new AtomicReference<>());
    }

    private AiAgentService agentService(String response, AtomicReference<String> capturedSession) {
        return service(runtime(AiAgentRuntimeStatus.READY, "ready"), request -> {
            capturedSession.set(request.sessionId());
            return AiChatResponse.ok(response);
        });
    }

    private AiAgentService service(AiAgentRuntime runtime, AiChatClient chatClient) {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setName("openclaw-lite");
        AiHarnessAgentRegistry registry = mock(AiHarnessAgentRegistry.class);
        when(registry.withAgent(any(AiAgentProfile.class), any())).thenAnswer(invocation -> {
            AiAgentProfile profile = invocation.getArgument(0);
            HarnessAgent agent = mock(HarnessAgent.class);
            when(agent.call(any(Msg.class), any(RuntimeContext.class))).thenAnswer(call -> {
                Msg message = call.getArgument(0);
                RuntimeContext context = call.getArgument(1);
                AiChatResponse response = chatClient.chat(new AiChatRequest(
                        profile.name(),
                        message.getTextContent(),
                        context.getSessionId(),
                        profile.modelName(),
                        properties.getTemperature(),
                        properties.getMaxTokens(),
                        profile.systemPrompt(),
                        List.of()));
                String content = response.success() ? response.content() : response.errorMessage();
                return Mono.just(Msg.builder()
                        .name(profile.name())
                        .role(MsgRole.ASSISTANT)
                        .textContent(content)
                        .build());
            });
            Function<HarnessAgent, ?> action = invocation.getArgument(1);
            return action.apply(agent);
        });
        AiHarnessAgentRouter router = AiHarnessAgentRouter.withDefaultProvider(
                null,
                request -> new AiAgentProfile(
                        properties.getName(),
                        request.tenantId(),
                        properties.getName(),
                        properties.getModelName(),
                        properties.getSystemPrompt(),
                        List.of(),
                        true));
        return new AiAgentService(
                properties,
                new AiSkillRegistry(List.of()),
                runtime,
                chatClient,
                router,
                registry,
                new AiHarnessSessionKeyFactory(),
                null,
                null,
                null,
                null,
                java.util.List.of() /* middlewares */);
    }

    private AiAgentRuntime runtime(AiAgentRuntimeStatus status, String message) {
        return new AiAgentRuntime("openclaw-lite", "qwen-plus", "dashscope_chat", List.of(), status, message);
    }

    private static class EchoSkill implements AiSkill {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "echo";
        }

        @Override
        public boolean readOnly() {
            return true;
        }

        @Override
        public AiSkillResult call(Map<String, Object> arguments) {
            return AiSkillResult.ok(arguments.get("text") + " / " + arguments.get("source"));
        }
    }
}
