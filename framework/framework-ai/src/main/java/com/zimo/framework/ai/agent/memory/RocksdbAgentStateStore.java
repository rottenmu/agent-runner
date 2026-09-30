package com.zimo.framework.ai.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.core.util.StrUtil;
import com.zimo.framework.common.storage.FileStorageService;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 RocksDB 的分布式 AgentStateStore。
 *
 * <p>AgentScope 要求 {@code RemoteFilesystemSpec} 配合分布式状态存储使用；
 * 该实现将智能体会话状态（消息、记忆等）持久化到 RocksDB，状态键格式为
 * {@code astate/{agentId}/{sessionId}/{key}}，活动时间单独保存在 {@code astate-meta} 前缀下。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
public class RocksdbAgentStateStore implements AgentStateStore {

    private static final Logger log = LoggerFactory.getLogger(RocksdbAgentStateStore.class);
    private static final String PREFIX = "astate";
    /** 会话活动时间元数据使用独立前缀，避免混入 AgentScope 状态键。 */
    private static final String ACTIVITY_PREFIX = "astate-meta";
    private static final String SEP = "/";

    private final FileStorageService storage;
    private final ObjectMapper objectMapper;

    public RocksdbAgentStateStore(FileStorageService storage) {
        this.storage = storage;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void save(String agentId, String sessionId, String key, State state) {
        try {
            persist(agentId, sessionId, key, objectMapper.writeValueAsBytes(state));
        } catch (Exception e) {
            log.warn("AgentStateStore save failed: {}/{}/{}", agentId, sessionId, key, e);
        }
    }

    @Override
    public void save(String agentId, String sessionId, String key, List<? extends State> states) {
        try {
            persist(agentId, sessionId, key, objectMapper.writeValueAsBytes(states));
        } catch (Exception e) {
            log.warn("AgentStateStore save(list) failed: {}/{}/{}", agentId, sessionId, key, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends State> Optional<T> get(String agentId, String sessionId, String key, Class<T> type) {
        byte[] raw;
        synchronized (storage) {
            raw = storage.get(key(agentId, sessionId, key));
            if (raw != null) {
                updateLastActivity(agentId, sessionId, System.currentTimeMillis());
            }
        }
        if (raw == null) {
            return Optional.empty();
        }
        try {
            Object value = objectMapper.readValue(raw, Object.class);
            if (value instanceof List<?> list && !list.isEmpty()) {
                return Optional.of(objectMapper.convertValue(list.get(0), type));
            }
            return Optional.of(objectMapper.convertValue(value, type));
        } catch (Exception e) {
            log.warn("AgentStateStore get failed: {}/{}/{}", agentId, sessionId, key, e);
            return Optional.empty();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends State> List<T> getList(String agentId, String sessionId, String key, Class<T> type) {
        byte[] raw;
        synchronized (storage) {
            raw = storage.get(key(agentId, sessionId, key));
            if (raw != null) {
                updateLastActivity(agentId, sessionId, System.currentTimeMillis());
            }
        }
        if (raw == null) {
            return List.of();
        }
        try {
            Object value = objectMapper.readValue(raw, Object.class);
            if (value instanceof List<?> list) {
                List<T> result = new ArrayList<>();
                for (Object item : list) {
                    result.add(objectMapper.convertValue(item, type));
                }
                return result;
            }
            T single = objectMapper.convertValue(value, type);
            return single == null ? List.of() : List.of(single);
        } catch (Exception e) {
            log.warn("AgentStateStore getList failed: {}/{}/{}", agentId, sessionId, key, e);
            return List.of();
        }
    }

    @Override
    public boolean exists(String agentId, String sessionId) {
        String prefix = prefix(agentId, sessionId);
        synchronized (storage) {
            boolean exists = !storage.list(prefix + SEP).isEmpty();
            if (exists) {
                updateLastActivity(agentId, sessionId, System.currentTimeMillis());
            }
            return exists;
        }
    }

    @Override
    public void delete(String agentId, String sessionId) {
        synchronized (storage) {
            deleteSessionState(agentId, sessionId);
            storage.delete(activityKey(agentId, sessionId));
        }
    }

    @Override
    public void delete(String agentId, String sessionId, String key) {
        synchronized (storage) {
            storage.delete(key(agentId, sessionId, key));
        }
    }

    @Override
    public Set<String> listSessionIds(String agentId) {
        Set<String> ids = new TreeSet<>();
        for (String key : storage.list(PREFIX + SEP + safe(agentId) + SEP)) {
            String rest = key.substring((PREFIX + SEP + safe(agentId) + SEP).length());
            int slash = rest.indexOf(SEP);
            if (slash > 0) {
                ids.add(rest.substring(0, slash));
            }
        }
        return ids;
    }

    /**
     * 清理超过指定闲置期限的会话状态，并为历史状态补齐首次活动时间。
     *
     * @param now 本次清理使用的当前时间，不允许为空
     * @param retentionDays 会话保留天数，范围为 1 至 365
     * @return 本次清理的过期会话数量；无过期会话时返回 0
     * @throws IllegalArgumentException 当前时间为空或保留天数越界时抛出
     */
    public int cleanupExpiredSessions(Instant now, int retentionDays) {
        Objects.requireNonNull(now, "now must not be null");
        if (retentionDays < 1 || retentionDays > 365) {
            throw new IllegalArgumentException("短期会话保留天数必须在 1 至 365 之间");
        }
        long nowMillis = now.toEpochMilli();
        long cutoffMillis = now.minusSeconds(retentionDays * 24L * 60L * 60L).toEpochMilli();
        synchronized (storage) {
            int deletedSessions = 0;
            for (SessionRef session : listStoredSessions()) {
                if (cleanupSessionIfExpired(session, nowMillis, cutoffMillis)) {
                    deletedSessions++;
                }
            }
            return deletedSessions;
        }
    }

    private void persist(String agentId, String sessionId, String key, byte[] serialized) {
        synchronized (storage) {
            storage.store(key(agentId, sessionId, key), serialized);
            updateLastActivity(agentId, sessionId, System.currentTimeMillis());
        }
    }

    private void updateLastActivity(String agentId, String sessionId, long lastActivityMillis) {
        try {
            storage.store(activityKey(agentId, sessionId),
                    Long.toString(lastActivityMillis).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("AgentStateStore activity metadata update failed: {}/{}", agentId, sessionId, e);
        }
    }

    /** 按状态键与元数据键分别列举候选，兼容尚未写入元数据的历史会话。 */
    private Set<SessionRef> listStoredSessions() {
        Set<SessionRef> sessions = new LinkedHashSet<>();
        for (String key : storage.list(PREFIX + SEP)) {
            SessionRef session = parseStateSession(key);
            if (session != null) {
                sessions.add(session);
            }
        }
        for (String key : storage.list(ACTIVITY_PREFIX + SEP)) {
            SessionRef session = parseActivitySession(key);
            if (session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    /** 在共享存储锁内复查活动时间，再删除完整会话前缀，避免与读写竞态。 */
    private boolean cleanupSessionIfExpired(SessionRef session, long nowMillis, long cutoffMillis) {
        String metadataKey = activityKey(session.agentId(), session.sessionId());
        byte[] rawActivity = storage.get(metadataKey);
        if (rawActivity == null) {
            updateLastActivity(session.agentId(), session.sessionId(), nowMillis);
            return false;
        }
        long lastActivityMillis = parseLastActivity(rawActivity, session, nowMillis);
        if (lastActivityMillis >= cutoffMillis) {
            return false;
        }
        deleteSessionState(session.agentId(), session.sessionId());
        storage.delete(metadataKey);
        return true;
    }

    /** 解析活动时间；损坏或非数字元数据重置为当前时间，避免误删状态。 */
    private long parseLastActivity(byte[] rawActivity, SessionRef session, long nowMillis) {
        try {
            return Long.parseLong(new String(rawActivity, StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            log.warn("AgentStateStore activity metadata is invalid; reset it: {}/{}",
                    session.agentId(), session.sessionId());
            updateLastActivity(session.agentId(), session.sessionId(), nowMillis);
            return nowMillis;
        }
    }

    /** 删除会话下所有 AgentState 键，调用方须持有共享存储锁。 */
    private void deleteSessionState(String agentId, String sessionId) {
        String sessionPrefix = prefix(agentId, sessionId) + SEP;
        for (String key : storage.list(sessionPrefix)) {
            storage.delete(key);
        }
    }

    /** 从 AgentState 键中提取已净化的 agentId 与 sessionId。 */
    private SessionRef parseStateSession(String key) {
        String rest = key.substring((PREFIX + SEP).length());
        int agentSeparator = rest.indexOf(SEP);
        int sessionSeparator = rest.indexOf(SEP, agentSeparator + 1);
        if (agentSeparator <= 0 || sessionSeparator <= agentSeparator + 1) {
            return null;
        }
        return new SessionRef(rest.substring(0, agentSeparator),
                rest.substring(agentSeparator + 1, sessionSeparator));
    }

    /** 从 metadata 键中提取已净化的 agentId 与 sessionId。 */
    private SessionRef parseActivitySession(String key) {
        String rest = key.substring((ACTIVITY_PREFIX + SEP).length());
        int separator = rest.indexOf(SEP);
        if (separator <= 0 || separator == rest.length() - 1 || rest.indexOf(SEP, separator + 1) >= 0) {
            return null;
        }
        return new SessionRef(rest.substring(0, separator), rest.substring(separator + 1));
    }

    private String key(String agentId, String sessionId, String key) {
        return prefix(agentId, sessionId) + SEP + safe(key);
    }

    private String prefix(String agentId, String sessionId) {
        return PREFIX + SEP + safe(agentId) + SEP + safe(sessionId);
    }

    private String activityKey(String agentId, String sessionId) {
        return ACTIVITY_PREFIX + SEP + safe(agentId) + SEP + safe(sessionId);
    }

    private String safe(String value) {
        if (StrUtil.isBlank(value)) {
            return "_";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private record SessionRef(String agentId, String sessionId) {
    }
}
