package com.zimo.module.sys.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zimo.module.sys.annotation.FieldDesensitize;
import com.zimo.module.sys.enums.DataLevelEnum;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FieldMaskServiceTest {

    @AfterEach
    void tearDown() {
        FieldMaskService.clearAccessProfile();
    }

    @Test
    void masksAnnotatedPrivacyAndConfidentialManufacturingFieldsForPublicUser() {
        FieldMaskService.setAccessProfile(FieldMaskService.AccessProfile.builder()
                .dataLevel(DataLevelEnum.PUBLIC)
                .build());
        FieldMaskService service = new FieldMaskService();
        ProjectCostView view = new ProjectCostView(
                "13812348000",
                "1299.80",
                "880.00",
                "heat-treatment-process",
                "normal"
        );

        ProjectCostView result = service.mask(view);

        assertThat(result.phone).isEqualTo("138****8000");
        assertThat(result.price).isEqualTo("****");
        assertThat(result.cost).isEqualTo("****");
        assertThat(result.process).isEqualTo("h********************s");
        assertThat(result.remark).isEqualTo("normal");
    }

    @Test
    void keepsConfidentialFieldsForConfidentialUserButStillMasksPhone() {
        FieldMaskService.setAccessProfile(FieldMaskService.AccessProfile.builder()
                .dataLevel(DataLevelEnum.CONFIDENTIAL)
                .build());
        FieldMaskService service = new FieldMaskService();
        ProjectCostView view = new ProjectCostView(
                "13812348000",
                "1299.80",
                "880.00",
                "heat-treatment-process",
                "normal"
        );

        ProjectCostView result = service.mask(view);

        assertThat(result.phone).isEqualTo("138****8000");
        assertThat(result.price).isEqualTo("1299.80");
        assertThat(result.cost).isEqualTo("880.00");
        assertThat(result.process).isEqualTo("heat-treatment-process");
    }

    @Test
    void skipsMaskingWhenGlobalSwitchDisabled() {
        FieldMaskService service = new FieldMaskService(false);
        ProjectCostView view = new ProjectCostView(
                "13812348000",
                "1299.80",
                "880.00",
                "heat-treatment-process",
                "normal"
        );

        service.mask(view);

        assertThat(view.phone).isEqualTo("13812348000");
        assertThat(view.price).isEqualTo("1299.80");
    }

    @Test
    void masksCollectionItemsAndSupportsTemporarilyAllowedFields() {
        FieldMaskService.setAccessProfile(FieldMaskService.AccessProfile.builder()
                .dataLevel(DataLevelEnum.PUBLIC)
                .allowedFields(List.of("price"))
                .build());
        FieldMaskService service = new FieldMaskService();
        List<ProjectCostView> views = new ArrayList<>();
        views.add(new ProjectCostView("13812348000", "1299.80", "880.00", "process-a", "one"));
        views.add(new ProjectCostView("13912348000", "2299.80", "1880.00", "process-b", "two"));

        service.mask(views);

        assertThat(views).extracting(item -> item.price).containsExactly("1299.80", "2299.80");
        assertThat(views).extracting(item -> item.cost).containsExactly("****", "****");
        assertThat(views).extracting(item -> item.phone).containsExactly("138****8000", "139****8000");
    }

    private static class ProjectCostView {

        @FieldDesensitize(type = FieldDesensitize.Type.PHONE)
        private String phone;

        @FieldDesensitize(type = FieldDesensitize.Type.CUSTOM)
        private String price;

        @FieldDesensitize(type = FieldDesensitize.Type.CUSTOM)
        private String cost;

        @FieldDesensitize(type = FieldDesensitize.Type.DEFAULT)
        private String process;

        private String remark;

        private ProjectCostView(String phone, String price, String cost, String process, String remark) {
            this.phone = phone;
            this.price = price;
            this.cost = cost;
            this.process = process;
            this.remark = remark;
        }
    }
}
