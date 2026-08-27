package com.zimo.starter.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.starter.ai.AiAgentProperties;
import com.zimo.starter.ai.skill.AiSkillRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiAgentRuntimeFactoryTest {
    @Test
    void returnsNotConfiguredWhenApiKeyIsBlank() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setName("ai-agent");
        properties.setModelName("qwen-plus");
        properties.setApiKey("");
        AiSkillRegistry registry = new AiSkillRegistry(List.of());

        AiAgentRuntime runtime = new AiAgentRuntimeFactory().create(properties, registry);

        assertThat(runtime.status()).isEqualTo(AiAgentRuntimeStatus.NOT_CONFIGURED);
        assertThat(runtime.agentName()).isEqualTo("ai-agent");
        assertThat(runtime.modelName()).isEqualTo("qwen-plus");
        assertThat(runtime.message()).contains("AI 服务未配置");
        assertThat(runtime.message()).doesNotContain("Bearer");
    }

    @Test
    void returnsInitializationFailedWhenHarnessAgentClassIsMissing() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setApiKey("dummy-api-key");
        AiSkillRegistry registry = new AiSkillRegistry(List.of());
        AiAgentRuntimeFactory factory = new AiAgentRuntimeFactory() {
            @Override
            protected String harnessAgentClassName() {
                return "missing.HarnessAgent";
            }
        };

        AiAgentRuntime runtime = factory.create(properties, registry);

        assertThat(runtime.status()).isEqualTo(AiAgentRuntimeStatus.INITIALIZATION_FAILED);
        assertThat(runtime.message()).contains("AgentScope Java 依赖未加载");
        assertThat(runtime.message()).doesNotContain("dummy-api-key");
    }

    @Test
    void returnsReadyWithoutBuildingHarnessAgentDuringStartupCheck() {
        AiAgentProperties properties = new AiAgentProperties();
        properties.setApiKey("dummy-api-key");
        AiSkillRegistry registry = new AiSkillRegistry(List.of());
        AiAgentRuntimeFactory factory = new AiAgentRuntimeFactory() {
            @Override
            protected String harnessAgentClassName() {
                return FailingHarnessAgent.class.getName();
            }
        };

        AiAgentRuntime runtime = factory.create(properties, registry);

        assertThat(runtime.status()).isEqualTo(AiAgentRuntimeStatus.READY);
        assertThat(runtime.message()).doesNotContain("dummy-api-key");
    }

    public static class FailingHarnessAgent {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            public Builder name(String name) {
                return this;
            }

            public Builder sysPrompt(String sysPrompt) {
                return this;
            }

            public Builder model(String model) {
                throw new IllegalStateException("provider echoed dummy-api-key");
            }

            public Builder workspace(java.nio.file.Path workspace) {
                return this;
            }

            public Object build() {
                return new Object();
            }
        }
    }
}
