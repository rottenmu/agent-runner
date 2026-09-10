package com.zimo.framework.ai.agent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.common.storage.FileStorageService;
import io.agentscope.harness.agent.filesystem.remote.store.BaseStore;
import io.agentscope.harness.agent.filesystem.remote.store.StoreItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 RocksDB 的 AgentScope 持久化存储实现。
 *
 * <p>将 {@link FileStorageService}（RocksDB 嵌入式 KV 存储）适配为 AgentScope Harness
 * 的 {@link BaseStore}，使智能体工作区、长期记忆（MEMORY.md / memory/*.md）和会话日志
 * 全部持久化到 RocksDB，而非本地文件系统。</p>
 *
 * <p>复合键格式：{@code {namespace/...}::{key}}，值以 JSON 序列化存储，版本号用于
 * 乐观锁（putIfVersion）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
public class RocksdbBaseStore implements BaseStore {

    private static final Logger log = LoggerFactory.getLogger(RocksdbBaseStore.class);
    private static final String KEY_SEPARATOR = "/";
    private static final String VERSION_FIELD = "__version";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final FileStorageService storage;
    private final ObjectMapper objectMapper;

    /**
     * 创建 RocksDB 存储。
     *
     * @param storage RocksDB 文件存储服务，不允许为空
     */
    public RocksdbBaseStore(FileStorageService storage) {
        this.storage = Objects.requireNonNull(storage, "storage must not be null");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public StoreItem get(List<String> namespace, String key) {
        String compound = compoundKey(namespace, key);
        byte[] raw = storage.get(compound);
        if (raw == null) {
            return null;
        }
        return deserialize(key, raw);
    }

    @Override
    public void put(List<String> namespace, String key, Map<String, Object> value) {
        String compound = compoundKey(namespace, key);
        Map<String, Object> withVersion = new HashMap<>(value);
        withVersion.put(VERSION_FIELD, System.currentTimeMillis());
        storage.store(compound, serialize(withVersion));
        log.debug("RocksDB store put: {} (namespace={})", key, namespace);
    }

    @Override
    public boolean putIfVersion(List<String> namespace, String key, Map<String, Object> value, long version) {
        String compound = compoundKey(namespace, key);
        byte[] raw = storage.get(compound);
        if (raw != null) {
            StoreItem existing = deserialize(key, raw);
            if (existing.version() != version) {
                return false;
            }
        }
        put(namespace, key, value);
        return true;
    }

    @Override
    public List<StoreItem> search(List<String> namespace, int offset, int limit) {
        String prefix = namespacePrefix(namespace);
        List<StoreItem> items = new ArrayList<>();
        for (String compound : storage.list(prefix)) {
            String key = compound.substring(prefix.length());
            byte[] raw = storage.get(compound);
            if (raw != null) {
                items.add(deserialize(key, raw));
            }
        }
        items.sort(Comparator.comparingLong(StoreItem::version));
        int from = Math.min(Math.max(offset, 0), items.size());
        int to = limit > 0 ? Math.min(from + limit, items.size()) : items.size();
        return items.subList(from, to);
    }

    @Override
    public void delete(List<String> namespace, String key) {
        storage.delete(compoundKey(namespace, key));
        log.debug("RocksDB store delete: {} (namespace={})", key, namespace);
    }

    private StoreItem deserialize(String key, byte[] raw) {
        try {
            Map<String, Object> value = objectMapper.readValue(raw, MAP_TYPE);
            long version = value.containsKey(VERSION_FIELD)
                    ? ((Number) value.remove(VERSION_FIELD)).longValue()
                    : 0L;
            return new StoreItem(key, value, version);
        } catch (Exception exception) {
            throw new IllegalStateException("RocksDB 存储值反序列化失败: " + key, exception);
        }
    }

    private byte[] serialize(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception exception) {
            throw new IllegalStateException("RocksDB 存储值序列化失败", exception);
        }
    }

    private String compoundKey(List<String> namespace, String key) {
        return namespacePrefix(namespace) + sanitizeKey(key);
    }

    private String namespacePrefix(List<String> namespace) {
        // List 判空用 CollUtil（StrUtil 仅适用于 String）
        if (cn.hutool.core.collection.CollUtil.isEmpty(namespace)) {
            return "";
        }
        List<String> safe = new ArrayList<>();
        for (String part : namespace) {
            safe.add(sanitizeKey(part));
        }
        return String.join("/", safe) + KEY_SEPARATOR;
    }

    /** 清洗 AgentScope 内部键中的非法字符（冒号等），避免 Windows 路径校验失败。 */
    private String sanitizeKey(String value) {
        if (StrUtil.isBlank(value)) {
            return "_";
        }
        return value.replace(":", "_");
    }
}
