package com.zimo.module.feishu.gateway;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class FeishuAgentRateLimiter {
    private final Clock clock;
    private final boolean enabled;
    private final long windowSeconds;
    private final int maxRequests;
    private final Map<String, Deque<Instant>> requests = new ConcurrentHashMap<>();

    public FeishuAgentRateLimiter(Clock clock, boolean enabled, long windowSeconds, int maxRequests) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.enabled = enabled;
        this.windowSeconds = Math.max(1, windowSeconds);
        this.maxRequests = Math.max(1, maxRequests);
    }

    public boolean tryAcquire(String tenantKey, String senderUserId) {
        if (!enabled) {
            return true;
        }

        String key = tenantKey + ":" + senderUserId;
        Instant now = clock.instant();
        Instant cutoff = now.minusSeconds(windowSeconds);
        Deque<Instant> timestamps = requests.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= maxRequests) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
