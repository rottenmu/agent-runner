package com.zimo.module.feishu.autoconfig;

import com.zimo.module.feishu.agent.FeishuAiChannelMessageHandler;
import com.zimo.module.feishu.agent.FeishuProjectCardRenderer;
import com.zimo.module.feishu.channel.FeishuAgentMessageHandler;
import com.zimo.module.feishu.config.FeishuConfigResponse;
import com.zimo.module.feishu.config.FeishuConfigService;
import com.zimo.module.feishu.gateway.FeishuAgentGatewayMessageHandler;
import com.zimo.starter.ai.autoconfig.AiAgentAutoConfiguration;
import com.zimo.starter.ai.channel.AiChannelHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeishuAiChannelAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeishuAutoConfiguration.class))
            .withPropertyValues(
                    "plugin.feishu.datasource.url=jdbc:sqlite:file:feishu-test?mode=memory&cache=shared",
                    "plugin.feishu.datasource.driver-class-name=org.sqlite.JDBC",
                    "plugin.feishu.datasource.username=",
                    "plugin.feishu.datasource.password=",
                    "feishu.app-id=test_app",
                    "feishu.app-secret=test_secret",
                    "feishu.verification-token=test_token",
                    "feishu.encrypt-key=test_encrypt_key",
                    "feishu.agent.channel.enabled=true",
                    "feishu.agent.channel.auto-start=false",
                    "feishu.agent.gateway.enabled=true",
                    "feishu.cli.enabled=true")
            .withBean(FeishuConfigService.class, () -> mock(FeishuConfigService.class))
            .withBean(AiChannelHandler.class, () -> mock(AiChannelHandler.class));

    @Test
    void readsActiveAgentBindingFromRuntimeConfigProvider() {
        FeishuConfigResponse activeConfig = new FeishuConfigResponse();
        activeConfig.setAgentId("a-bound");
        activeConfig.setTenantKey("tenant-1");
        FeishuConfigService configService = mock(FeishuConfigService.class);
        when(configService.getActiveConfigSummary()).thenReturn(activeConfig);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("configService", configService);
        ObjectProvider<FeishuConfigService> configServiceProvider =
                beanFactory.getBeanProvider(FeishuConfigService.class);
        RuntimeFeishuConfigProvider configProvider = new RuntimeFeishuConfigProvider(
                configServiceProvider,
                new FeishuProperties());

        assertThat(configProvider.getActiveAgentId()).isEqualTo("a-bound");
        assertThat(configProvider.getActiveAgentId("tenant-1")).isEqualTo("a-bound");
        assertThat(configProvider.getActiveAgentId("tenant-2")).isNull();
    }
    @Test
    void registersFeishuAiChannelHandlerWhenAiChannelHandlerExists() {
        runner.withPropertyValues("feishu.agent.ai-channel.enabled=true")
                .run(context -> {
            assertThat(context).hasSingleBean(FeishuAgentMessageHandler.class);
            assertThat(context).getBean(FeishuAgentMessageHandler.class)
                    .isInstanceOf(FeishuAiChannelMessageHandler.class);
        });
    }

    @Test
    void registersProjectCardRendererForAiChannel() {
        runner.withPropertyValues("feishu.agent.ai-channel.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(FeishuProjectCardRenderer.class);
                    assertThat(context).hasSingleBean(FeishuAgentMessageHandler.class);
                    assertThat(context).getBean(FeishuAgentMessageHandler.class)
                            .isInstanceOf(FeishuAiChannelMessageHandler.class);
                });
    }

    @Test
    void registersFeishuAiChannelHandlerWhenAiAutoConfigurationCreatesChannelHandler() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        FeishuAutoConfiguration.class, JacksonAutoConfiguration.class,
                        RestClientAutoConfiguration.class,
                        AiAgentAutoConfiguration.class))
                .withPropertyValues(
                        "ai.agent.enabled=true",
                        "ai.agent.api-key=",
                        "feishu.app-id=test_app",
                        "feishu.app-secret=test_secret",
                        "feishu.verification-token=test_token",
                        "feishu.encrypt-key=test_encrypt_key",
                        "feishu.agent.channel.enabled=true",
                        "feishu.agent.channel.auto-start=false",
                        "feishu.agent.gateway.enabled=true",
                        "feishu.agent.ai-channel.enabled=true",
                        "feishu.cli.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiChannelHandler.class);
                    assertThat(context).hasSingleBean(FeishuAgentMessageHandler.class);
                    assertThat(context).getBean(FeishuAgentMessageHandler.class)
                            .isInstanceOf(FeishuAiChannelMessageHandler.class);
                });
    }

    @Test
    void keepsGatewayHandlerByDefaultWhenAiChannelHandlerExists() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(FeishuAgentMessageHandler.class);
            assertThat(context).getBean(FeishuAgentMessageHandler.class)
                    .isInstanceOf(FeishuAgentGatewayMessageHandler.class);
        });
    }

    @Test
    void keepsGatewayHandlerWhenAiChannelExplicitlyDisabled() {
        runner.withPropertyValues("feishu.agent.ai-channel.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(FeishuAgentMessageHandler.class);
                    assertThat(context).getBean(FeishuAgentMessageHandler.class)
                            .isInstanceOf(FeishuAgentGatewayMessageHandler.class);
                });
    }
}
