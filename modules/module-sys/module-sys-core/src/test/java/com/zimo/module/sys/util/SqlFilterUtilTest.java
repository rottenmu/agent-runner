package com.zimo.module.sys.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zimo.module.sys.enums.DataScopeEnum;
import org.junit.jupiter.api.Test;

class SqlFilterUtilTest {

    @Test
    void buildsEmptyConditionForAllDataScope() {
        SqlFilterUtil.SqlCondition condition = SqlFilterUtil.buildDataScopeCondition(
                DataScopeEnum.ALL,
                "p",
                "factory_id",
                "create_by",
                "factory-01",
                7L
        );

        assertThat(condition.isEmpty()).isTrue();
        assertThat(SqlFilterUtil.appendWhereCondition("select * from pm_project", condition))
                .isEqualTo("select * from pm_project");
    }

    @Test
    void buildsOrganizationConditionForFactoryGroupAndWorkshopScopes() {
        SqlFilterUtil.SqlCondition condition = SqlFilterUtil.buildDataScopeCondition(
                DataScopeEnum.FACTORY,
                "p",
                "factory_id",
                "create_by",
                "factory-01",
                7L
        );

        assertThat(condition.getSql()).isEqualTo("p.factory_id = :permissionOrganizationId");
        assertThat(condition.getParams()).containsEntry("permissionOrganizationId", "factory-01");
    }

    @Test
    void buildsSelfConditionForSelfDataScope() {
        SqlFilterUtil.SqlCondition condition = SqlFilterUtil.buildDataScopeCondition(
                DataScopeEnum.SELF,
                "p",
                "factory_id",
                "create_by",
                "factory-01",
                7L
        );

        assertThat(condition.getSql()).isEqualTo("p.create_by = :permissionUserId");
        assertThat(condition.getParams()).containsEntry("permissionUserId", 7L);
    }

    @Test
    void appendsConditionToSqlWithOrWithoutExistingWhereClause() {
        SqlFilterUtil.SqlCondition condition = SqlFilterUtil.SqlCondition.of(
                "p.factory_id = :permissionOrganizationId",
                "permissionOrganizationId",
                "factory-01"
        );

        assertThat(SqlFilterUtil.appendWhereCondition("select * from pm_project p", condition))
                .isEqualTo("select * from pm_project p WHERE p.factory_id = :permissionOrganizationId");
        assertThat(SqlFilterUtil.appendWhereCondition("select * from pm_project p where p.deleted = 0", condition))
                .isEqualTo("select * from pm_project p where p.deleted = 0 AND p.factory_id = :permissionOrganizationId");
    }

    @Test
    void buildsOrganizationHierarchyCondition() {
        SqlFilterUtil.SqlCondition condition = SqlFilterUtil.buildOrganizationHierarchyCondition(
                "p",
                "org_path",
                "001/002/"
        );

        assertThat(condition.getSql()).isEqualTo("p.org_path LIKE :permissionOrganizationPath");
        assertThat(condition.getParams()).containsEntry("permissionOrganizationPath", "001/002/%");
    }

    @Test
    void rejectsUnsafeSqlIdentifiers() {
        assertThatThrownBy(() -> SqlFilterUtil.buildOrganizationHierarchyCondition(
                "p;drop",
                "org_path",
                "001/"
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> SqlFilterUtil.buildDataScopeCondition(
                DataScopeEnum.FACTORY,
                "p",
                "factory_id or 1=1",
                "create_by",
                "factory-01",
                7L
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
