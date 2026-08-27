package com.zimo.module.feishu.gateway;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAgentRateLimiterTest {

    @Test
    void rejectsRequestsOverWindowLimitByTenantAndSender() {
        MutableClock clock = new MutableClock("2026-07-03T00:00:00Z");
        FeishuAgentRateLimiter limiter = new FeishuAgentRateLimiter(clock, true, 60, 2);

        assertThat(limiter.tryAcquire("tenant-a", "user-a")).isTrue();
        assertThat(limiter.tryAcquire("tenant-a", "user-a")).isTrue();
        assertThat(limiter.tryAcquire("tenant-a", "user-a")).isFalse();
        assertThat(limiter.tryAcquire("tenant-a", "user-b")).isTrue();
        assertThat(limiter.tryAcquire("tenant-b", "user-a")).isTrue();
    }

    @Test
    void removesExpiredTimestampsOutsideWindow() {
        MutableClock clock = new MutableClock("2026-07-03T00:00:00Z");
        FeishuAgentRateLimiter limiter = new FeishuAgentRateLimiter(clock, true, 60, 1);

        assertThat(limiter.tryAcquire("tenant-a", "user-a")).isTrue();
        assertThat(limiter.tryAcquire("tenant-a", "user-a")).isFalse();

        clock.plusSeconds(61);

        assertThat(limiter.tryAcquire("tenant-a", "user-a")).isTrue();
    }

    @Test
    void allowsAllWhenDisabled() {
        FeishuAgentRateLimiter limiter = new FeishuAgentRateLimiter(Clock.systemUTC(), false, 60, 1);

        assertThat(limiter.tryAcquire("tenant-a", "user-a")).isTrue();
        assertThat(limiter.tryAcquire("tenant-a", "user-a")).isTrue();
    }

    @Test
    void treatsNonPositiveMaxRequestsAsOne() {
        FeishuAgentRateLimiter limiter = new FeishuAgentRateLimiter(
                new MutableClock("2026-07-03T00:00:00Z"), true, 60, 0);

        assertThat(limiter.tryAcquire("tenant-a", "user-a")).isTrue();
        assertThat(limiter.tryAcquire("tenant-a", "user-a")).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(String instant) {
            this.instant = Instant.parse(instant);
        }

        private void plusSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
