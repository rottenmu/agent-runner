package com.zimo.framework.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.module.agentmemory.chat.AiChatMessage;
import com.zimo.framework.ai.agent.AiHarnessAgentFactory;
import com.zimo.framework.ai.agent.AiHarnessAgentRegistry;
import com.zimo.framework.ai.agent.AiHarnessAgentRouter;
import com.zimo.framework.ai.agent.AiHarnessSessionKeyFactory;
import com.zimo.framework.ai.chat.AiChatClient;
import com.zimo.framework.ai.chat.AiChatResponse;
import com.zimo.framework.ai.runtime.AiAgentRuntime;
import com.zimo.framework.ai.runtime.AiAgentRuntimeStatus;
import com.zimo.framework.ai.skill.AiSkillRegistry;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 压缩严格重试测试（P1）：摘要失败按 maxRetries 重试，耗尽回退为空。
 */
class CompressionRetryTest {

    private AiAgentService service(AiChatClient chatClient) {
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
                new AiHarnessAgentRouter(null, (com.zimo.framework.ai.agent.AiAgentProfile) null),
                registry,
                new AiHarnessSessionKeyFactory(),
                null,
                null,
                null,
                List.of(),
                java.util.List.of() /* middlewares */);
    }

    private String callSummarizeWithRetry(AiAgentService svc, int maxRetries) throws Exception {
        Method m = AiAgentService.class.getDeclaredMethod(
                "summarizeWithRetry", List.class, int.class, int.class);
        m.setAccessible(true);
        return (String) m.invoke(svc,
                List.of(new AiChatMessage("user", "第一轮对话内容")), 200, maxRetries);
    }

    @Test
    void retriesUntilSuccessThenStops() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AiAgentService svc = service(req -> {
            calls.incrementAndGet();
            // 前 2 次失败（模拟 LLM 超时），第 3 次成功
            if (calls.get() <= 2) {
                return AiChatResponse.fail("模型超时");
            }
            return AiChatResponse.ok("最终摘要");
        });

        String result = callSummarizeWithRetry(svc, 3);

        assertThat(result).isEqualTo("最终摘要");
        assertThat(calls).hasValue(3);   // 初始 1 次 + 重试 2 次
    }

    @Test
    void fallsBackEmptyWhenAllRetriesExhausted() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AiAgentService svc = service(req -> {
            calls.incrementAndGet();
            return AiChatResponse.fail("模型不可用");
        });

        String result = callSummarizeWithRetry(svc, 2);

        assertThat(result).isEmpty();     // 回退：保持会话记忆不变
        assertThat(calls).hasValue(3);    // 初始 1 次 + 重试 2 次 = 3
    }

    @Test
    void maxRetriesZeroMeansSingleAttempt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AiAgentService svc = service(req -> {
            calls.incrementAndGet();
            return AiChatResponse.fail("失败");
        });

        String result = callSummarizeWithRetry(svc, 0);

        assertThat(result).isEmpty();
        assertThat(calls).hasValue(1);    // 不重试
    }

    @Test
    void propertiesDefaultMaxRetriesIsTwoAndConfigurable() {
        AiAgentProperties properties = new AiAgentProperties();
        assertThat(properties.getContextCompressionMaxRetries()).isEqualTo(2);
        properties.setContextCompressionMaxRetries(5);
        assertThat(properties.getEffectiveContextCompressionSettings().maxRetries()).isEqualTo(5);
        properties.setContextCompressionMaxRetries(-1);
        assertThat(properties.getContextCompressionMaxRetries()).isEqualTo(0);
    }
}
