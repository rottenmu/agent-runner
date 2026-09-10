package com.zimo.framework.ai.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.framework.ai.AiAgentProperties;
import com.zimo.framework.ai.AiAgentService;
import com.zimo.framework.ai.agent.AiHarnessAgentFactory;
import com.zimo.module.agentmemory.memory.AiMemoryService;
import com.zimo.framework.ai.channel.AiChannelHandler;
import com.zimo.framework.ai.chat.AiChatClient;
import com.zimo.framework.ai.chat.AiChatResponse;
import com.zimo.framework.ai.runtime.AiAgentRuntime;
import com.zimo.framework.ai.runtime.AiAgentRuntimeFactory;
import com.zimo.framework.ai.runtime.AiAgentRuntimeStatus;
import com.zimo.framework.ai.skill.AiSkillDescriptor;
import com.zimo.framework.ai.skill.AiSkillRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.client.RestClient;

class AiAgentAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestClientAutoConfiguration.class,
                    JacksonAutoConfiguration.class,
                    AiAgentAutoConfiguration.class));

    @Test
    void createsPropertiesAndDefaultSkills() {
        contextRunner
                .withPropertyValues(
                        "ai.agent.enabled=true",
                        "ai.agent.name=ai-agent",
                        "ai.agent.model-name=qwen3.7-max",
                        "ai.agent.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiAgentProperties.class);
                    assertThat(context.getBean(AiAgentProperties.class).getModelName()).isEqualTo("qwen3.7-max");
                    assertThat(context).hasSingleBean(AiSkillRegistry.class);
                    assertThat(context.getBean(AiSkillRegistry.class).list())
                            .extracting(AiSkillDescriptor::name)
                            .contains("echo", "summarize", "generate_plan", "route_plugin_task");
                });
    }

    @Test
    void createsRuntimeBeansWithoutManagementBeans() {
        contextRunner
                .withPropertyValues("ai.agent.enabled=true", "ai.agent.api-key=")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiAgentRuntimeFactory.class);
                    assertThat(context).hasSingleBean(AiAgentRuntime.class);
                    assertThat(context).hasSingleBean(RestClient.Builder.class);
                    assertThat(context).hasSingleBean(AiChatClient.class);
                    assertThat(context).hasSingleBean(AiAgentService.class);
                    assertThat(context).hasSingleBean(AiChannelHandler.class);
                    assertThat(context.getBean(AiAgentRuntime.class).status())
                            .isEqualTo(AiAgentRuntimeStatus.NOT_CONFIGURED);
                });
    }

    @Test
    void backsOffWhenCustomChatClientExists() {
        contextRunner
                .withBean(AiAgentRuntime.class, () -> new AiAgentRuntime(
                        "ai-agent", "qwen-plus", "dashscope_chat", List.of(),
                        AiAgentRuntimeStatus.READY, "ready"))
                .withBean(AiChatClient.class, () -> request -> AiChatResponse.ok("custom"))
                .withPropertyValues("ai.agent.enabled=true")
                .run(context -> {
                    AiAgentService service = context.getBean(AiAgentService.class);
                    assertThat(service.reply("hello").content()).isEqualTo("custom");
                    assertThat(context).hasSingleBean(AiChatClient.class);
                });
    }

    /**
     * 回归护栏：未引入 module-agent-memory（上下文中不存在 AiMemoryService Bean）时，自动装配仍须成功，
     * HarnessAgent 工厂照常创建，仅跳过记忆读写工具注册。
     * 改造前该方法参数是硬依赖 {@code AiMemoryService}，此场景会在上下文启动阶段直接失败。
     */
    @Test
    void createsHarnessAgentFactoryWithoutMemoryService() {
        contextRunner
                .withPropertyValues("ai.agent.enabled=true", "ai.agent.api-key=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AiMemoryService.class);
                    assertThat(context).hasSingleBean(AiHarnessAgentFactory.class);
                });
    }
}