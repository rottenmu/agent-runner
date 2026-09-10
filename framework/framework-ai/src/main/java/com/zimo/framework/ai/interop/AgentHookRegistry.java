package com.zimo.framework.ai.interop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AGENTS.md hook 注册表：按触发点分组管理 {@link AgentHook}（dsh A8）。
 *
 * <p>同一触发点下同名 hook 以最新注册为准；{@code all(trigger)} 返回注册顺序。
 * 支持从 {@link InteropInstruction} 批量解析注册，供 ToolPipeline 桥接消费。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-24
 */
public class AgentHookRegistry {

    private final Map<String, Map<String, AgentHook>> byTrigger = new LinkedHashMap<>();
    private final List<AgentHook> ordered = new ArrayList<>();

    /** 注册（同触发点同名覆盖）。 */
    public void register(AgentHook hook) {
        if (hook == null || hook.command() == null || hook.command().isBlank()) {
            return;
        }
        Map<String, AgentHook> group = byTrigger.computeIfAbsent(
                hook.trigger(), t -> new LinkedHashMap<>());
        boolean replaced = group.containsKey(hook.name());
        group.put(hook.name(), hook);
        if (!replaced) {
            ordered.add(hook);
        } else {
            for (int i = 0; i < ordered.size(); i++) {
                AgentHook existing = ordered.get(i);
                if (existing.trigger().equals(hook.trigger())
                        && existing.name().equals(hook.name())) {
                    ordered.set(i, hook);
                    break;
                }
            }
        }
    }

    /** 批量注册（从解析结果）。 */
    public void registerAll(List<AgentHook> hooks) {
        if (hooks == null) {
            return;
        }
        for (AgentHook hook : hooks) {
            register(hook);
        }
    }

    /** 查询某触发点全部 hook（按注册顺序）。 */
    public List<AgentHook> all(String trigger) {
        Map<String, AgentHook> group = byTrigger.get(trigger);
        return group == null ? List.of() : List.copyOf(group.values());
    }

    /** 查询某触发点下匹配指定工具的 hook。 */
    public List<AgentHook> matching(String trigger, String toolName) {
        return all(trigger).stream()
                .filter(hook -> hook.matches(toolName))
                .toList();
    }

    /** 全部 hook（按注册顺序）。 */
    public List<AgentHook> all() {
        return List.copyOf(ordered);
    }

    public boolean isEmpty() {
        return ordered.isEmpty();
    }

    public int size() {
        return ordered.size();
    }
}