package com.zimo.module.sys.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PermissionEnumContractTest {

    @Test
    void dataScopeEnumCoversManufacturingDataScopeLevels() {
        assertThat(DataScopeEnum.values())
                .extracting(DataScopeEnum::getCode)
                .containsExactly("all", "group", "factory", "workshop", "self");

        assertThat(DataScopeEnum.FACTORY.getDescription()).contains("工厂");
        assertThat(DataScopeEnum.fromCode("workshop")).isEqualTo(DataScopeEnum.WORKSHOP);
        assertThat(DataScopeEnum.SELF.matchCode("self")).isTrue();
    }

    @Test
    void permOperTypeEnumCoversCommonApiOperationTypes() {
        assertThat(PermOperTypeEnum.values())
                .extracting(PermOperTypeEnum::getCode)
                .contains("add", "edit", "delete", "export", "audit");

        assertThat(PermOperTypeEnum.ADD.getDescription()).isEqualTo("新增");
        assertThat(PermOperTypeEnum.fromCode("audit")).isEqualTo(PermOperTypeEnum.AUDIT);
        assertThat(PermOperTypeEnum.DELETE.matchCode("delete")).isTrue();
    }

    @Test
    void dataLevelEnumCoversPublicInternalAndConfidentialLevels() {
        assertThat(DataLevelEnum.values())
                .extracting(DataLevelEnum::getCode)
                .containsExactly("public", "internal", "confidential");

        assertThat(DataLevelEnum.CONFIDENTIAL.getDescription()).contains("部门主管级");
        assertThat(DataLevelEnum.fromCode("internal")).isEqualTo(DataLevelEnum.INTERNAL);
        assertThat(DataLevelEnum.PUBLIC.matchCode("public")).isTrue();
    }
}
