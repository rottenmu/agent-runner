package com.zimo.framework.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.ai.AiAgentProperties;
import com.zimo.framework.ai.preset.AiAgentPreset;
import com.zimo.framework.ai.preset.AiAgentPresetRegistry;
import com.zimo.framework.ai.skill.AiSkillRegistry;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@link AiHarnessAgentFactory} 的 preset 驱动装配验证：
 * prompt 片段追加、超参覆盖、plan 模式启用、知识库注入。
 */
class AiHarnessAgentFactoryPresetTest {

    private AiAgentProperties properties;
    private HarnessAgent.Builder builder;
    private AiAgentPresetRegistry registry;

    @BeforeEach
    void setUp() {
        properties = new AiAgentProperties();
        properties.setApiKey("test-api-key");
        properties.setMaxIters(5);
        properties.setMaxTokens(2000);
        properties.setHarnessWorkspaceRoot("target/harness-preset-workspaces");
        builder = mock(HarnessAgent.Builder.class);
        when(builder.agentId(any())).thenReturn(builder);
        when(builder.name(any())).thenReturn(builder);
        when(builder.sysPrompt(anyString())).thenReturn(builder);
        when(builder.model(any(Model.class))).thenReturn(builder);
        when(builder.toolkit(any(Toolkit.class))).thenReturn(builder);
        when(builder.maxIters(anyInt())).thenReturn(builder);
        when(builder.workspace(any(Path.class))).thenReturn(builder);
        when(builder.compaction(any(CompactionConfig.class))).thenReturn(builder);
        registry = new AiAgentPresetRegistry();
    }

    @Test
    void planPresetAppendsPromptSuffixAndEnablesPlanModeAndOverridesMaxIters() {
        AiHarnessAgentFactory factory = factory();
        AiAgentProfile profile = profile("plan", null);

        factory.create(profile, key(profile));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(builder).sysPrompt(prompt.capture());
        assertThat(prompt.getValue()).contains("【规划执行】").contains("1) 先制定分步执行计划");
        verify(builder).maxIters(10);
        verify(builder).enablePlanMode(true);
        verify(builder).planFileDirectory(anyString());
    }

    @Test
    void ragPresetInjectsKnowledgeBaseContentIntoPrompt() throws IOException {
        Path knowledge = Files.createTempDirectory("rag-knowledge");
        Files.writeString(knowledge.resolve("guide.md"), "公司福利政策：每年 5 天带薪年假。");
        AiAgentProperties withMapper = properties;
        AiHarnessAgentFactory factory = factoryWithMapper(new ObjectMapper());
        AiAgentProfile profile = profileWithConfig("rag",
                "{\"knowledgeBase\":\"" + knowledge.toString().replace("\\", "\\\\") + "\"}");

        factory.create(profile, key(profile));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(builder).sysPrompt(prompt.capture());
        assertThat(prompt.getValue()).contains("【检索增强】").contains("公司福利政策")
                .contains("--- 文档: guide.md ---");
        verify(builder, never()).enablePlanMode(anyBoolean());
    }

    @Test
    void toolPresetAppendsToolPromptAndKeepsDefaultMaxIters() {
        AiHarnessAgentFactory factory = factory();
        AiAgentProfile profile = profile("tool", null);

        factory.create(profile, key(profile));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(builder).sysPrompt(prompt.capture());
        assertThat(prompt.getValue()).contains("【工具调用】");
        // tool overrides maxIters=8
        verify(builder).maxIters(8);
        verify(builder, never()).enablePlanMode(anyBoolean());
    }

    @Test
    void graphPresetAppendsGraphPromptAndOverridesMaxIters() {
        AiHarnessAgentFactory factory = factory();
        AiAgentProfile profile = profile("graph", null);

        factory.create(profile, key(profile));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(builder).sysPrompt(prompt.capture());
        assertThat(prompt.getValue()).contains("【图任务流】");
        verify(builder).maxIters(12);
    }

    @Test
    void conversationPresetKeepsBasePromptAndDefaultMaxIters() {
        AiHarnessAgentFactory factory = factory();
        AiAgentProfile profile = profile("conversation", null);

        factory.create(profile, key(profile));

        verify(builder).sysPrompt("system-a");
        verify(builder).maxIters(5);
        verify(builder, never()).enablePlanMode(anyBoolean());
    }

    @Test
    void customRegisteredPresetAppliedToProfile() {
        AiAgentPreset custom = new AiAgentPreset(
                "custom-mode", "自定义模式", "\n\n【自定义】我是定制模式。",
                Set.of(AiAgentPreset.ABILITY_PLAN), Map.of(AiAgentPreset.OVERRIDE_MAX_ITERS, 3));
        registry.register(custom);
        AiHarnessAgentFactory factory = factory();
        AiAgentProfile profile = profile("custom-mode", null);

        factory.create(profile, key(profile));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(builder).sysPrompt(prompt.capture());
        assertThat(prompt.getValue()).contains("【自定义】我是定制模式。");
        verify(builder).maxIters(3);
        verify(builder).enablePlanMode(true);
    }

    /* ---------------- helpers ---------------- */

    private AiHarnessAgentFactory factory() {
        return factoryWithMapper(null);
    }

    private AiHarnessAgentFactory factoryWithMapper(ObjectMapper objectMapper) {
        return new AiHarnessAgentFactory(
                properties,
                new AiSkillRegistry(List.of()),
                null,
                objectMapper,
                null,
                null,
                null,
                null,
                registry) {
            @Override
            protected HarnessAgent.Builder newBuilder() {
                return builder;
            }
        };
    }

    private AiAgentProfile profile(String agentType, String agentConfig) {
        return new AiAgentProfile(
                "agent-a", "tenant-a", "Agent A", "model-a", "system-a",
                List.of(), agentType, agentConfig, true);
    }

    private AiAgentProfile profileWithConfig(String agentType, String agentConfig) {
        return profile(agentType, agentConfig);
    }

    private AiHarnessAgentKey key(AiAgentProfile profile) {
        return AiHarnessAgentKey.from(profile, properties);
    }
}