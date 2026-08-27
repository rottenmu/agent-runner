package com.zimo.starter.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.starter.ai.AiAgentReply;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * around-middleware 瀑布单测：链顺序 / 短路 / 后置包裹 / 消息改写。
 */
class AiAgentMiddlewareChainTest {

    private static final AiRequestContext CTX =
            AiRequestContext.of("s1", "a1", null);

    /** 记录执行序。 */
    private static final List<String> ORDER = new ArrayList<>();

    private static AiAgentMiddleware record(String name) {
        return (context, message, next) -> {
            ORDER.add(name + ":pre");
            AiMiddlewareResult r = next.proceed(context, message);
            ORDER.add(name + ":post");
            return r;
        };
    }

    @Test
    void chainRunsInOrderWithPostWrap() {
        ORDER.clear();
        MiddlewareChain terminal = (ctx, msg) -> {
            ORDER.add("terminal");
            return AiMiddlewareResult.reply(new AiAgentReply("a1", "done"));
        };
        MiddlewareChain chain = AiAgentMiddleware.buildChain(
                List.of(record("A"), record("B")), terminal);
        AiMiddlewareResult result = chain.proceed(CTX, "hi");

        assertThat(result.kind()).isEqualTo(AiMiddlewareResult.Kind.REPLY);
        assertThat(result.reply().content()).isEqualTo("done");
        // A:pre → B:pre → terminal → B:post → A:post（瀑布后置逆序）
        assertThat(ORDER).containsExactly(
                "A:pre", "B:pre", "terminal", "B:post", "A:post");
    }

    @Test
    void denyShortCircuitsWithoutCallingNext() {
        ORDER.clear();
        AiAgentMiddleware denier = (context, message, next) -> {
            ORDER.add("deny");
            return AiMiddlewareResult.deny("blocked by policy");
        };
        MiddlewareChain terminal = (ctx, msg) -> {
            ORDER.add("terminal");
            return AiMiddlewareResult.reply(new AiAgentReply("a1", "done"));
        };
        MiddlewareChain chain = AiAgentMiddleware.buildChain(List.of(denier), terminal);
        AiMiddlewareResult result = chain.proceed(CTX, "hi");

        assertThat(result.kind()).isEqualTo(AiMiddlewareResult.Kind.DENY);
        assertThat(result.message()).isEqualTo("blocked by policy");
        assertThat(ORDER).containsExactly("deny");
    }

    @Test
    void replyShortCircuitsWithoutCallingNext() {
        AiAgentMiddleware replier = (context, message, next) ->
                AiMiddlewareResult.reply(new AiAgentReply("a1", "direct-answer"));
        MiddlewareChain terminal = (ctx, msg) ->
                AiMiddlewareResult.reply(new AiAgentReply("a1", "model-answer"));
        MiddlewareChain chain = AiAgentMiddleware.buildChain(List.of(replier), terminal);

        AiMiddlewareResult result = chain.proceed(CTX, "hi");
        assertThat(result.reply().content()).isEqualTo("direct-answer");
    }

    @Test
    void rewritePropagatesToNextAndTerminal() {
        List<String> seen = new ArrayList<>();
        AiAgentMiddleware rewriter = (context, message, next) ->
                next.proceed(context, message + " [rewritten]");
        MiddlewareChain terminal = (ctx, msg) -> {
            seen.add(msg);
            return AiMiddlewareResult.deny("stop");
        };
        MiddlewareChain chain = AiAgentMiddleware.buildChain(List.of(rewriter), terminal);
        chain.proceed(CTX, "hi");
        assertThat(seen).containsExactly("hi [rewritten]");
    }

    @Test
    void emptyMiddlewareFallsThroughToTerminal() {
        MiddlewareChain terminal = (ctx, msg) ->
                AiMiddlewareResult.reply(new AiAgentReply("a1", "fallback"));
        MiddlewareChain chain = AiAgentMiddleware.buildChain(List.of(), terminal);
        AiMiddlewareResult result = chain.proceed(CTX, "hi");
        assertThat(result.reply().content()).isEqualTo("fallback");
    }
}
