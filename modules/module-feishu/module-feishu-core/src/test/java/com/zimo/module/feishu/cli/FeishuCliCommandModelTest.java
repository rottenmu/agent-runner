package com.zimo.module.feishu.cli;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuCliCommandModelTest {

    @Test
    void createsStructuredApiRequestWithoutHardcodedJson() {
        FeishuCliCommandRequest request = FeishuCliCommandRequest
                .api("bitable", "POST", "/open-apis/bitable/v1/apps/app_token/tables/table_id/records")
                .withData("fields", Map.of("name", "样件A"))
                .withParam("page_size", 20);

        assertThat(request.getBusinessType()).isEqualTo("bitable");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getApiPath()).contains("/open-apis/bitable");
        assertThat(request.getParams()).containsEntry("page_size", 20);
        assertThat(request.getData()).containsKey("fields");
    }
}
