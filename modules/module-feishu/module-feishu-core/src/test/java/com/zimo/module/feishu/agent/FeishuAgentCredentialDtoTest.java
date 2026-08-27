package com.zimo.module.feishu.agent;

import com.zimo.module.feishu.agent.dto.FeishuTenantScanInitRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAgentCredentialDtoTest {
    @Test
    void scanRequestCarriesTenantPermissionsAndEvents() {
        FeishuTenantScanInitRequest request = new FeishuTenantScanInitRequest();
        request.setTenantName("生产租户");
        request.setAppName("智能体运行平台 Agent");
        request.setPermissionScopes(List.of("im:message", "sheets:spreadsheet", "docs:document"));
        request.setEventSubscriptions(List.of("im.message.receive_v1"));

        assertThat(request.getTenantName()).isEqualTo("生产租户");
        assertThat(request.getPermissionScopes()).contains("im:message", "sheets:spreadsheet", "docs:document");
        assertThat(request.getEventSubscriptions()).contains("im.message.receive_v1");
    }
}
