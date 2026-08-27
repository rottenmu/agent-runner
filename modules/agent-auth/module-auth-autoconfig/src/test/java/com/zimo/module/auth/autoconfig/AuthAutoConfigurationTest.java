package com.zimo.module.auth.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zimo.module.auth.security.SysStpInterface;
import com.zimo.module.auth.service.SysRbacService;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AuthAutoConfigurationTest {

    private static final String SYS_AUTO_CONFIGURATION =
            "com.zimo.module.sys.autoconfig.SysAutoConfiguration";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuthAutoConfiguration.class))
            .withBean(SqlSessionFactory.class, AuthAutoConfigurationTest::sqlSessionFactory);

    @Test
    void startsWithoutRbacServiceWhenOnlyAuthStarterIsEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SysStpInterface.class);
        });
    }

    @Test
    void createsStpInterfaceWhenRbacServiceExists() {
        contextRunner
                .withBean(SysRbacService.class, EmptyRbacService::new)
                .run(context -> assertThat(context).hasSingleBean(SysStpInterface.class));
    }

    @Test
    void declaresThatItRunsAfterSysAutoConfiguration() {
        AutoConfiguration autoConfiguration = AuthAutoConfiguration.class.getAnnotation(AutoConfiguration.class);

        assertThat(autoConfiguration).isNotNull();
        assertThat(autoConfiguration.afterName()).contains(SYS_AUTO_CONFIGURATION);
    }

    private static final class EmptyRbacService implements SysRbacService {
        @Override public List<Long> getUserRoleIds(Long userId) { return List.of(); }
        @Override public void assignUserRoles(Long userId, List<Long> roleIds) { }
        @Override public List<Long> getRoleMenuIds(Long roleId) { return List.of(); }
        @Override public void assignRoleMenus(Long roleId, List<Long> menuIds) { }
        @Override public List<String> getRoleKeysByUserId(Long userId) { return List.of(); }
        @Override public List<String> getPermissionsByUserId(Long userId) { return List.of(); }
    }

    private static SqlSessionFactory sqlSessionFactory() {
        SqlSessionFactory sqlSessionFactory = mock(SqlSessionFactory.class);
        Environment environment = new Environment("test", new JdbcTransactionFactory(), mock(DataSource.class));
        when(sqlSessionFactory.getConfiguration()).thenReturn(new Configuration(environment));
        return sqlSessionFactory;
    }
}
