package com.zimo.module.feishu.autoconfig;

import com.zimo.module.feishu.channel.FeishuAgentMessageHandler;
import com.zimo.module.feishu.channel.NoopFeishuAgentMessageHandler;
import com.zimo.module.feishu.config.FeishuConfigService;
import com.zimo.module.feishu.gateway.FeishuAgentCommandRouter;
import com.zimo.module.feishu.gateway.FeishuAgentDispatchService;
import com.zimo.module.feishu.gateway.FeishuAgentGatewayMessageHandler;
import com.zimo.module.feishu.gateway.FeishuAgentRateLimiter;
import com.zimo.module.feishu.gateway.FeishuAgentResultCardFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FeishuAgentGatewayAutoConfigurationTest {

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
                    "feishu.cli.enabled=true")
            .withBean(FeishuConfigService.class, () -> mock(FeishuConfigService.class));

    @Test
    void registersGatewayBeansWhenEnabled() {
        runner.withPropertyValues("feishu.agent.gateway.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(FeishuAgentGatewayProperties.class);
                    assertThat(context).hasSingleBean(FeishuAgentCommandRouter.class);
                    assertThat(context).hasSingleBean(FeishuAgentRateLimiter.class);
                    assertThat(context).hasSingleBean(FeishuAgentResultCardFactory.class);
                    assertThat(context).hasSingleBean(FeishuAgentDispatchService.class);
                    assertThat(context).hasSingleBean(FeishuAgentMessageHandler.class);
                    assertThat(context).getBean(FeishuAgentMessageHandler.class)
                            .isInstanceOf(FeishuAgentGatewayMessageHandler.class);
                });
    }

    @Test
    void keepsNoopHandlerWhenGatewayDisabled() {
        runner.withPropertyValues("feishu.agent.gateway.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FeishuAgentDispatchService.class);
                    assertThat(context).hasSingleBean(FeishuAgentMessageHandler.class);
                    assertThat(context).getBean(FeishuAgentMessageHandler.class)
                            .isInstanceOf(NoopFeishuAgentMessageHandler.class);
                });
    }

    @Test
    void keepsNoopHandlerWhenCliDisabled() {
        runner.withPropertyValues("feishu.cli.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FeishuAgentDispatchService.class);
                    assertThat(context).hasSingleBean(FeishuAgentMessageHandler.class);
                    assertThat(context).getBean(FeishuAgentMessageHandler.class)
                            .isInstanceOf(NoopFeishuAgentMessageHandler.class);
                });
    }

    @Test
    void skipsGatewayBeansWhenChannelDisabled() {
        runner.withPropertyValues("feishu.agent.channel.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FeishuAgentDispatchService.class);
                    assertThat(context).doesNotHaveBean(FeishuAgentMessageHandler.class);
                });
    }

    @Test
    void bindsGatewayDefaultProperties() {
        runner.run(context -> {
            FeishuAgentGatewayProperties properties = context.getBean(FeishuAgentGatewayProperties.class);

            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.isRateLimitEnabled()).isTrue();
            assertThat(properties.getRateLimitWindowSeconds()).isEqualTo(60);
            assertThat(properties.getRateLimitMaxRequests()).isEqualTo(10);
            assertThat(properties.isArchiveEnabled()).isTrue();
            assertThat(properties.isProgressReplyEnabled()).isTrue();
            assertThat(properties.getArchiveAppToken()).isNull();
            assertThat(properties.getArchiveTableId()).isNull();
        });
    }
}
