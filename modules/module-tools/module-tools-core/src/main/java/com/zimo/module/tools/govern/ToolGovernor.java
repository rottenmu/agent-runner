package com.zimo.module.tools.govern;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具调用治理运行时：令牌桶限流 + 熔断器。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class ToolGovernor {

    /** 每个工具默认令牌桶：容量 20，每秒补充 10（可通过 limit-per-second 调整）。 */
    public static final int DEFAULT_CAPACITY = 20;
    public static final int DEFAULT_RATE_PER_SECOND = 10;

    /** 熔断阈值：连续失败次数。 */
    public static final int DEFAULT_FAILURE_THRESHOLD = 5;
    /** 熔断打开时长（毫秒）。 */
    public static final long DEFAULT_OPEN_DURATION_MS = 30_000;

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Circuit> circuits = new ConcurrentHashMap<>();

    /** 尝试获取令牌；超限返回 false。 */
    public boolean tryAcquire(String toolName) {
        TokenBucket bucket = buckets.computeIfAbsent(toolName,
                t -> new TokenBucket(DEFAULT_CAPACITY, DEFAULT_RATE_PER_SECOND));
        return bucket.tryAcquire();
    }

    /** 熔断状态：CLOSED=正常 / OPEN=已熔断（拒绝） / HALF_OPEN=半开尝试。 */
    public String circuitState(String toolName) {
        Circuit circuit = circuits.get(toolName);
        if (circuit == null) {
            return "CLOSED";
        }
        return circuit.state();
    }

    /** 调用前检查熔断；已熔断返回 false。 */
    public boolean allowCall(String toolName) {
        Circuit circuit = circuits.get(toolName);
        return circuit == null || circuit.allow();
    }

    /** 记录成功（关闭熔断计数 / 半开复位）。 */
    public void recordSuccess(String toolName) {
        Circuit circuit = circuits.get(toolName);
        if (circuit != null) {
            circuit.onSuccess();
        }
    }

    /** 记录失败（计数；超阈值打开熔断）。 */
    public void recordFailure(String toolName) {
        Circuit circuit = circuits.computeIfAbsent(toolName, t -> new Circuit());
        circuit.onFailure();
    }

    /** 手动复位熔断与限流。 */
    public void reset(String toolName) {
        circuits.remove(toolName);
        buckets.remove(toolName);
    }

    /* ---------------- 令牌桶 ---------------- */

    private static final class TokenBucket {
        private final double capacity;
        private final double ratePerSecond;
        private double tokens;
        private long lastRefillNanos = System.nanoTime();

        TokenBucket(int capacity, int ratePerSecond) {
            this.capacity = capacity;
            this.ratePerSecond = ratePerSecond;
            this.tokens = capacity;
        }

        synchronized boolean tryAcquire() {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            lastRefillNanos = now;
            tokens = Math.min(capacity, tokens + elapsedSeconds * ratePerSecond);
        }
    }

    /* ---------------- 熔断器 ---------------- */

    private static final class Circuit {
        private int failureCount;
        private long openedAt;
        private volatile boolean open;
        private volatile boolean halfOpen;

        synchronized String state() {
            if (open) {
                return System.currentTimeMillis() - openedAt >= DEFAULT_OPEN_DURATION_MS ? "HALF_OPEN" : "OPEN";
            }
            return "CLOSED";
        }

        synchronized boolean allow() {
            if (!open) {
                return true;
            }
            if (System.currentTimeMillis() - openedAt >= DEFAULT_OPEN_DURATION_MS) {
                open = false;
                halfOpen = true;
                return true;
            }
            return false;
        }

        synchronized void onSuccess() {
            failureCount = 0;
            open = false;
            halfOpen = false;
        }

        synchronized void onFailure() {
            if (halfOpen) {
                open = true;
                openedAt = System.currentTimeMillis();
                halfOpen = false;
                return;
            }
            failureCount++;
            if (failureCount >= DEFAULT_FAILURE_THRESHOLD) {
                open = true;
                openedAt = System.currentTimeMillis();
                halfOpen = false;
            }
        }
    }
}
