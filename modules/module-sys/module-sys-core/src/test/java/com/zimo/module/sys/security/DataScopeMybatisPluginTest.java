package com.zimo.module.sys.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.module.sys.annotation.DataScope;
import com.zimo.module.sys.context.PermissionCache;
import com.zimo.module.sys.context.UserPermissionContext;
import com.zimo.module.sys.enums.DataScopeEnum;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataScopeMybatisPluginTest {

    @AfterEach
    void tearDown() {
        PermissionCache.clear();
        PermissionCache.unbindCurrent();
        DataScopeMybatisPlugin.clearAccessProfile();
    }

    @Test
    void appendsFactoryConditionToSelectSql() throws Exception {
        bindUser(DataScopeEnum.FACTORY);
        DataScopeMybatisPlugin plugin = new DataScopeMybatisPlugin();

        DataScopeMybatisPlugin.RewriteResult result = plugin.rewriteSql(
                "select * from pm_project p",
                dataScope("selectProjects"),
                "10.0.0.8"
        );

        assertThat(result.getSql())
                .isEqualTo("select * from pm_project p WHERE p.dept_id = :permissionOrganizationId");
        assertThat(result.getParams()).containsEntry("permissionOrganizationId", "factory-01");
        assertThat(result.isFiltered()).isTrue();
    }

    @Test
    void appendsSelfConditionAfterExistingWhereBeforeOrderBy() throws Exception {
        bindUser(DataScopeEnum.SELF);
        DataScopeMybatisPlugin plugin = new DataScopeMybatisPlugin();

        DataScopeMybatisPlugin.RewriteResult result = plugin.rewriteSql(
                "select * from pm_project p where p.deleted = 0 order by p.id desc",
                dataScope("selectProjects"),
                "10.0.0.8"
        );

        assertThat(result.getSql())
                .isEqualTo("select * from pm_project p where p.deleted = 0 AND p.create_by = :permissionUserId order by p.id desc");
        assertThat(result.getParams()).containsEntry("permissionUserId", 7L);
    }

    @Test
    void choosesSmallestScopeAcrossRoles() throws Exception {
        bindUser(DataScopeEnum.ALL);
        DataScopeMybatisPlugin.setAccessProfile(DataScopeMybatisPlugin.AccessProfile.builder()
                .roleDataScopes(List.of(DataScopeEnum.GROUP, DataScopeEnum.SELF, DataScopeEnum.FACTORY))
                .build());
        DataScopeMybatisPlugin plugin = new DataScopeMybatisPlugin();

        DataScopeMybatisPlugin.RewriteResult result = plugin.rewriteSql(
                "select * from pm_project p",
                dataScope("selectProjects"),
                "10.0.0.8"
        );

        assertThat(result.getSql()).contains("p.create_by = :permissionUserId");
    }

    @Test
    void ignoresSqlWhenDataScopeTableDoesNotMatch() throws Exception {
        bindUser(DataScopeEnum.FACTORY);
        DataScopeMybatisPlugin plugin = new DataScopeMybatisPlugin();

        DataScopeMybatisPlugin.RewriteResult result = plugin.rewriteSql(
                "select * from wms_stock s",
                dataScope("selectProjects"),
                "10.0.0.8"
        );

        assertThat(result.getSql()).isEqualTo("select * from wms_stock s");
        assertThat(result.isFiltered()).isFalse();
    }

    @Test
    void appendsTrustedCustomSqlRuleForMatchedTable() throws Exception {
        bindUser(DataScopeEnum.FACTORY);
        DataScopeMybatisPlugin.setAccessProfile(DataScopeMybatisPlugin.AccessProfile.builder()
                .customSqlRules(List.of(new DataScopeMybatisPlugin.CustomSqlRule("pm_project", "p.status <> 'ARCHIVED'")))
                .build());
        DataScopeMybatisPlugin plugin = new DataScopeMybatisPlugin();

        DataScopeMybatisPlugin.RewriteResult result = plugin.rewriteSql(
                "select * from pm_project p",
                dataScope("selectProjects"),
                "10.0.0.8"
        );

        assertThat(result.getSql())
                .isEqualTo("select * from pm_project p WHERE (p.dept_id = :permissionOrganizationId) AND (p.status <> 'ARCHIVED')");
    }

    @Test
    void skipsFilteringForTemporaryAuthorizationSuperAdminAndIpWhitelist() throws Exception {
        bindUser(DataScopeEnum.SELF);
        DataScopeMybatisPlugin plugin = new DataScopeMybatisPlugin(List.of("127.0.0.1"));

        DataScopeMybatisPlugin.setAccessProfile(DataScopeMybatisPlugin.AccessProfile.builder()
                .temporaryAuthorized(true)
                .build());
        assertThat(plugin.rewriteSql("select * from pm_project p", dataScope("selectProjects"), "10.0.0.8").isFiltered())
                .isFalse();

        DataScopeMybatisPlugin.setAccessProfile(DataScopeMybatisPlugin.AccessProfile.builder()
                .superAdmin(true)
                .build());
        assertThat(plugin.rewriteSql("select * from pm_project p", dataScope("selectProjects"), "10.0.0.8").isFiltered())
                .isFalse();

        DataScopeMybatisPlugin.clearAccessProfile();
        assertThat(plugin.rewriteSql("select * from pm_project p", dataScope("selectProjects"), "127.0.0.1").isFiltered())
                .isFalse();
    }

    @Test
    void skipsFilteringForWildcardIpWhitelist() throws Exception {
        bindUser(DataScopeEnum.SELF);
        DataScopeMybatisPlugin plugin = new DataScopeMybatisPlugin(List.of("10.0.0.*"));

        DataScopeMybatisPlugin.RewriteResult result = plugin.rewriteSql(
                "select * from pm_project p",
                dataScope("selectProjects"),
                "10.0.0.88"
        );

        assertThat(result.isFiltered()).isFalse();
    }

    private static void bindUser(DataScopeEnum dataScope) {
        UserPermissionContext context = UserPermissionContext.of(
                7L,
                "zhangsan",
                "factory-01",
                "factory-one",
                Set.of("sys:role:list"),
                dataScope,
                Set.of()
        );
        PermissionCache.bindCurrent(context);
    }

    private static DataScope dataScope(String methodName) throws NoSuchMethodException {
        Method method = DemoMapper.class.getDeclaredMethod(methodName);
        return method.getAnnotation(DataScope.class);
    }

    private interface DemoMapper {

        @DataScope(value = "pm_project", tableAlias = "p")
        void selectProjects();
    }
}
