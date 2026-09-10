package com.zimo.module.feishu.autoconfig;

import com.zimo.module.feishu.agent.FeishuAgentCredentialController;
import com.zimo.module.feishu.agent.FeishuAgentCredentialService;
import com.zimo.module.feishu.agent.FeishuAppCreationClient;
import com.zimo.module.feishu.config.FeishuConfigService;
import com.zimo.module.feishu.config.FeishuSchemaInitializer;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FeishuAgentCredentialAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
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
                    "feishu.agent.credential.enabled=true",
                    "feishu.agent.channel.auto-start=false")
            .withBean(FeishuConfigService.class, () -> mock(FeishuConfigService.class));

    @Test
    void registersAgentCredentialBeansWhenEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FeishuAgentCredentialProperties.class);
            assertThat(context).hasSingleBean(FeishuAppCreationClient.class);
            assertThat(context).hasSingleBean(FeishuAgentCredentialService.class);
            assertThat(context).hasSingleBean(FeishuAgentCredentialController.class);
        });
    }

    @Test
    void registersSchemaInitializerWhenDataSourceExists() {
        contextRunner
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .run(context -> assertThat(context).hasSingleBean(FeishuSchemaInitializer.class));
    }
}
