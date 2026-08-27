package com.zimo.starter.ai.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zimo.starter.ai.agent.AiAgentProfile;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link AiAgentPresetRegistry} 单测：5 内置解析 / 未知回退 / 自定义注册与覆盖。
 */
class AiAgentPresetRegistryTest {

    @Test
    void resolvesAllFiveBuiltinPresetsByAgentType() {
        AiAgentPresetRegistry registry = new AiAgentPresetRegistry();

        AiAgentPreset conversation = registry.resolve(AiAgentProfile.TYPE_CONVERSATION);
        assertThat(conversation.id()).isEqualTo(AiAgentProfile.TYPE_CONVERSATION);
        assertThat(conversation.promptSuffix()).isEmpty();
        assertThat(conversation.abilities()).isEmpty();

        AiAgentPreset rag = registry.resolve(AiAgentProfile.TYPE_RAG);
        assertThat(rag.id()).isEqualTo(AiAgentProfile.TYPE_RAG);
        assertThat(rag.promptSuffix()).contains("{knowledge}").contains("【检索增强】");
        assertThat(rag.hasAbility(AiAgentPreset.ABILITY_RAG)).isTrue();
        assertThat(rag.overrideInt(AiAgentPreset.OVERRIDE_MAX_TOKENS, -1)).isEqualTo(4096);

        AiAgentPreset tool = registry.resolve(AiAgentProfile.TYPE_TOOL);
        assertThat(tool.id()).isEqualTo(AiAgentProfile.TYPE_TOOL);
        assertThat(tool.promptSuffix()).contains("【工具调用】");
        assertThat(tool.hasAbility(AiAgentPreset.ABILITY_TOOL)).isTrue();
        assertThat(tool.overrideInt(AiAgentPreset.OVERRIDE_MAX_ITERS, -1)).isEqualTo(8);

        AiAgentPreset plan = registry.resolve(AiAgentProfile.TYPE_PLAN);
        assertThat(plan.id()).isEqualTo(AiAgentProfile.TYPE_PLAN);
        assertThat(plan.promptSuffix()).contains("【规划执行】");
        assertThat(plan.hasAbility(AiAgentPreset.ABILITY_PLAN)).isTrue();
        assertThat(plan.overrideInt(AiAgentPreset.OVERRIDE_MAX_ITERS, -1)).isEqualTo(10);
        assertThat(plan.overrideInt(AiAgentPreset.OVERRIDE_MAX_TOKENS, -1)).isEqualTo(4096);

        AiAgentPreset graph = registry.resolve(AiAgentProfile.TYPE_GRAPH);
        assertThat(graph.id()).isEqualTo(AiAgentProfile.TYPE_GRAPH);
        assertThat(graph.promptSuffix()).contains("【图任务流】");
        assertThat(graph.hasAbility(AiAgentPreset.ABILITY_GRAPH)).isTrue();
        assertThat(graph.overrideInt(AiAgentPreset.OVERRIDE_MAX_ITERS, -1)).isEqualTo(12);
    }

    @Test
    void fallsBackToConversationForUnknownOrBlankType() {
        AiAgentPresetRegistry registry = new AiAgentPresetRegistry();
        assertThat(registry.resolve("unknown-type").id())
                .isEqualTo(AiAgentProfile.TYPE_CONVERSATION);
        assertThat(registry.resolve(null).id())
                .isEqualTo(AiAgentProfile.TYPE_CONVERSATION);
        assertThat(registry.resolve("  ").id())
                .isEqualTo(AiAgentProfile.TYPE_CONVERSATION);
    }

    @Test
    void registersAndOverridesCustomPreset() {
        AiAgentPresetRegistry registry = new AiAgentPresetRegistry();
        AiAgentPreset custom = new AiAgentPreset(
                "custom", "自定义模式", "\n\n【自定义】定制超参",
                Set.of(AiAgentPreset.ABILITY_TOOL),
                Map.of(AiAgentPreset.OVERRIDE_MAX_ITERS, 3));
        registry.register(custom);

        assertThat(registry.resolve("custom")).isSameAs(custom);
        assertThat(registry.all()).hasSize(6);

        // 同名覆盖，以最新为准
        AiAgentPreset replaced = new AiAgentPreset(
                "custom", "重新定义", "v2", Set.of(), Map.of());
        registry.register(replaced);
        assertThat(registry.resolve("custom")).isSameAs(replaced);
        assertThat(registry.all()).hasSize(6);
    }

    @Test
    void rejectsBlankOrNullPresetId() {
        AiAgentPresetRegistry registry = new AiAgentPresetRegistry();
        assertThatThrownBy(() -> registry.register(
                new AiAgentPreset("", "x", "", Set.of(), Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.register(
                new AiAgentPreset(null, "x", "", Set.of(), Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findReturnsOptionalPreset() {
        AiAgentPresetRegistry registry = new AiAgentPresetRegistry();
        assertThat(registry.find(AiAgentProfile.TYPE_RAG)).isPresent();
        assertThat(registry.find("missing")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
    }

    @Test
    void builtinStaticFallbackMatchesRegistrySemantics() {
        AiAgentPreset rag = AiAgentPresetRegistry.builtin(AiAgentProfile.TYPE_RAG);
        assertThat(rag.id()).isEqualTo(AiAgentProfile.TYPE_RAG);
        assertThat(rag.hasAbility(AiAgentPreset.ABILITY_RAG)).isTrue();
        assertThat(rag.overrideInt(AiAgentPreset.OVERRIDE_MAX_TOKENS, -1)).isEqualTo(4096);

        assertThat(AiAgentPresetRegistry.builtin(null).id())
                .isEqualTo(AiAgentProfile.TYPE_CONVERSATION);
        assertThat(AiAgentPresetRegistry.builtin("nope").id())
                .isEqualTo(AiAgentProfile.TYPE_CONVERSATION);
    }
}