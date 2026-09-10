package com.zimo.framework.ai.plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * 插件事件总线（对齐 dsh Cordis 事件模型：{@code ctx.emit / ctx.on}）。
 *
 * <p>插件在 onLoad 中通过 {@link PluginContext#eventBus()} 订阅事件；运行时
 * （Turn 循环 / 工具流水线 / 插件生命周期）发布事件。订阅与插件 id 关联，
 * 插件卸载时按 id 整体撤销，保证可逆回滚。</p>
 *
 * <p>事件类型常量见 {@link #PLUGIN_LOADED} 等；payload 为只读 Map，避免订阅方
 * 篡改运行时状态。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-25
 */
public class PluginEventBus {

    /* ---- 事件类型 ---- */
    public static final String PLUGIN_LOADED = "plugin.loaded";
    public static final String PLUGIN_UNLOADED = "plugin.unloaded";
    public static final String AGENT_TURN_BEGIN = "agent.turn.begin";
    public static final String AGENT_TURN_END = "agent.turn.end";
    public static final String TOOL_PRE = "tool.pre";
    public static final String TOOL_POST = "tool.post";

    /** 事件订阅者（返回 true 表示继续派发，false 表示短路/吞没）。 */
    @FunctionalInterface
    public interface EventHandler {
        boolean handle(String eventType, Map<String, Object> payload);
    }

    private record Subscription(String pluginId, String eventType, EventHandler handler) {
    }

    private final Map<String, List<Subscription>> byEvent = new ConcurrentHashMap<>();
    private final List<Subscription> all = new CopyOnWriteArrayList<>();

    /** 订阅事件（记录插件归属，卸载时可撤销）。 */
    public void on(String pluginId, String eventType, EventHandler handler) {
        if (pluginId == null || eventType == null || handler == null) {
            return;
        }
        Subscription sub = new Subscription(pluginId, eventType, handler);
        byEvent.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(sub);
        all.add(sub);
    }

    /**
     * 发布事件：同步派发给该类型全部订阅者；任一订阅者返回 false 即短路
     * （停止继续派发），返回是否被短路（false=被短路/吞没）。
     */
    public boolean emit(String eventType, Map<String, Object> payload) {
        List<Subscription> subs = byEvent.get(eventType);
        if (subs == null || subs.isEmpty()) {
            return true;
        }
        Map<String, Object> safe = payload == null ? Map.of() : Map.copyOf(payload);
        for (Subscription sub : subs) {
            if (!sub.handler().handle(eventType, safe)) {
                return false;
            }
        }
        return true;
    }

    /** 撤销某插件全部订阅（卸载回滚）。 */
    public void removeAll(String pluginId) {
        if (pluginId == null) {
            return;
        }
        all.removeIf(sub -> pluginId.equals(sub.pluginId()));
        byEvent.values().forEach(list -> list.removeIf(sub -> pluginId.equals(sub.pluginId())));
        byEvent.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /** 某事件类型的订阅者数。 */
    public int subscriberCount(String eventType) {
        List<Subscription> subs = byEvent.get(eventType);
        return subs == null ? 0 : subs.size();
    }

    /** 全部订阅数（含跨类型）。 */
    public int totalSubscriptions() {
        return all.size();
    }
}