package com.zimo.framework.ai.agent;

import com.zimo.framework.ai.AiAgentProperties;
import cn.hutool.core.util.StrUtil;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * 按租户、智能体和配置指纹管理 HarnessAgent 实例。
 *
 * <p>同一缓存键并发创建时仅执行一次工厂调用；配置变化形成新键，显式失效和 LRU 淘汰停止旧实例复用。
 * 正在执行请求的实例会在租约释放后关闭，工厂异常不会进入缓存。</p>
 *
 * @author Codex
 * @since 2026-07-25
 */
public class AiHarnessAgentRegistry implements AutoCloseable {

    private final AiHarnessAgentFactory factory;
    private final AiAgentProperties properties;
    private final int capacity;
    private final ConcurrentHashMap<AiHarnessAgentKey, AgentEntry> instances =
            new ConcurrentHashMap<>();
    private final LinkedHashMap<AiHarnessAgentKey, Boolean> accessOrder =
            new LinkedHashMap<>(16, 0.75F, true);
    private final Object accessLock = new Object();
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private volatile boolean closed;

    /**
     * 创建 HarnessAgent 注册表。
     *
     * @param factory HarnessAgent 工厂，不允许为空
     * @param properties starter 配置，不允许为空；容量非正数时使用默认值 128
     */
    public AiHarnessAgentRegistry(
            AiHarnessAgentFactory factory,
            AiAgentProperties properties) {
        this.factory = Objects.requireNonNull(factory, "factory must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.capacity = properties.getHarnessRegistryCapacity();
    }

    /**
     * 获取或创建与 profile 完全匹配的 HarnessAgent。
     *
     * <p>该方法适用于短暂获取或诊断；执行模型调用时应使用 {@link #withAgent(AiAgentProfile, Function)}，
     * 以避免实例在请求期间被关闭。</p>
     *
     * @param profile 已通过路由校验的智能体配置，不允许为空
     * @return 可复用的 HarnessAgent；不同租户或配置指纹不会复用
     * @throws RuntimeException 当工厂构建失败时原样抛出，失败结果不会缓存
     */
    public HarnessAgent getOrCreate(AiAgentProfile profile) {
        lifecycleLock.readLock().lock();
        try {
            ensureOpen();
            AiHarnessAgentKey key = AiHarnessAgentKey.from(profile, properties);
            AgentEntry entry = entryFor(key, profile);
            touch(key);
            return entry.agent();
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    /**
     * 在实例租约保护下执行一次 HarnessAgent 操作。
     *
     * <p>LRU 淘汰、显式失效或应用关闭会立即阻止新请求复用目标实例，已开始的操作完成后才关闭资源。</p>
     *
     * @param profile 已通过路由校验的智能体配置，不允许为空
     * @param action 使用 HarnessAgent 执行的操作，不允许为空
     * @param <T> 操作返回类型
     * @return 操作结果，允许为空
     */
    public <T> T withAgent(
            AiAgentProfile profile,
            Function<HarnessAgent, T> action) {
        Objects.requireNonNull(action, "action must not be null");
        AgentEntry entry = acquire(profile);
        try {
            return action.apply(entry.agent());
        } finally {
            closeIfReady(entry.release());
        }
    }

    /**
     * 失效指定租户下智能体的全部配置版本。
     *
     * @param tenantId 租户标识，不允许为空
     * @param agentId 智能体标识，不允许为空
     */
    public void invalidate(String tenantId, String agentId) {
        String requiredTenantId = required(tenantId, "tenantId");
        String requiredAgentId = required(agentId, "agentId");
        lifecycleLock.writeLock().lock();
        try {
            List<AiHarnessAgentKey> matchingKeys = instances.keySet().stream()
                    .filter(key -> key.tenantId().equals(requiredTenantId)
                            && key.agentId().equals(requiredAgentId))
                    .toList();
            removeAndRetire(matchingKeys);
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    /**
     * 停止注册表接收新调用，并关闭当前无活动租约的全部 HarnessAgent。
     *
     * <p>仍在执行的实例会在最后一个调用结束时关闭。</p>
     */
    @Override
    public void close() {
        lifecycleLock.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            removeAndRetire(new ArrayList<>(instances.keySet()));
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    private AgentEntry acquire(AiAgentProfile profile) {
        while (true) {
            lifecycleLock.readLock().lock();
            try {
                ensureOpen();
                AiHarnessAgentKey key = AiHarnessAgentKey.from(profile, properties);
                AgentEntry entry = entryFor(key, profile);
                if (entry.acquire()) {
                    touch(key);
                    return entry;
                }
                instances.remove(key, entry);
            } finally {
                lifecycleLock.readLock().unlock();
            }
        }
    }

    private AgentEntry entryFor(
            AiHarnessAgentKey key,
            AiAgentProfile profile) {
        return instances.computeIfAbsent(
                key,
                ignored -> new AgentEntry(Objects.requireNonNull(
                        factory.create(profile, key),
                        "factory returned null HarnessAgent")));
    }

    private void touch(AiHarnessAgentKey key) {
        List<AgentEntry> evicted = new ArrayList<>();
        synchronized (accessLock) {
            accessOrder.put(key, Boolean.TRUE);
            while (accessOrder.size() > capacity) {
                AiHarnessAgentKey eldest = accessOrder.entrySet()
                        .iterator()
                        .next()
                        .getKey();
                accessOrder.remove(eldest);
                AgentEntry removed = instances.remove(eldest);
                if (removed != null) {
                    evicted.add(removed);
                }
            }
        }
        evicted.forEach(this::retire);
    }

    private void removeAndRetire(List<AiHarnessAgentKey> keys) {
        List<AgentEntry> removed = new ArrayList<>();
        synchronized (accessLock) {
            for (AiHarnessAgentKey key : keys) {
                accessOrder.remove(key);
                AgentEntry entry = instances.remove(key);
                if (entry != null) {
                    removed.add(entry);
                }
            }
        }
        removed.forEach(this::retire);
    }

    private void retire(AgentEntry entry) {
        closeIfReady(entry.retire());
    }

    private void closeIfReady(HarnessAgent agent) {
        if (agent == null) {
            return;
        }
        try {
            agent.close();
        } catch (RuntimeException ignored) {
            // 关闭异常不影响注册表淘汰、失效或应用停止流程。
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("HarnessAgent registry is closed");
        }
    }

    private String required(String value, String fieldName) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static final class AgentEntry {

        private final HarnessAgent agent;
        private int activeUses;
        private boolean retired;
        private boolean agentClosed;

        private AgentEntry(HarnessAgent agent) {
            this.agent = agent;
        }

        private HarnessAgent agent() {
            return agent;
        }

        private synchronized boolean acquire() {
            if (retired) {
                return false;
            }
            activeUses++;
            return true;
        }

        private synchronized HarnessAgent release() {
            activeUses--;
            return closeWhenReady();
        }

        private synchronized HarnessAgent retire() {
            retired = true;
            return closeWhenReady();
        }

        private HarnessAgent closeWhenReady() {
            if (!retired || activeUses > 0 || agentClosed) {
                return null;
            }
            agentClosed = true;
            return agent;
        }
    }
}
