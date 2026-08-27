package com.zimo.starter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.starter.ai.agent.AiAgentProfile;
import com.zimo.starter.ai.agent.AiHarnessAgentFactory;
import com.zimo.starter.ai.agent.AiHarnessAgentRegistry;
import com.zimo.starter.ai.agent.AiHarnessAgentRouter;
import com.zimo.starter.ai.agent.AiHarnessSessionKeyFactory;
import com.zimo.starter.ai.chat.AiChatClient;
import com.zimo.starter.ai.chat.AiChatRequest;
import com.zimo.starter.ai.chat.AiChatResponse;
import com.zimo.starter.ai.runtime.AiAgentRuntime;
import com.zimo.starter.ai.runtime.AiAgentRuntimeStatus;
import com.zimo.starter.ai.skill.AiSkillRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiAgentServiceTest {
    @Test
    void returnsNotConfiguredMessageWithoutCallingChatClient() {
        AtomicInteger calls = new AtomicInteger();
        AiAgentService service = service(runtime(AiAgentRuntimeStatus.NOT_CONFIGURED, "AI service is not configured"),
                request -> {
                    calls.incrementAndGet();
                    return AiChatResponse.ok("should not call");
                });

        AiAgentReply reply = service.chat("hello", "s1");

        assertThat(reply.agent()).isEqualTo("ai-agent");
        assertThat(reply.content()).contains("AI service is not configured");
        assertThat(calls).hasValue(0);
    }

    @Test
    void rejectsBlankMessageWithoutCallingChatClient() {
        AtomicInteger calls = new AtomicInteger();
        AiAgentService service = service(runtime(AiAgentRuntimeStatus.READY, "ready"),
                request -> {
                    calls.incrementAndGet();
                    return AiChatResponse.ok("should not call");
                });

        AiAgentReply reply = service.chat("   ", "s1");

        assertThat(reply.content()).contains("消息内容不能为空");
        assertThat(calls).hasValue(0);
    }

    @Test
    void returnsModelContentWhenRuntimeIsReady() {
        AiAgentService service = service(runtime(AiAgentRuntimeStatus.READY, "ready"),
                request -> AiChatResponse.ok("model reply"));

        AiAgentReply reply = service.reply("hello");

        assertThat(reply.agent()).isEqualTo("ai-agent");
        assertThat(reply.content()).isEqualTo("model reply");
    }

    @Test
    void returnsReadableErrorWhenChatClientFails() {
        AiAgentService service = service(runtime(AiAgentRuntimeStatus.READY, "ready"),
                request -> AiChatResponse.fail("model call failed: 500"));

        AiAgentReply reply = service.chat("hello", "s1");

        assertThat(reply.content()).contains("model call failed");
    }

    @Test
    void carriesRecentSessionHistoryIntoFollowUpRequest() {
        List<AiChatRequest> requests = new ArrayList<>();
        AiAgentService service = service(runtime(AiAgentRuntimeStatus.READY, "ready"),
                request -> {
                    requests.add(request);
                    return AiChatResponse.ok(request.message() + " answer");
                });

        service.chat("first", "feishu:tenant:chat:user");
        service.chat("second", "feishu:tenant:chat:user");

        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).history()).isEmpty();
        assertThat(requests.get(1).history())
                .extracting("role", "content")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("user", "first"),
                        org.assertj.core.groups.Tuple.tuple("assistant", "first answer"));
        assertThat(requests.get(1).systemPrompt()).isNotBlank();
    }

    @Test
    void usesRuntimeAgentProfileForChatRequest() {
        List<AiChatRequest> requests = new ArrayList<>();
        AiAgentService service = service(runtime(AiAgentRuntimeStatus.READY, "ready"), request -> {
            requests.add(request);
            return AiChatResponse.ok("profile reply");
        });
        AiAgentProfile profile = new AiAgentProfile(
                "agent-1", "managed-agent", "deepseek-chat", "managed prompt", List.of("skill-1"));

        AiAgentReply reply = service.chat("hello", "s1", profile);

        assertThat(reply.agent()).isEqualTo("managed-agent");
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.modelName()).isEqualTo("deepseek-chat");
            assertThat(request.systemPrompt()).isEqualTo("managed prompt");
        });
    }
    private AiAgentService service(AiAgentRuntime runtime, AiChatClient chatClient) {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setName("ai-agent");
        properties.setModelName("qwen-plus");
        properties.setTemperature(0.7);
        properties.setMaxTokens(2000);
        AiHarnessAgentRegistry registry = new AiHarnessAgentRegistry(
                new AiHarnessAgentFactory(properties, new AiSkillRegistry(List.of())),
                properties);
        return new AiAgentService(
                properties,
                new AiSkillRegistry(List.of()),
                runtime,
                chatClient,
                new AiHarnessAgentRouter(null, (AiAgentProfile) null),
                registry,
                new AiHarnessSessionKeyFactory(),
                null,
                null,
                null,
                null,
                java.util.List.of() /* middlewares */);
    }

    private AiAgentRuntime runtime(AiAgentRuntimeStatus status, String message) {
        return new AiAgentRuntime("ai-agent", "qwen-plus", "dashscope_chat", List.of(), status, message);
    }
}
