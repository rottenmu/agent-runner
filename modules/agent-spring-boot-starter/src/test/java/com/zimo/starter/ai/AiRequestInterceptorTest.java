package com.zimo.starter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.starter.ai.agent.AiHarnessAgentFactory;
import com.zimo.starter.ai.agent.AiHarnessAgentRegistry;
import com.zimo.starter.ai.agent.AiHarnessAgentRouter;
import com.zimo.starter.ai.agent.AiHarnessSessionKeyFactory;
import com.zimo.starter.ai.agent.AiRequestDecision;
import com.zimo.starter.ai.agent.AiRequestInterceptor;
import com.zimo.starter.ai.chat.AiChatClient;
import com.zimo.starter.ai.chat.AiChatRequest;
import com.zimo.starter.ai.chat.AiChatResponse;
import com.zimo.starter.ai.runtime.AiAgentRuntime;
import com.zimo.starter.ai.runtime.AiAgentRuntimeStatus;
import com.zimo.starter.ai.skill.AiSkillRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 请求拦截器（AiRequestInterceptor）测试：放行/改写/拒绝/链式。
 */
class AiRequestInterceptorTest {

    private AiAgentService service(List<AiRequestInterceptor> interceptors, AiChatClient chatClient) {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setName("ai-agent");
        properties.setModelName("qwen-plus");
        AiHarnessAgentRegistry registry = new AiHarnessAgentRegistry(
                new AiHarnessAgentFactory(properties, new AiSkillRegistry(List.of())),
                properties);
        return new AiAgentService(
                properties,
                new AiSkillRegistry(List.of()),
                new AiAgentRuntime("ai-agent", "qwen-plus", "dashscope_chat", List.of(),
                        AiAgentRuntimeStatus.READY, "ready"),
                chatClient,
                new AiHarnessAgentRouter(null, (com.zimo.starter.ai.agent.AiAgentProfile) null),
                registry,
                new AiHarnessSessionKeyFactory(),
                null,
                null,
                null,
                interceptors,
                java.util.List.of() /* middlewares */);
    }

    @Test
    void passThroughKeepsOriginalMessage() {
        AtomicReference<String> seen = new AtomicReference<>();
        AiAgentService svc = service(
                List.of((ctx, msg) -> AiRequestDecision.pass()),
                req -> {
                    seen.set(req.message());
                    return AiChatResponse.ok("ok");
                });

        AiAgentReply reply = svc.reply("hello");

        assertThat(seen).hasValue("hello");
        assertThat(reply.content()).isEqualTo("ok");
    }

    @Test
    void rewriteChangesMessageBeforeModel() {
        AtomicReference<String> seen = new AtomicReference<>();
        AiAgentService svc = service(
                List.of((ctx, msg) -> AiRequestDecision.rewrite("[改写]" + msg)),
                req -> {
                    seen.set(req.message());
                    return AiChatResponse.ok("ok");
                });

        svc.reply("帮我查资料");

        assertThat(seen).hasValue("[改写]帮我查资料");
    }

    @Test
    void denyBlocksRequestWithoutCallingModel() {
        AtomicInteger calls = new AtomicInteger();
        AiAgentService svc = service(
                List.of((ctx, msg) -> AiRequestDecision.deny("内容涉及敏感词")),
                req -> {
                    calls.incrementAndGet();
                    return AiChatResponse.ok("no");
                });

        AiAgentReply reply = svc.reply("攻击性内容");

        assertThat(reply.content()).contains("请求被拦截").contains("敏感词");
        assertThat(calls).hasValue(0);
    }

    @Test
    void chainRewriteThenPassAppliesAll() {
        AtomicReference<String> seen = new AtomicReference<>();
        AiAgentService svc = service(
                List.of(
                        (ctx, msg) -> AiRequestDecision.rewrite("[前缀]" + msg),
                        (ctx, msg) -> AiRequestDecision.rewrite(msg + "[后缀]"),
                        (ctx, msg) -> AiRequestDecision.pass()),
                req -> {
                    seen.set(req.message());
                    return AiChatResponse.ok("ok");
                });

        svc.reply("原始消息");

        assertThat(seen).hasValue("[前缀]原始消息[后缀]");
    }

    @Test
    void firstDenyStopsChain() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger secondCalled = new AtomicInteger();
        AiAgentService svc = service(
                List.of(
                        (ctx, msg) -> AiRequestDecision.deny("安全拦截"),
                        (ctx, msg) -> {
                            secondCalled.incrementAndGet();
                            return AiRequestDecision.rewrite("不应到达");
                        }),
                req -> {
                    calls.incrementAndGet();
                    return AiChatResponse.ok("no");
                });

        AiAgentReply reply = svc.reply("危险指令");

        assertThat(reply.content()).contains("安全拦截");
        assertThat(secondCalled).hasValue(0);
        assertThat(calls).hasValue(0);
    }
}
