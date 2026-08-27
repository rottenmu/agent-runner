package com.zimo.module.sys.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.module.sys.enums.DataScopeEnum;
import com.zimo.module.sys.security.DataScopeMybatisPlugin;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataScopeServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-07-01T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void resolvesSmallestDataScopeAcrossRolesByIntersectionRule() {
        DataScopeService service = new DataScopeService(fixedClock);

        DataScopeEnum scope = service.resolveEffectiveScope(List.of(
                DataScopeService.RoleDataScope.of("group-admin", 100, DataScopeEnum.GROUP),
                DataScopeService.RoleDataScope.of("factory-user", 50, DataScopeEnum.FACTORY),
                DataScopeService.RoleDataScope.of("self-user", 10, DataScopeEnum.SELF)
        ));

        assertThat(scope).isEqualTo(DataScopeEnum.SELF);
    }

    @Test
    void comparesDataScopePriorityFromWideToNarrow() {
        DataScopeService service = new DataScopeService(fixedClock);

        assertThat(service.isNarrowerOrEqual(DataScopeEnum.FACTORY, DataScopeEnum.GROUP)).isTrue();
        assertThat(service.isNarrowerOrEqual(DataScopeEnum.GROUP, DataScopeEnum.FACTORY)).isFalse();
        assertThat(service.minScope(DataScopeEnum.ALL, DataScopeEnum.WORKSHOP)).isEqualTo(DataScopeEnum.WORKSHOP);
    }

    @Test
    void activeTemporaryAuthorizationCanBypassDataFilterAndExpiredOneIsIgnored() {
        DataScopeService service = new DataScopeService(fixedClock);
        DataScopeService.TemporaryAuthorization active = DataScopeService.TemporaryAuthorization.of(
                "TEMP-001",
                DataScopeEnum.ALL,
                Instant.parse("2026-07-01T09:00:00Z")
        );
        DataScopeService.TemporaryAuthorization expired = DataScopeService.TemporaryAuthorization.of(
                "TEMP-002",
                DataScopeEnum.ALL,
                Instant.parse("2026-07-01T07:59:59Z")
        );

        assertThat(service.isTemporaryAuthorizationValid(active)).isTrue();
        assertThat(service.isTemporaryAuthorizationValid(expired)).isFalse();
        assertThat(service.resolveTemporaryScope(DataScopeEnum.SELF, active)).isEqualTo(DataScopeEnum.ALL);
        assertThat(service.resolveTemporaryScope(DataScopeEnum.SELF, expired)).isEqualTo(DataScopeEnum.SELF);
    }

    @Test
    void buildsDataScopePluginAccessProfileWithRoleScopesAndTemporaryAuthorization() {
        DataScopeService service = new DataScopeService(fixedClock);
        DataScopeService.TemporaryAuthorization active = DataScopeService.TemporaryAuthorization.of(
                "TEMP-003",
                DataScopeEnum.ALL,
                Instant.parse("2026-07-01T09:00:00Z")
        );

        DataScopeMybatisPlugin.AccessProfile profile = service.buildAccessProfile(
                List.of(
                        DataScopeService.RoleDataScope.of("factory-user", 10, DataScopeEnum.FACTORY),
                        DataScopeService.RoleDataScope.of("workshop-user", 20, DataScopeEnum.WORKSHOP)
                ),
                List.of(new DataScopeMybatisPlugin.CustomSqlRule("pm_project", "p.status <> 'ARCHIVED'")),
                active,
                false,
                "10.0.0.8"
        );

        assertThat(profile.getRoleDataScopes()).containsExactly(DataScopeEnum.WORKSHOP, DataScopeEnum.FACTORY);
        assertThat(profile.getCustomSqlRules()).hasSize(1);
        assertThat(profile.isTemporaryAuthorized()).isTrue();
        assertThat(profile.isSuperAdmin()).isFalse();
        assertThat(profile.getRequestIp()).isEqualTo("10.0.0.8");
    }
}
