package com.zimo.framework.ai.preset;

import java.util.Map;
import java.util.Set;

/**
 * 智能体运行时模式预设（对应 dsh {@code preset}：模式=能力+配置的组合）。
 *
 * <p>一个 preset 是命名配置组合：
 * <ul>
 *   <li>{@code id} 预设标识（与 {@code agentType} 同值空间，内置 conversation/rag/tool/plan/graph）</li>
 *   <li>{@code description} 预设说明</li>
 *   <li>{@code promptSuffix} 类型专属系统提示词片段（追加在基础 prompt 之后）</li>
 *   <li>{@code abilities} 启用的能力开关（plan/graph/taskList/rag/tool 等），供 factory 应用策略</li>
 *   <li>{@code overrides} 超参覆盖（maxIters/temperature/maxTokens），未覆盖项沿用全局配置</li>
 * </ul>
 * 静态内置 preset 由 {@link AiAgentPresetRegistry} 提供；业务可注册自定义 preset 扩展。</p>
 *
 * <p>能力键常量：{@link #ABILITY_PLAN} 启用 Harness 计划模式、{@link #ABILITY_GRAPH} 图任务流声明、
 * {@link #ABILITY_SHELL} 计划模式内允许 shell、{@link #ABILITY_TASK_LIST} 任务清单、
 * {@link #ABILITY_RAG} 检索增强提示词、{@link #ABILITY_TOOL} 工具优先提示词。</p>
 *
 * @param id 预设标识
 * @param description 预设说明
 * @param promptSuffix 类型专属提示词片段（可空）
 * @param abilities 能力开关集合（可空，默认空集）
 * @param overrides 超参覆盖（可空，默认空图）
 */
public record AiAgentPreset(
        String id,
        String description,
        String promptSuffix,
        Set<String> abilities,
        Map<String, Object> overrides) {

    /* ---- 能力键 ---- */
    public static final String ABILITY_PLAN = "plan";
    public static final String ABILITY_GRAPH = "graph";
    public static final String ABILITY_SHELL = "shell";
    public static final String ABILITY_TASK_LIST = "taskList";
    public static final String ABILITY_RAG = "rag";
    public static final String ABILITY_TOOL = "tool";

    /* ---- 超参键 ---- */
    public static final String OVERRIDE_MAX_ITERS = "maxIters";
    public static final String OVERRIDE_TEMPERATURE = "temperature";
    public static final String OVERRIDE_MAX_TOKENS = "maxTokens";

    /** 超参覆盖默认值常量（与 AiAgentProperties 默认一致）。 */
    public static final int DEFAULT_MAX_ITERS = 5;
    public static final double DEFAULT_TEMPERATURE = 0.7;
    public static final int DEFAULT_MAX_TOKENS = 2000;

    public AiAgentPreset {
        abilities = abilities == null ? Set.of() : Set.copyOf(abilities);
        overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
    }

    public boolean hasAbility(String ability) {
        return abilities.contains(ability);
    }

    /** 读取超参覆盖，缺省回退给定默认值。 */
    public int overrideInt(String key, int fallback) {
        Object value = overrides.get(key);
        return value instanceof Number n ? n.intValue() : fallback;
    }

    public double overrideDouble(String key, double fallback) {
        Object value = overrides.get(key);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
}