package com.zimo.starter.ai.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 插件事件总线单测（dsh A1 Cordis 事件模型）：订阅/派发/短路/卸载撤销。
 */
class PluginEventBusTest {

    @Test
    void deliversEventToSubscribers() {
        PluginEventBus bus = new PluginEventBus();
        AtomicInteger count = new AtomicInteger();
        bus.on("p1", "agent.turn.begin", (type, payload) -> {
            count.incrementAndGet();
            return true;
        });
        bus.on("p2", "agent.turn.begin", (type, payload) -> {
            count.incrementAndGet();
            return true;
        });

        boolean continued = bus.emit("agent.turn.begin", Map.of("agentId", "a"));

        assertThat(continued).isTrue();
        assertThat(count.get()).isEqualTo(2);
    }

    @Test
    void shortCircuitsOnFalseHandler() {
        PluginEventBus bus = new PluginEventBus();
        AtomicInteger second = new AtomicInteger();
        bus.on("p1", "tool.pre", (t, p) -> false);
        bus.on("p2", "tool.pre", (t, p) -> {
            second.incrementAndGet();
            return true;
        });

        boolean continued = bus.emit("tool.pre", Map.of());

        assertThat(continued).isFalse();
        assertThat(second.get()).isZero();
    }

    @Test
    void emitsOnlyToMatchingEventType() {
        PluginEventBus bus = new PluginEventBus();
        AtomicInteger count = new AtomicInteger();
        bus.on("p1", "tool.pre", (t, p) -> {
            count.incrementAndGet();
            return true;
        });

        bus.emit("tool.post", Map.of());
        assertThat(count.get()).isZero();

        bus.emit("tool.pre", Map.of());
        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    void removeAllRevokesPluginSubscriptions() {
        PluginEventBus bus = new PluginEventBus();
        AtomicInteger count = new AtomicInteger();
        bus.on("p1", "agent.turn.begin", (t, p) -> {
            count.incrementAndGet();
            return true;
        });
        bus.on("p2", "agent.turn.begin", (t, p) -> {
            count.incrementAndGet();
            return true;
        });
        assertThat(bus.totalSubscriptions()).isEqualTo(2);

        bus.removeAll("p1");

        assertThat(bus.totalSubscriptions()).isEqualTo(1);
        bus.emit("agent.turn.begin", Map.of());
        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    void payloadIsDefensiveCopy() {
        PluginEventBus bus = new PluginEventBus();
        Map<String, Object> original = new java.util.LinkedHashMap<>();
        original.put("agentId", "a");
        bus.on("p1", "agent.turn.begin", (t, p) -> true);

        bus.emit("agent.turn.begin", original);
        original.put("agentId", "mutated");

        assertThat(bus.subscriberCount("agent.turn.begin")).isEqualTo(1);
    }

    @Test
    void emitWithoutSubscribersContinues() {
        PluginEventBus bus = new PluginEventBus();
        assertThat(bus.emit("agent.turn.begin", Map.of())).isTrue();
        assertThat(bus.subscriberCount("agent.turn.begin")).isZero();
    }
}