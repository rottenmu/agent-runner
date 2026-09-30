package com.zimo.framework.ai.memory;

import com.zimo.module.agentmemory.engine.MemoryAwarePromptBuilder;
import com.zimo.module.agentmemory.engine.MemoryScope;
import com.zimo.framework.ai.observ.HarnessTraceMiddleware;
import io.agentscope.core.agent.RuntimeContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 记忆预召回注入中间件验证。
 *
 * <p>本类钉住的是<b>注入契约</b>，不是召回算法：</p>
 * <ol>
 *   <li>片段追加在系统提示词<b>末尾</b>，原提示词逐字保留；</li>
 *   <li>系统提示词为空时不留下前导空行；</li>
 *   <li><b>每轮只召回一次</b> —— {@code onSystemPrompt} 在推理循环里会被多次调用，
 *       不缓存就会一轮产生多份审计行，指标虚高且无法解释；</li>
 *   <li>「本轮无片段」这个负结论同样要缓存，否则每轮都要重复付一次召回成本；</li>
 *   <li>隔离维度与查询文本经 {@code RuntimeContext} 传递，链路 ID 复用自研中间件的键；</li>
 *   <li>任何异常都降级为「原样返回提示词」——记忆是增强能力，不允许让对话失败。</li>
 * </ol>
 *
 * <p><b>为什么这里用桩而不是真召回</b>：真实召回链路的正确性已由
 * {@code MemoryAwarePromptBuilderTest} 覆盖。本类只关心中间件与增强器之间的接口契约，
 * 用桩才能精确构造「返回 null」「抛异常」「被调用几次」这三种状态。</p>
 */
class MemoryPromptMiddlewareTest {

    private static final String TRACE = "trace-mw-1";

    @Test
    @DisplayName("片段追加到系统提示词末尾，原提示词逐字保留")
    void appendsFragmentToSystemPrompt() {
        StubBuilder builder = new StubBuilder("## 已知的用户长期记忆\n- [custom|0.90] 偏好邮件汇报");
        MemoryPromptMiddleware middleware = new MemoryPromptMiddleware(builder);
        RuntimeContext context = context(
                MemoryScope.of("t1", "u1", "s1"), "怎么汇报", TRACE);

        String result = middleware.onSystemPrompt(null, context, "你是一个助手").block();

        assertTrue(result.startsWith("你是一个助手"), "原系统提示词必须逐字保留，实际：" + result);
        assertTrue(result.contains("偏好邮件汇报"), "片段没进去，记忆等于没注入");
        assertTrue(result.indexOf("你是一个助手") < result.indexOf("## 已知的用户长期记忆"),
                "片段必须在原提示词之后（追加语义），实际：" + result);
    }

    @Test
    @DisplayName("系统提示词为空时只返回片段，不产生前导空行")
    void noLeadingBlankLineWhenPromptBlank() {
        StubBuilder builder = new StubBuilder("## 已知的用户长期记忆\n- [custom|0.90] 偏好邮件汇报");
        MemoryPromptMiddleware middleware = new MemoryPromptMiddleware(builder);

        String result = middleware.onSystemPrompt(null,
                context(MemoryScope.of("t1", "u1", "s1"), "怎么汇报", TRACE), "   ").block();

        assertEquals("## 已知的用户长期记忆\n- [custom|0.90] 偏好邮件汇报", result);
    }

    @Test
    @DisplayName("同一轮内多次调用只召回一次（结果被缓存进 RuntimeContext）")
    void recallsOnlyOncePerTurn() {
        StubBuilder builder = new StubBuilder("## 已知的用户长期记忆\n- [custom|0.90] x");
        MemoryPromptMiddleware middleware = new MemoryPromptMiddleware(builder);
        RuntimeContext context = context(MemoryScope.of("t1", "u1", "s1"), "怎么汇报", TRACE);

        middleware.onSystemPrompt(null, context, "提示词").block();
        middleware.onSystemPrompt(null, context, "提示词").block();
        middleware.onSystemPrompt(null, context, "提示词").block();

        assertEquals(1, builder.calls(),
                "onSystemPrompt 在推理循环里会被多次调用，不缓存会让一轮产生多份重复审计");
    }

    @Test
    @DisplayName("本轮无片段时也要缓存结论（空召回不再重复付召回成本）")
    void cachesNegativeResult() {
        StubBuilder builder = new StubBuilder((String) null);
        MemoryPromptMiddleware middleware = new MemoryPromptMiddleware(builder);
        RuntimeContext context = context(MemoryScope.of("t1", "u1", "s1"), "怎么汇报", TRACE);

        String first = middleware.onSystemPrompt(null, context, "提示词").block();
        String second = middleware.onSystemPrompt(null, context, "提示词").block();

        assertEquals("提示词", first);
        assertEquals("提示词", second, "无片段时提示词必须原样返回");
        assertEquals(1, builder.calls(), "「无片段」是有意义的负结果，不能与「还没算」混为一谈");
    }

