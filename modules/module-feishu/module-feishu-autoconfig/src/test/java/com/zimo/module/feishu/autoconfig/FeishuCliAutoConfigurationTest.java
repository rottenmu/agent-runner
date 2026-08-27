package com.zimo.module.feishu.autoconfig;

import com.zimo.module.feishu.cli.FeishuCliExecutor;
import com.zimo.module.feishu.cli.FeishuCliPolicy;
import com.zimo.module.feishu.cli.FeishuCliTemplate;
import com.zimo.module.feishu.cli.bitable.FeishuBitableCliService;
import com.zimo.module.feishu.cli.calendar.FeishuCalendarCliService;
import com.zimo.module.feishu.cli.document.FeishuDocumentCliService;
import com.zimo.module.feishu.cli.task.FeishuTaskCliService;
import com.zimo.module.feishu.config.FeishuConfigService;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeishuCliAutoConfigurationTest {

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
                    "feishu.agent.channel.auto-start=false")
            .withBean(FeishuConfigService.class, () -> mock(FeishuConfigService.class));

    @Test
    void registersCliBeansWhenEnabled() {
        runner.withPropertyValues("feishu.cli.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(FeishuCliProperties.class);
                    assertThat(context).hasSingleBean(FeishuCliExecutor.class);
                    assertThat(context).hasSingleBean(FeishuCliPolicy.class);
                    assertThat(context).hasSingleBean(FeishuCliTemplate.class);
                    assertThat(context).hasSingleBean(FeishuBitableCliService.class);
                    assertThat(context).hasSingleBean(FeishuDocumentCliService.class);
                    assertThat(context).hasSingleBean(FeishuCalendarCliService.class);
                    assertThat(context).hasSingleBean(FeishuTaskCliService.class);
                });
    }

    @Test
    void skipsCliBeansWhenDisabled() {
        runner.withPropertyValues("feishu.cli.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(FeishuCliTemplate.class));
    }

    @Test
    void doesNotRegisterBusinessCliExecutorInterfaceAsMybatisMapper() {
        runner.withBean(SqlSessionFactory.class, FeishuCliAutoConfigurationTest::sqlSessionFactory)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FeishuCliExecutor.class);
                    assertThat(context).doesNotHaveBean("com.zimo.module.feishu.cli.FeishuCliExecutor");
                });
    }

    private static SqlSessionFactory sqlSessionFactory() {
        SqlSessionFactory sqlSessionFactory = mock(SqlSessionFactory.class);
        Configuration configuration = new Configuration();
        configuration.setEnvironment(new Environment(
                "test",
                new JdbcTransactionFactory(),
                mock(DataSource.class)));
        when(sqlSessionFactory.getConfiguration()).thenReturn(configuration);
        return sqlSessionFactory;
    }
}
