package com.zimo.starter.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zimo.starter.ai.AiAgentProperties;
import com.zimo.starter.ai.skill.AiSkill;
import com.zimo.starter.ai.skill.AiSkillRegistry;
import com.zimo.starter.ai.skill.AiSkillResult;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiHarnessAgentFactoryTest {

    @Test
    void buildsTenantIsolatedHarnessAgentFromProfileAndProperties() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setHarnessWorkspaceRoot("target/harness-workspaces");
        properties.setApiKey("configured-api-key");
        properties.setMaxIters(7);
        properties.setContextCompressionTriggerMessages(24);
        properties.setContextCompressionRecentMessages(6);
        HarnessAgent.Builder builder = preparedBuilder();
        HarnessAgent built = mock(HarnessAgent.class);
        when(builder.build()).thenReturn(built);
        AiSkillRegistry skillRegistry = new AiSkillRegistry(List.of(
                skill("skill-a"),
                skill("skill-not-bound")));
        AiHarnessAgentFactory factory = factory(properties, skillRegistry, builder);
        AiAgentProfile profile = new AiAgentProfile(
                "agent-a",
                "tenant-a",
                "Agent A",
                "model-a",
                "system-a",
                List.of("skill-a"),
                true);
        AiHarnessAgentKey key = AiHarnessAgentKey.from(profile, properties);

        assertThat(factory.create(profile, key)).isSameAs(built);

        verify(builder).agentId("agent-a");
        verify(builder).name("Agent A");
        verify(builder).sysPrompt("system-a");
        ArgumentCaptor<Model> model = ArgumentCaptor.forClass(Model.class);
        verify(builder).model(model.capture());
        assertThat(model.getValue().getModelName()).isEqualTo("model-a");
        assertThat(model.getValue()).isInstanceOf(DashScopeChatModel.class);
        verify(builder).maxIters(7);
        assertManagedTools(builder);
        assertWorkspace(builder);
        assertCompaction(builder);
    }

    @Test
    void usesNativeDashScopeModelOnlyForNativeEndpoint() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setApiKey("configured-api-key");
        properties.setModelType("dashscope_native");
        properties.setBaseUrl("https://dashscope.aliyuncs.com");
        HarnessAgent.Builder builder = preparedBuilder();
        when(builder.build()).thenReturn(mock(HarnessAgent.class));
        AiHarnessAgentFactory factory = factory(
                properties,
                new AiSkillRegistry(List.of()),
                builder);
        AiAgentProfile profile = new AiAgentProfile(
                "agent-a",
                "tenant-a",
                "Agent A",
                "model-a",
                "system-a",
                List.of(),
                true);

        factory.create(profile, AiHarnessAgentKey.from(profile, properties));

        ArgumentCaptor<Model> model = ArgumentCaptor.forClass(Model.class);
        verify(builder).model(model.capture());
        assertThat(model.getValue()).isInstanceOf(DashScopeChatModel.class);
    }

    private static HarnessAgent.Builder preparedBuilder() {
        HarnessAgent.Builder builder = mock(HarnessAgent.Builder.class);
        when(builder.agentId(any())).thenReturn(builder);
        when(builder.name(any())).thenReturn(builder);
        when(builder.sysPrompt(any())).thenReturn(builder);
        when(builder.model(any(Model.class))).thenReturn(builder);
        when(builder.toolkit(any(Toolkit.class))).thenReturn(builder);
        when(builder.maxIters(anyInt())).thenReturn(builder);
        when(builder.workspace(any(Path.class))).thenReturn(builder);
        when(builder.compaction(any(CompactionConfig.class))).thenReturn(builder);
        return builder;
    }

    private static AiHarnessAgentFactory factory(
            AiAgentProperties properties,
            AiSkillRegistry skillRegistry,
            HarnessAgent.Builder builder) {
        return new AiHarnessAgentFactory(properties, skillRegistry) {
            @Override
            protected HarnessAgent.Builder newBuilder() {
                return builder;
            }
        };
    }

    private static void assertManagedTools(HarnessAgent.Builder builder) {
        ArgumentCaptor<Toolkit> toolkit = ArgumentCaptor.forClass(Toolkit.class);
        verify(builder).toolkit(toolkit.capture());
        assertThat(toolkit.getValue().getToolNames()).containsExactly("skill-a");
        ToolResultBlock skillResult = toolkit.getValue()
                .getTool("skill-a")
                .callAsync(ToolCallParam.builder()
                        .input(Map.of("text", "hello"))
                        .build())
                .block();
        assertThat(skillResult).isNotNull();
        assertThat(skillResult.getOutput())
                .singleElement()
                .isInstanceOfSatisfying(
                        TextBlock.class,
                        block -> assertThat(block.getText()).isEqualTo("hello"));
    }

    private static void assertWorkspace(HarnessAgent.Builder builder) {
        ArgumentCaptor<Path> workspace = ArgumentCaptor.forClass(Path.class);
        verify(builder).workspace(workspace.capture());
        assertThat(workspace.getValue().normalize().toString())
                .startsWith(Path.of("target/harness-workspaces").normalize().toString());
        assertThat(workspace.getValue().getNameCount())
                .isEqualTo(Path.of("target/harness-workspaces").getNameCount() + 2);
    }

    private static void assertCompaction(HarnessAgent.Builder builder) {
        ArgumentCaptor<CompactionConfig> compaction =
                ArgumentCaptor.forClass(CompactionConfig.class);
        verify(builder).compaction(compaction.capture());
        assertThat(compaction.getValue().getTriggerMessages()).isEqualTo(24);
        assertThat(compaction.getValue().getKeepMessages()).isEqualTo(6);
    }

    private static AiSkill skill(String name) {
        return new AiSkill() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return name + " description";
            }

            @Override
            public boolean readOnly() {
                return true;
            }

            @Override
            public AiSkillResult call(Map<String, Object> arguments) {
                return AiSkillResult.ok(String.valueOf(arguments.get("text")));
            }
        };
    }
}