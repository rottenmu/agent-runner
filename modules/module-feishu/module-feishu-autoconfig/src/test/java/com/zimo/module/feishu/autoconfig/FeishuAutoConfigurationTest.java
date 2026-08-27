package com.zimo.module.feishu.autoconfig;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lark.oapi.Client;
import com.zimo.module.feishu.FeishuPluginRegister;
import com.zimo.module.feishu.config.FeishuConfigController;
import com.zimo.module.feishu.config.FeishuConfigService;
import com.zimo.module.feishu.event.FeishuEventController;
import com.zimo.module.feishu.event.FeishuEventHandler;
import com.zimo.module.feishu.event.FeishuEventProperties;
import javax.sql.DataSource;
import com.zimo.module.feishu.message.FeishuMessageClient;
import com.zimo.module.feishu.message.FeishuMessageService;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.mybatis.spring.annotation.MapperScan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeishuAutoConfigurationTest {

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
                    "feishu.agent.channel.auto-start=false");

    @Test
    void bindsPropertiesAndCreatesCoreBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FeishuProperties.class);
            assertThat(context).hasSingleBean(Client.class);
            assertThat(context).hasSingleBean(FeishuPluginRegister.class);
            assertThat(context).hasSingleBean(FeishuMessageClient.class);
            assertThat(context).hasSingleBean(FeishuMessageService.class);
            assertThat(context).hasSingleBean(FeishuEventProperties.class);
            assertThat(context).hasSingleBean(FeishuEventHandler.class);
            assertThat(context).hasSingleBean(FeishuEventController.class);

            FeishuProperties properties = context.getBean(FeishuProperties.class);
            assertThat(properties.getAppId()).isEqualTo("cli_test_app");
            assertThat(properties.getAppSecret()).isEqualTo("test_secret");
            assertThat(properties.getVerificationToken()).isEqualTo("test_token");
            assertThat(properties.getEncryptKey()).isEqualTo("test_encrypt_key");
            assertThat(properties.isEnabled()).isTrue();
        });
    }

    @Test
    void registersConfigManagementEndpointWhenMybatisIsAvailable() {
        contextRunner
                .withBean(SqlSessionFactory.class, FeishuAutoConfigurationTest::sqlSessionFactory)
                .run(context -> {
                    assertThat(context).hasSingleBean(FeishuConfigService.class);
                    assertThat(context).hasSingleBean(FeishuConfigController.class);
                });
    }

    @Test
    void mapperScanUsesFeishuSqlSessionFactory() {
        MapperScan mapperScan = FeishuAutoConfiguration.FeishuMapperConfiguration.class
                .getAnnotation(MapperScan.class);

        assertThat(mapperScan).isNotNull();
        assertThat(mapperScan.markerInterface()).isEqualTo(BaseMapper.class);
        assertThat(mapperScan.sqlSessionFactoryRef()).isEqualTo("feishuSqlSessionFactory");
        assertThat(mapperScan.basePackages()).containsExactly(
                "com.zimo.module.feishu.mapper",
                "com.zimo.module.feishu.mapping",
                "com.zimo.module.feishu.log",
                "com.zimo.module.feishu.cli");
    }

    @Test
    void readsFeishuDatasourceFromModuleApplicationYaml() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FeishuAutoConfiguration.class))
                .withPropertyValues(
                        "spring.datasource.url=jdbc:mysql://localhost:3306/admin_shell",
                        "spring.datasource.username=admin",
                        "feishu.app-id=cli_test_app",
                        "feishu.app-secret=test_secret",
                        "feishu.agent.channel.auto-start=false")
                .run(context -> {
                    assertThat(context).hasBean("feishuDataSourceProperties");
                    FeishuDataSourceProperties properties = context.getBean(
                            "feishuDataSourceProperties", FeishuDataSourceProperties.class);

                    assertThat(properties.url()).startsWith("jdbc:sqlite:");
                    assertThat(properties.driverClassName()).isEqualTo("org.sqlite.JDBC");
                });
    }

    @Test
    void rejectsEmbeddedDatabaseConnectionUrls() {
        FeishuDataSourceProperties properties = new FeishuDataSourceProperties(
                "jdbc:h2:mem:feishu_module",
                "feishu_user",
                "",
                "org.h2.Driver");

        assertThatThrownBy(properties::validateSupported)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only MySQL or SQLite is supported");
    }

    @Test
    void feishuTransactionAndSqlSessionFactoryShareModuleDataSource() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FeishuAutoConfiguration.class))
                .withPropertyValues(
                        "plugin.feishu.datasource.url=jdbc:sqlite:file:feishu-test?mode=memory&cache=shared",
                        "plugin.feishu.datasource.driver-class-name=org.sqlite.JDBC",
                        "plugin.feishu.datasource.username=",
                        "plugin.feishu.datasource.password=",
                        "feishu.app-id=cli_test_app",
                        "feishu.app-secret=test_secret",
                        "feishu.agent.channel.auto-start=false")
                .run(context -> {
                    DataSource dataSource = context.getBean("feishuDataSource", DataSource.class);
                    DataSourceTransactionManager transactionManager = context.getBean(
                            "feishuTransactionManager", DataSourceTransactionManager.class);
                    SqlSessionFactory sqlSessionFactory = context.getBean(
                            "feishuSqlSessionFactory", SqlSessionFactory.class);

                    assertThat(transactionManager.getDataSource()).isSameAs(dataSource);
                    assertThat(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource())
                            .isSameAs(dataSource);
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