    @Test
    @DisplayName("召回抛异常时原样返回提示词（记忆失败不阻断对话）")
    void swallowsRecallFailure() {
        MemoryPromptMiddleware middleware =
                new MemoryPromptMiddleware(new StubBuilder(new IllegalStateException("召回炸了")));

        String result = middleware.onSystemPrompt(null,
                context(MemoryScope.of("t1", "u1", "s1"), "怎么汇报", TRACE), "提示词").block();

        assertEquals("提示词", result, "记忆是增强能力，召回失败必须降级为「本轮没有记忆」");
    }

    @Test
    @DisplayName("未装配增强器时原样返回（模块缺失不算故障）")
    void noopWithoutBuilder() {
        MemoryPromptMiddleware middleware = new MemoryPromptMiddleware(null);

        assertEquals("提示词", middleware.onSystemPrompt(null,
                context(MemoryScope.of("t1", "u1", "s1"), "怎么汇报", TRACE), "提示词").block());
    }

    @Test
    @DisplayName("上下文为 null 时原样返回，不抛 NPE")
    void noopWithoutContext() {
        MemoryPromptMiddleware middleware = new MemoryPromptMiddleware(new StubBuilder("片段"));

        assertEquals("提示词", middleware.onSystemPrompt(null, null, "提示词").block());
    }

    @Test
    @DisplayName("隔离维度与查询文本经 RuntimeContext 传给增强器，链路 ID 取自媒体链路键")
    void passesBoundScopeQueryAndTraceId() {
        StubBuilder builder = new StubBuilder("片段");
        MemoryPromptMiddleware middleware = new MemoryPromptMiddleware(builder);
        MemoryScope scope = MemoryScope.of("t1", "u1", "s1");
        RuntimeContext context = context(scope, "怎么汇报", TRACE);

        middleware.onSystemPrompt(null, context, "提示词").block();

        assertEquals(List.of(scope), builder.seenScopes);
        assertEquals(List.of("怎么汇报"), builder.seenQueries);
        assertEquals(List.of(TRACE), builder.seenTraceIds,
                "traceId 取自 HarnessTraceMiddleware 的 RuntimeContext 键，否则召回 span 会无主");
    }

    @Test
    @DisplayName("片段缓存键在首次调用后即被写入（供后续轮次复用）")
    void writesFragmentCacheKey() {
        MemoryPromptMiddleware middleware = new MemoryPromptMiddleware(new StubBuilder("片段"));
        RuntimeContext context = context(MemoryScope.of("t1", "u1", "s1"), "怎么汇报", TRACE);

        middleware.onSystemPrompt(null, context, "提示词").block();

        assertEquals("片段", context.get(MemoryPromptMiddleware.CTX_FRAGMENT, String.class));
    }

    /** 造一个已绑定 scope/query/traceId 的运行时上下文（复现 AiAgentService 的绑定顺序）。 */
    private static RuntimeContext context(MemoryScope scope, String query, String traceId) {
        RuntimeContext context = RuntimeContext.empty();
        MemoryPromptMiddleware.bindContext(context, scope, query);
        HarnessTraceMiddleware.bindTraceId(context, traceId);
        return context;
    }

    /** 记录调用参数的桩：只替换「片段从哪来」，中间件的缓存/拼接/降级逻辑全是真实的。 */
    private static final class StubBuilder extends MemoryAwarePromptBuilder {

        private final String fragment;
        private final RuntimeException failure;
        private final AtomicInteger calls = new AtomicInteger();
        private final List<MemoryScope> seenScopes = new ArrayList<>();
        private final List<String> seenQueries = new ArrayList<>();
        private final List<String> seenTraceIds = new ArrayList<>();

        StubBuilder(String fragment) {
            this(fragment, null);
        }

        StubBuilder(RuntimeException failure) {
            this(null, failure);
        }

        private StubBuilder(String fragment, RuntimeException failure) {
            super(null, null, null, null, true);
            this.fragment = fragment;
            this.failure = failure;
        }

        @Override
        public String recallFragment(MemoryScope scope, String query, String traceId) {
            calls.incrementAndGet();
            seenScopes.add(scope);
            seenQueries.add(query);
            seenTraceIds.add(traceId);
            if (failure != null) {
                throw failure;
            }
            return fragment;
        }

        int calls() {
            return calls.get();
        }
    }
}
