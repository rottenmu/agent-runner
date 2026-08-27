package com.zimo.module.feishu.autoconfig;

import com.zimo.module.feishu.channel.FeishuAgentMessageHandler;
import com.zimo.module.feishu.channel.FeishuChannelMessageListener;
import com.zimo.module.feishu.config.FeishuConfigService;
import com.zimo.module.feishu.reply.FeishuAgentReplyService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.SmartLifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FeishuAgentChannelAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeishuAutoConfiguration.class))
            .withPropertyValues(
                    "plugin.feishu.datasource.url=jdbc:sqlite:file:feishu-test?mode=memory&cache=shared",
                    "plugin.feishu.datasource.driver-class-name=org.sqlite.JDBC",
                    "plugin.feishu.datasource.username=",
                    "plugin.feishu.datasource.password=",
                    "feishu.app-id=cli_test_app",
                    "feishu.app-secret=test_secret",
                    "feishu.verification-token=test_token",
                    "feishu.encrypt-key=test_encrypt_key",
                    "feishu.agent.channel.enabled=true",
                    "feishu.agent.channel.auto-start=false")
            .withBean(FeishuConfigService.class, () -> mock(FeishuConfigService.class));

    @Test
    void registersChannelBeansWhenEnabled() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(FeishuAgentChannelProperties.class);
            assertThat(context).hasSingleBean(FeishuAgentReplyService.class);
            assertThat(context).hasSingleBean(FeishuAgentMessageHandler.class);
            assertThat(context).hasSingleBean(FeishuChannelMessageListener.class);
            assertThat(context).hasSingleBean(SmartLifecycle.class);
        });
    }

    @Test
    void doesNotRegisterChannelListenerWhenDisabled() {
        runner.withPropertyValues("feishu.agent.channel.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FeishuChannelMessageListener.class));
    }
}
