package com.zimo.module.feishu.autoconfig;

import com.zimo.module.feishu.admin.FeishuAdminPermissionGuard;
import com.zimo.module.feishu.admin.FeishuAgentAdminController;
import com.zimo.module.feishu.admin.FeishuAgentAdminService;
import com.zimo.module.feishu.agent.FeishuAgentCredentialService;
import com.zimo.module.feishu.channel.FeishuChannelClientManager;
import com.zimo.module.feishu.cli.bitable.FeishuBitableCliService;
import com.zimo.module.feishu.config.FeishuConfigService;
import com.zimo.module.feishu.log.FeishuMessageLogService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FeishuAdminAutoConfigurationTest {

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
                    "feishu.agent.channel.auto-start=false")
            .withBean(FeishuAgentCredentialService.class, () -> mock(FeishuAgentCredentialService.class))
            .withBean(FeishuConfigService.class, () -> mock(FeishuConfigService.class))
            .withBean(FeishuChannelClientManager.class, () -> mock(FeishuChannelClientManager.class))
            .withBean(FeishuBitableCliService.class, () -> mock(FeishuBitableCliService.class))
            .withBean(FeishuMessageLogService.class, () -> mock(FeishuMessageLogService.class));

    @Test
    void shouldRegisterAdminBeansByDefaultWhenFeishuEnabled() {
        contextRunner
                .withPropertyValues("feishu.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(FeishuAdminProperties.class)
                        .hasSingleBean(FeishuAdminPermissionGuard.class)
                        .hasSingleBean(FeishuAgentAdminService.class)
                        .hasSingleBean(FeishuAgentAdminController.class));
    }

    @Test
    void shouldNotRegisterAdminServiceAndControllerWhenAdminDisabled() {
        contextRunner
                .withPropertyValues(
                        "feishu.enabled=true",
                        "feishu.admin.enabled=false")
                .run(context -> assertThat(context)
                        .hasSingleBean(FeishuAdminProperties.class)
                        .doesNotHaveBean(FeishuAgentAdminService.class)
                        .doesNotHaveBean(FeishuAgentAdminController.class));
    }

    @Test
    void shouldPassConfiguredApiTokenToPermissionGuard() {
        contextRunner
                .withPropertyValues(
                        "feishu.enabled=true",
                        "feishu.admin.api-token=secret-token")
                .run(context -> {
                    FeishuAdminPermissionGuard guard = context.getBean(FeishuAdminPermissionGuard.class);

                    assertThat(guard.isAllowed("secret-token")).isTrue();
                    assertThat(guard.isAllowed("wrong-token")).isFalse();
                });
    }
}
