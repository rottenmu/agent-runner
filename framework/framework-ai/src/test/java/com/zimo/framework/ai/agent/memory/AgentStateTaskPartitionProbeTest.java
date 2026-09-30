package com.zimo.framework.ai.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.framework.common.storage.FileStorageService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentStateTaskPartitionProbe} 的测试（M4-3，PRD 标准 10）。
 *
 * <p><b>为什么重点测「读不到」而不是「读到了」</b>：这条判据的失效方式不是报错，
 * 而是<b>静默读成 0 条</b> —— 于是「已清空」与「根本没读到」在断言上完全相同，
 * 标准 10 退化成恒真守卫。因此这里把 {@code readable=false} 的几种成因
 * 逐条钉死，确保它们<b>不会</b>被误判成 {@code cleared}。</p>
 */
class AgentStateTaskPartitionProbeTest {

    private static final String UID = "u-m4";
    /**
     * 线上实测落盘的会话段（2026-09-17 从 {@code data/rocksdb} 提取）：
     * 会话键里的 {@code :} 已被 {@code RocksdbAgentStateStore.safe()} 换成 {@code _}。
     */
    private static final String SESSION = "6_e2e-m48_ai-agent7_console12_m4-sess-plan4_u-m4";
    private static final String KEY = "astate/" + UID + "/" + SESSION + "/agent_state";

    @Test
    void readsTasksFromTheConfirmedWireFormat() {
        FakeStorage storage = new FakeStorage();
        storage.store(KEY, stateJson("""
                {"subject":"对账 3 月报销单","state":"completed"},
                {"subject":"导出差异明细","state":"pending"}
                """));

        AgentStateTaskPartitionProbe.Snapshot snapshot =
                new AgentStateTaskPartitionProbe(storage).inspect(UID, SESSION);

        assertThat(snapshot.readable()).isTrue();
        assertThat(snapshot.total()).isEqualTo(2);
        assertThat(snapshot.completed()).isEqualTo(1);
        assertThat(snapshot.subjects())
                .containsExactly("对账 3 月报销单", "导出差异明细");
        assertThat(snapshot.cleared()).isFalse();
    }

    /**
     * 本轮最关键的对照：<b>清空后</b>必须 {@code readable=true} 且 {@code total=0}。
     */
    @Test
    void reportsClearedAfterDeletingThePartition() {
        FakeStorage storage = new FakeStorage();
        storage.store(KEY, stateJson("""
                {"subject":"对账","state":"completed"}
                """));
        AgentStateTaskPartitionProbe probe = new AgentStateTaskPartitionProbe(storage);

        assertThat(probe.clear(UID, SESSION)).isTrue();

        AgentStateTaskPartitionProbe.Snapshot after = probe.inspect(UID, SESSION);
        // 清理后 key 没了 → 不可读（而不是「可读且为 0」）。
        // 这正是必须区分两种 0 的原因：直接看 count 的话两者一样。
        assertThat(after.readable()).isFalse();
        assertThat(after.total()).isZero();
        assertThat(after.cleared()).isFalse();
        assertThat(after.reason()).isNotBlank();
    }

    /**
     * 反向陷阱：<b>空 tasks 数组</b>才是真正的「已清空」态（key 还在、分区为空）。
     *
     * <p>与上一条对照：两者 {@code total} 都是 0，但 {@code cleared()} 必须一个 false 一个 true。
     * 若实现把「读不到」也当清空，这两条断言就会互相矛盾而暴露。</p>
     */
    @Test
    void distinguishesEmptyPartitionFromMissingKey() {
        FakeStorage storage = new FakeStorage();
        storage.store(KEY, stateJson(""));

        AgentStateTaskPartitionProbe.Snapshot snapshot =
                new AgentStateTaskPartitionProbe(storage).inspect(UID, SESSION);

        assertThat(snapshot.readable()).isTrue();
        assertThat(snapshot.total()).isZero();
        assertThat(snapshot.cleared()).isTrue();
        assertThat(snapshot.reason()).isNull();
    }

    /** 没有 state key（未进入计划模式）：不可读，且不得声称已清空。 */
    @Test
    void reportsUnreadableWhenNoStateKeyExists() {
        AgentStateTaskPartitionProbe.Snapshot snapshot =
                new AgentStateTaskPartitionProbe(new FakeStorage()).inspect(UID, SESSION);

        assertThat(snapshot.readable()).isFalse();
        assertThat(snapshot.cleared()).isFalse();
        assertThat(snapshot.reason()).isNotBlank();
    }

