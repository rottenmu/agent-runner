package com.zimo.module.feishu.agent;

import com.zimo.module.feishu.agent.dto.FeishuTenantScanInitRequest;
import com.zimo.module.feishu.agent.dto.FeishuTenantScanInitResponse;
import com.zimo.module.feishu.config.FeishuConfigRequest;
import com.zimo.module.feishu.config.FeishuConfigResponse;
import com.zimo.module.feishu.config.FeishuConfigService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeishuAgentCredentialServiceTest {
    private FeishuAppCreationClient appCreationClient;
    private FeishuConfigService configService;
    private FeishuAgentCredentialService service;

    @BeforeEach
    void setUp() {
        appCreationClient = mock(FeishuAppCreationClient.class);
        configService = mock(FeishuConfigService.class);
        service = new FeishuAgentCredentialServiceImpl(appCreationClient, configService);
    }

    @Test
    void initTenantScanCreatesEnabledCredentialAndReturnsScanInfo() {
        when(appCreationClient.initScan(any())).thenReturn(new FeishuAppCreationResult(
                "https://open.feishu.cn/app/create?q=scan",
                "scan_ticket_1",
                600,
                "cli_created",
                "secret_created"
        ));
        when(configService.create(any())).thenReturn(maskedConfig());

        FeishuTenantScanInitRequest request = new FeishuTenantScanInitRequest();
        request.setTenantName("生产租户");
        request.setAppName("智能体运行平台 Agent");
        request.setPermissionScopes(List.of("im:message"));
        request.setEventSubscriptions(List.of("im.message.receive_v1"));

        FeishuTenantScanInitResponse response = service.initTenantScan(request);

        ArgumentCaptor<FeishuConfigRequest> captor = ArgumentCaptor.forClass(FeishuConfigRequest.class);
        verify(configService).create(captor.capture());
        assertThat(captor.getValue().getAppId()).isEqualTo("cli_created");
        assertThat(captor.getValue().getAppSecret()).isEqualTo("secret_created");
        assertThat(captor.getValue().getEnabled()).isEqualTo(1);
        assertThat(response.getScanUrl()).contains("open.feishu.cn");
        assertThat(response.getScanTicket()).isEqualTo("scan_ticket_1");
    }

    private static FeishuConfigResponse maskedConfig() {
        FeishuConfigResponse response = new FeishuConfigResponse();
        response.setId(1L);
        response.setConfigName("生产租户");
        response.setAppId("cli_created");
        response.setAppSecret("******");
        response.setEnabled(1);
        return response;
    }
}
