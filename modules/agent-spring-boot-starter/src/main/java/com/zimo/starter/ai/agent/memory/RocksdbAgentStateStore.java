package com.zimo.starter.ai.agent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.core.util.StrUtil;
import com.zimo.framework.common.storage.FileStorageService;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 RocksDB 的分布式 AgentStateStore。
 *
 * <p>AgentScope 要求 {@code RemoteFilesystemSpec} 配合分布式状态存储使用；
 * 该实现将智能体会话状态（消息、记忆等）持久化到 RocksDB，键格式：
 * {@code astate/{agentId}/{sessionId}/{key}}。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
public class RocksdbAgentStateStore implements AgentStateStore {

    private static final Logger log = LoggerFactory.getLogger(RocksdbAgentStateStore.class);
    private static final String PREFIX = "astate";
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
            storage.store(key(agentId, sessionId, key), objectMapper.writeValueAsBytes(state));
        } catch (Exception e) {
            log.warn("AgentStateStore save failed: {}/{}/{}", agentId, sessionId, key, e);
        }
    }

    @Override
    public void save(String agentId, String sessionId, String key, List<? extends State> states) {
        try {
            storage.store(key(agentId, sessionId, key), objectMapper.writeValueAsBytes(states));
        } catch (Exception e) {
            log.warn("AgentStateStore save(list) failed: {}/{}/{}", agentId, sessionId, key, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends State> Optional<T> get(String agentId, String sessionId, String key, Class<T> type) {
        byte[] raw = storage.get(key(agentId, sessionId, key));
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
        byte[] raw = storage.get(key(agentId, sessionId, key));
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
        return !storage.list(prefix + SEP).isEmpty();
    }

    @Override
    public void delete(String agentId, String sessionId) {
        String prefix = prefix(agentId, sessionId);
        for (String key : storage.list(prefix + SEP)) {
            storage.delete(key);
        }
    }

    @Override
    public void delete(String agentId, String sessionId, String key) {
        storage.delete(key(agentId, sessionId, key));
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

    private String key(String agentId, String sessionId, String key) {
        return prefix(agentId, sessionId) + SEP + safe(key);
    }

    private String prefix(String agentId, String sessionId) {
        return PREFIX + SEP + safe(agentId) + SEP + safe(sessionId);
    }

    private String safe(String value) {
        if (StrUtil.isBlank(value)) {
            return "_";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
