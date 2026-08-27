package com.zimo.module.sys.context;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Local permission cache with a backend extension point for Redis or other stores.
 */
public final class PermissionCache {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
    private static final ConcurrentMap<Long, CacheEntry> LOCAL_CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();
    private static volatile Backend backend = NoopBackend.INSTANCE;

    private PermissionCache() {
    }

    public static void put(UserPermissionContext context) {
        put(context, DEFAULT_TTL);
    }

    public static void put(UserPermissionContext context, Duration ttl) {
        Objects.requireNonNull(context, "context must not be null");
        Long userId = Objects.requireNonNull(context.getUserId(), "context userId must not be null");
        Duration actualTtl = normalizeTtl(ttl);
        LOCAL_CACHE.put(userId, new CacheEntry(context, Instant.now().plus(actualTtl)));
        backend.save(context, actualTtl);
    }

    public static Optional<UserPermissionContext> get(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }

        CacheEntry localEntry = LOCAL_CACHE.get(userId);
        if (localEntry != null) {
            if (!localEntry.isExpired()) {
                return Optional.of(localEntry.context());
            }
            LOCAL_CACHE.remove(userId, localEntry);
        }

        Optional<UserPermissionContext> loaded = backend.load(userId);
        loaded.ifPresent(context -> LOCAL_CACHE.put(userId, new CacheEntry(context, Instant.now().plus(DEFAULT_TTL))));
        return loaded;
    }

    public static UserPermissionContext getOrNull(Long userId) {
        return get(userId).orElse(null);
    }

    public static void remove(Long userId) {
        if (userId == null) {
            return;
        }
        LOCAL_CACHE.remove(userId);
        if (Objects.equals(CURRENT_USER_ID.get(), userId)) {
            CURRENT_USER_ID.remove();
        }
        backend.delete(userId);
    }

    public static void clear() {
        LOCAL_CACHE.clear();
        backend.clear();
    }

    public static int cleanupExpired() {
        int removed = 0;
        for (Long userId : LOCAL_CACHE.keySet()) {
            CacheEntry entry = LOCAL_CACHE.get(userId);
            if (entry != null && entry.isExpired() && LOCAL_CACHE.remove(userId, entry)) {
                removed++;
            }
        }
        return removed;
    }

    public static int size() {
        cleanupExpired();
        return LOCAL_CACHE.size();
    }

    public static void bindCurrent(Long userId) {
        if (userId == null || get(userId).isEmpty()) {
            CURRENT_USER_ID.remove();
            return;
        }
        CURRENT_USER_ID.set(userId);
    }

    public static void bindCurrent(UserPermissionContext context) {
        put(context);
        CURRENT_USER_ID.set(context.getUserId());
    }

    public static void unbindCurrent() {
        CURRENT_USER_ID.remove();
    }

    public static Optional<UserPermissionContext> current() {
        return get(CURRENT_USER_ID.get());
    }

    public static void setBackend(Backend backend) {
        PermissionCache.backend = backend == null ? NoopBackend.INSTANCE : backend;
    }

    public static Backend getBackend() {
        return backend;
    }

    public interface Backend {

        void save(UserPermissionContext context, Duration ttl);

        Optional<UserPermissionContext> load(Long userId);

        void delete(Long userId);

        default void clear() {
        }
    }

    public enum NoopBackend implements Backend {
        INSTANCE;

        @Override
        public void save(UserPermissionContext context, Duration ttl) {
        }

        @Override
        public Optional<UserPermissionContext> load(Long userId) {
            return Optional.empty();
        }

        @Override
        public void delete(Long userId) {
        }
    }

    private static Duration normalizeTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return DEFAULT_TTL;
        }
        return ttl;
    }

    private record CacheEntry(UserPermissionContext context, Instant expiresAt) {

        private boolean isExpired() {
            return !expiresAt.isAfter(Instant.now());
        }
    }
}
