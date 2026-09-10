package com.zimo.framework.ai.interop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.ai.AiAgentProperties;
import com.zimo.framework.ai.agent.AiAgentProfile;
import com.zimo.framework.ai.agent.AiHarnessAgentFactory;
import com.zimo.framework.ai.agent.AiHarnessAgentKey;
import com.zimo.framework.ai.preset.AiAgentPresetRegistry;
import com.zimo.framework.ai.skill.AiSkillRegistry;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 外部 harness 子智能体 provider 单测（dsh A8 meta-harness）。
 */
class ExternalHarnessSubagentProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void extractParsesExternalHarnessTasksFromConfig() throws Exception {
        ExternalHarnessSubagentProvider provider = ExternalHarnessSubagentProvider.defaults();
        var config = MAPPER.readTree("""
                {
                  "externalHarness": {
                    "tasks": [
                      {
                        "name": "claude-code",
                        "description": "代码评审",
                        "url": "https://harness.example.com/run",
                        "headers": { "Authorization": "Bearer abc" },
                        "model": "claude-sonnet-4",
                        "maxIters": 10,
                        "remoteStreaming": true
                      }
                    ]
                  }
                }
                """);

        List<ExternalHarnessSubagent> subagents = provider.extract(config);

        assertThat(subagents).hasSize(1);
        ExternalHarnessSubagent subagent = subagents.get(0);
        assertThat(subagent.name()).isEqualTo("claude-code");
        assertThat(subagent.url()).isEqualTo("https://harness.example.com/run");
        assertThat(subagent.headers()).containsEntry("Authorization", "Bearer abc");
        assertThat(subagent.model()).isEqualTo("claude-sonnet-4");
        assertThat(subagent.maxIters()).isEqualTo(10);
        assertThat(subagent.remoteStreaming()).isTrue();
    }

    @Test
    void extractReturnsEmptyForMissingOrBlankName() throws Exception {
        ExternalHarnessSubagentProvider provider = ExternalHarnessSubagentProvider.defaults();
        assertThat(provider.extract(null)).isEmpty();

        var config = MAPPER.readTree("""
                { "externalHarness": { "tasks": [
                    { "name": "", "url": "https://x.example.com" },
                    { "name": "ok", "url": "" },
                    { "name": "valid", "url": "https://valid.example.com" }
                ] } }
                """);
        List<ExternalHarnessSubagent> subagents = provider.extract(config);
        assertThat(subagents).hasSize(1);
        assertThat(subagents.get(0).name()).isEqualTo("valid");
    }

    @Test
    void toDeclarationBuildsRemoteDeclaration() {
        ExternalHarnessSubagentProvider provider = ExternalHarnessSubagentProvider.defaults();
        ExternalHarnessSubagent subagent = new ExternalHarnessSubagent(
                "claude-code", "评审", "https://h.example.com/run",
                Map.of("Authorization", "Bearer x"), "claude-4", 12, true);

        SubagentDeclaration declaration = provider.toDeclaration(subagent);

        assertThat(declaration.getName()).isEqualTo("claude-code");
        assertThat(declaration.getUrl()).isEqualTo("https://h.example.com/run");
        assertThat(declaration.getHeaders()).containsEntry("Authorization", "Bearer x");
        assertThat(declaration.getModel()).isEqualTo("claude-4");
        assertThat(declaration.getMaxIters()).isEqualTo(12);
    }

    @Test
    void toDeclarationRejectsInvalidSubagent() {
        ExternalHarnessSubagentProvider provider = ExternalHarnessSubagentProvider.defaults();
        assertThatThrownBy(() -> provider.toDeclaration(
                new ExternalHarnessSubagent("", "x", "https://h.example.com",
                        Map.of(), null, 0, false)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.toDeclaration(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factoryAppendsExternalHarnessDeclarationsToSubagents() throws Exception {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setApiKey("test-api-key");
        properties.setHarnessWorkspaceRoot("target/harness-external-workspaces");
        HarnessAgent.Builder builder = mock(HarnessAgent.Builder.class);
        when(builder.agentId(any())).thenReturn(builder);
        when(builder.name(any())).thenReturn(builder);
        when(builder.sysPrompt(anyString())).thenReturn(builder);
        when(builder.model(any(Model.class))).thenReturn(builder);
        when(builder.toolkit(any(Toolkit.class))).thenReturn(builder);
        when(builder.maxIters(anyInt())).thenReturn(builder);
        when(builder.workspace(any(Path.class))).thenReturn(builder);
        when(builder.build()).thenReturn(mock(HarnessAgent.class));

        AiAgentPresetRegistry presetRegistry = new AiAgentPresetRegistry();
        ExternalHarnessSubagentProvider provider = ExternalHarnessSubagentProvider.defaults();
        AiHarnessAgentFactory factory = new AiHarnessAgentFactory(
                properties, new AiSkillRegistry(List.of()), null, MAPPER, null,
                null, null, null, presetRegistry, provider) {
            @Override
            protected HarnessAgent.Builder newBuilder() {
                return builder;
            }
        };
        // 诊断：确认 provider 已注入工厂字段
        try {
            var field = AiHarnessAgentFactory.class.getDeclaredField("externalHarnessProvider");
            field.setAccessible(true);
            assertThat(field.get(factory)).isSameAs(provider);
        } catch (Exception e) {
            throw new AssertionError("反射读取 externalHarnessProvider 失败", e);
        }
        AiAgentProfile profile = new AiAgentProfile(
                "agent-a", "tenant-a", "Agent A", "model-a", "system-a",
                List.of(), AiAgentProfile.TYPE_GRAPH,
                """
                { "externalHarness": { "tasks": [
                    { "name": "claude-code", "url": "https://h.example.com/run",
                      "headers": { "Authorization": "Bearer x" } }
                ] } }
                """,
                true);

        factory.create(profile, AiHarnessAgentKey.from(profile, properties));

        ArgumentCaptor<List<SubagentDeclaration>> declarations =
                ArgumentCaptor.forClass(List.class);
        verify(builder).subagents(declarations.capture());
        assertThat(declarations.getValue()).hasSize(1);
        assertThat(declarations.getValue().get(0).getName()).isEqualTo("claude-code");
        assertThat(declarations.getValue().get(0).getUrl())
                .isEqualTo("https://h.example.com/run");
    }
}