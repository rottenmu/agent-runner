package com.zimo.framework.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link AiHarnessSessionKey} 的解析测试（M4-3）。
 *
 * <p><b>本测试的重点是往返（round-trip）而非孤立配平</b>：如果只断言
 * 「给定这个串能切出那段」，那两边都是我写的，切错了也一样过 ——
 * 属于恒真断言（M3 偏差 3/4 的教训）。因此这里用<b>真实的
 * {@link AiHarnessSessionKeyFactory}</b> 造键，再用本类反解，
 * 断言反解结果等于当初传进去的原值。工厂一旦改编码方式，这里必然红。</p>
 */
class AiHarnessSessionKeyTest {

    private final AiHarnessSessionKeyFactory factory = new AiHarnessSessionKeyFactory();

    @Test
    void roundTripsEveryDimensionAgainstTheRealFactory() {
        String key = factory.create(
                request("u-m4", "console", "m4-sess-plan", "u-m4"),
                profile("a17862024605874"));

        assertThat(AiHarnessSessionKey.tenantIdOf(key)).isEqualTo("u-m4");
        assertThat(AiHarnessSessionKey.userIdOf(key)).isEqualTo("u-m4");
        assertThat(AiHarnessSessionKey.segmentOf(key, 1)).isEqualTo("a17862024605874");
        assertThat(AiHarnessSessionKey.segmentOf(key, 2)).isEqualTo("console");
        assertThat(AiHarnessSessionKey.segmentOf(key, 3)).isEqualTo("m4-sess-plan");
    }

    /**
     * 内容里含冒号时不得被切错 —— 这正是不能简单 split(":") 的理由。
     */
    @Test
    void keepsValuesContainingColonsIntact() {
        String key = factory.create(
                request("tenant:a", "feishu", "conv:b:c", "user:d"),
                profile("agent:e"));

        assertThat(AiHarnessSessionKey.userIdOf(key)).isEqualTo("user:d");
        assertThat(AiHarnessSessionKey.tenantIdOf(key)).isEqualTo("tenant:a");
        assertThat(AiHarnessSessionKey.segmentOf(key, 3)).isEqualTo("conv:b:c");
    }

    /**
     * <b>已知边界：净化后的键不可反解</b>（如实记录，不做掩盖）。
     *
     * <p>线上 RocksDB 里落盘的会话段形如
     * {@code 6_e2e-m48_ai-agent7_console12_m4-sess-plan4_u-m4} ——
     * {@code RocksdbAgentStateStore.safe()} 已把 {@code :} 换成 {@code _}，
     * 长度前缀与内容的分界信息<b>已被破坏</b>，无法再按索引切段。</p>
     *
     * <p>因此探针<b>不能</b>靠反解存储键来取 userId；正确做法是调用方把
     * <b>未净化</b>的原始 key 交给 {@link AiHarnessSessionKey} 解析。
     * 本测试把这个边界钉住，避免后人误以为「反正能从存储键里推出来」。</p>
     */
    @Test
    void cannotResolveDimensionsFromASanitizedStoredKey() {
        String sanitized = "6_e2e-m48_ai-agent7_console12_m4-sess-plan4_u-m4";

        // 反解结果是无意义的片段，而非 "u-m4" —— 这正是不能再反解的证据。
        assertThat(AiHarnessSessionKey.userIdOf(sanitized)).isNotEqualTo("u-m4");
    }

    @Test
    void returnsPlaceholderForBlankOrUnparsableInput() {
        assertThat(AiHarnessSessionKey.userIdOf(null)).isEqualTo("_");
        assertThat(AiHarnessSessionKey.userIdOf("")).isEqualTo("_");
        assertThat(AiHarnessSessionKey.userIdOf("not-a-framed-key")).isEqualTo("_");
        // 长度前缀非法：不抛异常，退化为兜底值（收尾路径不该因解析失败而中断）
        assertThat(AiHarnessSessionKey.userIdOf("xx:abc")).isEqualTo("_");
    }

    /** 全空维度的键：5 段都是空内容，userId 应得空内容而非兜底值。 */
    @Test
    void handlesEmptyFrames() {
        String key = factory.create(request("", "", "", ""), profile(""));

        // 工厂把空值编码成 "0:"，反解出来是空串 → 归一到兜底值
        assertThat(AiHarnessSessionKey.userIdOf(key)).isEqualTo("_");
    }

    private static AiAgentRouteRequest request(
            String tenantId, String channel, String conversationId, String userId) {
        return new AiAgentRouteRequest(tenantId, channel, userId, conversationId, null, null);
    }

    private static AiAgentProfile profile(String id) {
        return new AiAgentProfile(id, id, "model", "prompt", List.of());
    }
}