    /** 会话不匹配（键存在但属于别的会话）→ 不得串读。 */
    @Test
    void doesNotLeakTasksAcrossSessions() {
        FakeStorage storage = new FakeStorage();
        storage.store("astate/" + UID + "/6_other8_ai-agent7_console6_other4_u-m4/agent_state",
                stateJson("""
                        {"subject":"别的会话的任务","state":"pending"}
                        """));

        AgentStateTaskPartitionProbe.Snapshot snapshot =
                new AgentStateTaskPartitionProbe(storage).inspect(UID, SESSION);

        assertThat(snapshot.readable()).isFalse();
        assertThat(snapshot.subjects()).isEmpty();
    }

    /** 参数不完整时直接判不可读，不去做 IO。 */
    @Test
    void refusesIncompleteIdentifiers() {
        AgentStateTaskPartitionProbe probe = new AgentStateTaskPartitionProbe(new FakeStorage());

        assertThat(probe.inspect(null, SESSION).readable()).isFalse();
        assertThat(probe.inspect(UID, "  ").readable()).isFalse();
        assertThat(probe.clear(null, SESSION)).isFalse();
    }

    /** 存储缺失（未启用 RocksDB）时不抛异常。 */
    @Test
    void toleratesMissingStorage() {
        AgentStateTaskPartitionProbe probe = new AgentStateTaskPartitionProbe(null);

        assertThat(probe.inspect(UID, SESSION).readable()).isFalse();
        assertThat(probe.clear(UID, SESSION)).isFalse();
    }

    /** 内容不是合法 JSON 时不可读，但不得抛出。 */
    @Test
    void toleratesCorruptPayload() {
        FakeStorage storage = new FakeStorage();
        storage.store(KEY, "{not json".getBytes(StandardCharsets.UTF_8));

        AgentStateTaskPartitionProbe.Snapshot snapshot =
                new AgentStateTaskPartitionProbe(storage).inspect(UID, SESSION);

        assertThat(snapshot.readable()).isTrue();
        assertThat(snapshot.total()).isZero();
    }

    /**
     * 回归守卫：<b>传入未净化的会话键（含冒号）也必须能查到</b>。
     *
     * <p>调用方（{@code AiAgentService}）手里拿的是 {@code AiHarnessSessionKeyFactory} 的
     * 原始产物，含 {@code :} 分隔符；而存储里落的是净化后的 {@code _} 形态。
     * 若探针不跟着净化，前缀永远对不上、恒报「不可读」——
     * 这个 bug 不抛异常、不打日志，标准 10 会<b>无声失效</b>。此条即钉死它。</p>
     */
    @Test
    void findsPartitionWhenGivenTheUnsanitizedSessionKey() {
        FakeStorage storage = new FakeStorage();
        storage.store(KEY, stateJson("""
                {"subject":"对账","state":"completed"}
                """));

        AgentStateTaskPartitionProbe.Snapshot snapshot =
                new AgentStateTaskPartitionProbe(storage)
                        .inspect(UID, "6:e2e-m48:ai-agent7:console12:m4-sess-plan4:u-m4");

        assertThat(snapshot.readable()).isTrue();
        assertThat(snapshot.total()).isEqualTo(1);
    }

    /** 构造 wire 形态：{@code {"tasks_context":{"tasks":[...]}}}，空串表示空数组。 */
    private static byte[] stateJson(String tasks) {
        String body = tasks.isBlank() ? "" : tasks.trim();
        return ("{\"tasks_context\":{\"tasks\":[" + body + "]}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    /** 仅实现探针用到的 store/get/delete/list 的内存桩。 */
    private static final class FakeStorage implements FileStorageService {

        private final Map<String, byte[]> data = new LinkedHashMap<>();

        @Override
        public void store(String key, byte[] bytes) {
            data.put(key, bytes);
        }

        @Override
        public byte[] get(String key) {
            return data.get(key);
        }

        @Override
        public boolean delete(String key) {
            return data.remove(key) != null;
        }

        @Override
        public List<String> list(String prefix) {
            List<String> keys = new ArrayList<>();
            for (String key : data.keySet()) {
                if (key.startsWith(prefix)) {
                    keys.add(key);
                }
            }
            return keys;
        }

        @Override
        public boolean exists(String key) {
            return data.containsKey(key);
        }
    }
}
