package com.zimo.module.tools.govern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.util.StringUtils;

/**
 * 动态工具注册表：管理插件工具 / Python 脚本工具 / Java SDK 注册的执行器。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class ToolRegistry {

    private final Map<String, ToolExecutor> executors = new ConcurrentHashMap<>();

    /** 注册执行器。 */
    public void register(ToolExecutor executor) {
        if (executor != null && StringUtils.hasText(executor.name())) {
            executors.put(executor.name().trim(), executor);
        }
    }

    /** 注销执行器。 */
    public void unregister(String name) {
        if (StringUtils.hasText(name)) {
            executors.remove(name.trim());
        }
    }

    /** 查询执行器。 */
    public ToolExecutor get(String name) {
        return name == null ? null : executors.get(name.trim());
    }

    /** 工具是否存在。 */
    public boolean contains(String name) {
        return name != null && executors.containsKey(name.trim());
    }

    /** 全部执行器列表。 */
    public List<ToolExecutor> list() {
        return new ArrayList<>(executors.values());
    }

    /** MCP 工具描述列表。 */
    public List<Map<String, Object>> describeTools() {
        List<Map<String, Object>> descriptors = new ArrayList<>();
        for (ToolExecutor executor : executors.values()) {
            Map<String, Object> descriptor = new LinkedHashMap<>();
            descriptor.put("name", executor.name());
            descriptor.put("description", executor.description());
            descriptor.put("readOnly", executor.readOnly());
            descriptors.add(descriptor);
        }
        return descriptors;
    }
}
