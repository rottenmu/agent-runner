package com.zimo.framework.ai.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zimo.framework.common.storage.FileStorageService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 验证短期会话记忆按最后活动时间清理，并保护仍在使用的会话状态。 */
class RocksdbAgentStateStoreRetentionTest {

    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");
    private static final int RETENTION_DAYS = 7;

    @Test
    void deletesOnlySessionsOlderThanTheConfiguredRetention() {
        FakeStorage storage = new FakeStorage();
        storeSession(storage, "agent-a", "expired", NOW.minus(Duration.ofDays(7)).minusMillis(1));
        storeSession(storage, "agent-a", "boundary", NOW.minus(Duration.ofDays(7)));
        storeSession(storage, "agent-a", "recent", NOW.minus(Duration.ofDays(2)));
        storeSession(storage, "agent-b", "expired", NOW.minus(Duration.ofDays(2)));

        int deleted = new RocksdbAgentStateStore(storage).cleanupExpiredSessions(NOW, RETENTION_DAYS);

        assertThat(deleted).isEqualTo(1);
        assertSessionExists(storage, "agent-a", "expired", false);
        assertSessionExists(storage, "agent-a", "boundary", true);
        assertSessionExists(storage, "agent-a", "recent", true);
        assertSessionExists(storage, "agent-b", "expired", true);
    }

    @Test
    void preservesLegacySessionAndAddsActivityMetadata() {
        FakeStorage storage = new FakeStorage();
        storage.store(stateKey("agent-a", "legacy", "agent_state"), bytes("state"));

        int deleted = new RocksdbAgentStateStore(storage).cleanupExpiredSessions(NOW, RETENTION_DAYS);

        assertThat(deleted).isZero();
        assertThat(storage.exists(stateKey("agent-a", "legacy", "agent_state"))).isTrue();
        assertThat(storage.get(activityKey("agent-a", "legacy")))
                .isEqualTo(bytes(Long.toString(NOW.toEpochMilli())));
    }

    @Test
    void resetsCorruptActivityMetadataInsteadOfDeletingSession() {
        FakeStorage storage = new FakeStorage();
        storage.store(stateKey("agent-a", "corrupt", "agent_state"), bytes("state"));
        storage.store(activityKey("agent-a", "corrupt"), bytes("not-a-timestamp"));

        int deleted = new RocksdbAgentStateStore(storage).cleanupExpiredSessions(NOW, RETENTION_DAYS);

        assertThat(deleted).isZero();
        assertThat(storage.exists(stateKey("agent-a", "corrupt", "agent_state"))).isTrue();
        assertThat(storage.get(activityKey("agent-a", "corrupt")))
                .isEqualTo(bytes(Long.toString(NOW.toEpochMilli())));
    }

    @Test
    void rejectsMissingCurrentTimeAndRetentionOutsideOneTo365Days() {
        RocksdbAgentStateStore store = new RocksdbAgentStateStore(new FakeStorage());

        assertThatNullPointerException().isThrownBy(() -> store.cleanupExpiredSessions(null, RETENTION_DAYS));
        assertThatThrownBy(() -> store.cleanupExpiredSessions(NOW, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.cleanupExpiredSessions(NOW, 366))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void storeSession(FakeStorage storage, String agentId, String sessionId, Instant lastActivity) {
        storage.store(stateKey(agentId, sessionId, "agent_state"), bytes("state"));
        storage.store(stateKey(agentId, sessionId, "history"), bytes("history"));
        storage.store(activityKey(agentId, sessionId), bytes(Long.toString(lastActivity.toEpochMilli())));
    }

    private static void assertSessionExists(
            FakeStorage storage, String agentId, String sessionId, boolean expected) {
        assertThat(storage.exists(stateKey(agentId, sessionId, "agent_state"))).isEqualTo(expected);
        assertThat(storage.exists(stateKey(agentId, sessionId, "history"))).isEqualTo(expected);
        assertThat(storage.exists(activityKey(agentId, sessionId))).isEqualTo(expected);
    }

    private static String stateKey(String agentId, String sessionId, String key) {
        return "astate/" + agentId + "/" + sessionId + "/" + key;
    }

    private static String activityKey(String agentId, String sessionId) {
        return "astate-meta/" + agentId + "/" + sessionId;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class FakeStorage implements FileStorageService {

        private final Map<String, byte[]> data = new LinkedHashMap<>();

        @Override
        public void store(String key, byte[] value) {
            data.put(key, value);
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
            data.keySet().stream().filter(key -> key.startsWith(prefix)).forEach(keys::add);
            return keys;
        }

        @Override
        public boolean exists(String key) {
            return data.containsKey(key);
        }
    }
}
