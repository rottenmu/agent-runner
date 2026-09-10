package com.zimo.framework.ai.preset;

import com.zimo.framework.ai.agent.AiAgentProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 智能体模式预设注册表（模式=能力+配置组合，可注册/替换）。
 *
 * <p>内置 5 个预设与 {@link AiAgentProfile} 类型空间一致（conversation/rag/tool/plan/graph），
 * 作为 factory 类型策略的单一事实源：{@code AiHarnessAgentFactory} 装配时按
 * {@code agentType} 解析预设应用（prompt 片段/能力/超参覆盖）。业务可注册自定义预设扩展，
 * 名称冲突时以最新注册为准。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-24
 */
public class AiAgentPresetRegistry {

    private final Map<String, AiAgentPreset> presets = new LinkedHashMap<>();

    public AiAgentPresetRegistry() {
        registerDefaults();
    }

    /** 解析预设；未知类型返回普通对话预设（不抛异常）。 */
    public AiAgentPreset resolve(String agentType) {
        if (agentType == null || agentType.isBlank()) {
            return presets.get(AiAgentProfile.TYPE_CONVERSATION);
        }
        return presets.getOrDefault(agentType, presets.get(AiAgentProfile.TYPE_CONVERSATION));
    }

    /** 注册/替换预设。 */
    public AiAgentPreset register(AiAgentPreset preset) {
        if (preset == null || preset.id() == null || preset.id().isBlank()) {
            throw new IllegalArgumentException("preset id 不能为空");
        }
        presets.put(preset.id(), preset);
        return preset;
    }

    /** 查询预设，不存在时返回空。 */
    public Optional<AiAgentPreset> find(String id) {
        return Optional.ofNullable(id == null ? null : presets.get(id));
    }

    /** 当前全部预设（按注册顺序）。 */
    public Map<String, AiAgentPreset> all() {
        return Map.copyOf(presets);
    }

    /** 静态内建预设（无注册表场景，供 factory 兜底）：按类型返回等价预设。 */
    public static AiAgentPreset builtin(String agentType) {
        return switch (agentType == null ? "" : agentType) {
            case AiAgentProfile.TYPE_RAG -> new AiAgentPreset(
                    AiAgentProfile.TYPE_RAG, "检索增强",
                    "\n\n【检索增强】你是知识库问答助手。回答前应优先检索可用资料：{knowledge}\n回答应基于检索到的资料，引用来源；资料不足时明确说明，不要编造。",
                    java.util.Set.of(AiAgentPreset.ABILITY_RAG),
                    Map.of(AiAgentPreset.OVERRIDE_MAX_TOKENS, 4096));
            case AiAgentProfile.TYPE_TOOL -> new AiAgentPreset(
                    AiAgentProfile.TYPE_TOOL, "工具调用",
                    "\n\n【工具调用】你是工具调用型智能体。优先使用可用工具完成任务："
                            + "先分析任务需要的工具，调用工具获取结果，再基于结果组织回答。"
                            + "工具失败时尝试替代方案或说明原因。",
                    java.util.Set.of(AiAgentPreset.ABILITY_TOOL),
                    Map.of(AiAgentPreset.OVERRIDE_MAX_ITERS, 8));
            case AiAgentProfile.TYPE_PLAN -> new AiAgentPreset(
                    AiAgentProfile.TYPE_PLAN, "规划执行",
                    "\n\n【规划执行】你是规划执行型智能体。对于复杂任务："
                            + "1) 先制定分步执行计划并写入计划文件；2) 按计划逐步执行；"
                            + "3) 每步执行后更新进度；4) 计划完成后再给出最终答案。",
                    java.util.Set.of(AiAgentPreset.ABILITY_PLAN),
                    Map.of(AiAgentPreset.OVERRIDE_MAX_ITERS, 10,
                            AiAgentPreset.OVERRIDE_MAX_TOKENS, 4096));
            case AiAgentProfile.TYPE_GRAPH -> new AiAgentPreset(
                    AiAgentProfile.TYPE_GRAPH, "图任务流",
                    "\n\n【图任务流】你是任务编排协调者。将复杂任务拆解为多个子任务，"
                            + "按依赖顺序分发给对应子智能体执行，收集所有子任务结果后汇总输出。",
                    java.util.Set.of(AiAgentPreset.ABILITY_GRAPH),
                    Map.of(AiAgentPreset.OVERRIDE_MAX_ITERS, 12));
            default -> new AiAgentPreset(
                    AiAgentProfile.TYPE_CONVERSATION, "普通对话", "",
                    java.util.Set.of(), Map.of());
        };
    }

    private void registerDefaults() {
        register(new AiAgentPreset(
                AiAgentProfile.TYPE_CONVERSATION,
                "普通对话：基础提示词 + 全量超参，适合通用问答",
                "",
                java.util.Set.of(),
                Map.of()));
        register(new AiAgentPreset(
                AiAgentProfile.TYPE_RAG,
                "检索增强：知识库上下文注入 + 引用要求",
                "\n\n【检索增强】你是知识库问答助手。回答前应优先检索可用资料："
                        + "{knowledge}\n回答应基于检索到的资料，引用来源；资料不足时明确说明，不要编造。",
                java.util.Set.of(AiAgentPreset.ABILITY_RAG),
                Map.of(
                        AiAgentPreset.OVERRIDE_MAX_TOKENS, 4096)));
        register(new AiAgentPreset(
                AiAgentProfile.TYPE_TOOL,
                "工具调用：优先使用可用工具完成任务",
                "\n\n【工具调用】你是工具调用型智能体。优先使用可用工具完成任务："
                        + "先分析任务需要的工具，调用工具获取结果，再基于结果组织回答。"
                        + "工具失败时尝试替代方案或说明原因。",
                java.util.Set.of(AiAgentPreset.ABILITY_TOOL),
                Map.of(
                        AiAgentPreset.OVERRIDE_MAX_ITERS, 8)));
        register(new AiAgentPreset(
                AiAgentProfile.TYPE_PLAN,
                "规划执行：先规划后执行（Harness 计划模式）",
                "\n\n【规划执行】你是规划执行型智能体。对于复杂任务："
                        + "1) 先制定分步执行计划并写入计划文件；2) 按计划逐步执行；"
                        + "3) 每步执行后更新进度；4) 计划完成后再给出最终答案。",
                java.util.Set.of(AiAgentPreset.ABILITY_PLAN),
                Map.of(
                        AiAgentPreset.OVERRIDE_MAX_ITERS, 10,
                        AiAgentPreset.OVERRIDE_MAX_TOKENS, 4096)));
        register(new AiAgentPreset(
                AiAgentProfile.TYPE_GRAPH,
                "图任务流：子任务编排分发（Windows 下调退化为提示词引导）",
                "\n\n【图任务流】你是任务编排协调者。将复杂任务拆解为多个子任务，"
                        + "按依赖顺序分发给对应子智能体执行，收集所有子任务结果后汇总输出。",
                java.util.Set.of(AiAgentPreset.ABILITY_GRAPH),
                Map.of(
                        AiAgentPreset.OVERRIDE_MAX_ITERS, 12)));
    }
}