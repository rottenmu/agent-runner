package com.zimo.module.feishu.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zimo.module.feishu.admin.dto.FeishuBitableWriteTestRequest;
import com.zimo.module.feishu.admin.dto.FeishuChannelStatusResponse;
import com.zimo.module.feishu.admin.dto.FeishuCliDebugResponse;
import com.zimo.module.feishu.admin.dto.FeishuMessageLogPageRequest;
import com.zimo.module.feishu.admin.dto.FeishuRobotSwitchRequest;
import com.zimo.module.feishu.admin.dto.FeishuRobotSwitchResponse;
import com.zimo.module.feishu.agent.FeishuAgentCredentialService;
import com.zimo.module.feishu.agent.dto.FeishuTenantScanInitRequest;
import com.zimo.module.feishu.agent.dto.FeishuTenantScanInitResponse;
import com.zimo.module.feishu.channel.FeishuChannelClientManager;
import com.zimo.module.feishu.cli.FeishuCliCommandResult;
import com.zimo.module.feishu.cli.bitable.BitableRecordCreateRequest;
import com.zimo.module.feishu.cli.bitable.FeishuBitableCliService;
import com.zimo.module.feishu.config.FeishuConfigResponse;
import com.zimo.module.feishu.config.FeishuConfigService;
import com.zimo.module.feishu.log.FeishuMessageLogEntity;
import com.zimo.module.feishu.log.FeishuMessageLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeishuAgentAdminServiceTest {

    @Test
    void shouldDelegateTenantScanInit() {
        FeishuAgentCredentialService credentialService = mock(FeishuAgentCredentialService.class);
        FeishuTenantScanInitRequest request = new FeishuTenantScanInitRequest();
        FeishuTenantScanInitResponse expected = new FeishuTenantScanInitResponse();
        when(credentialService.initTenantScan(request)).thenReturn(expected);
        FeishuAgentAdminService service = newService(credentialService);

        FeishuTenantScanInitResponse response = service.initTenantScan(request);

        assertThat(response).isSameAs(expected);
        verify(credentialService).initTenantScan(request);
    }

    @Test
    void shouldReturnChannelStatusWithoutSecrets() throws JsonProcessingException {
        FeishuConfigService configService = mock(FeishuConfigService.class);
        FeishuChannelClientManager channelManager = mock(FeishuChannelClientManager.class);
        FeishuConfigResponse config = new FeishuConfigResponse();
        config.setId(10L);
        config.setAppId("cli_xxx");
        config.setAppSecret("******");
        config.setVerificationToken("******");
        config.setEncryptKey("******");
        config.setEnabled(1);
        config.setTenantKey("tenant_a");
        config.setTenantName("测试租户");
        config.setCredentialStatus("VALID");
        when(configService.getActiveConfigSummary()).thenReturn(config);
        when(channelManager.isRunning()).thenReturn(true);
        FeishuAgentAdminService service = newService(configService, channelManager);

        FeishuChannelStatusResponse response = service.getChannelStatus();

        assertThat(response.isConfigured()).isTrue();
        assertThat(response.isEnabled()).isTrue();
        assertThat(response.isRunning()).isTrue();
        assertThat(response.getAppId()).isEqualTo("cli_xxx");
        assertThat(response.getTenantKey()).isEqualTo("tenant_a");
        assertThat(response.getTenantName()).isEqualTo("测试租户");
        assertThat(response.getCredentialStatus()).isEqualTo("VALID");
        assertThat(response.getMessage()).isEqualTo("Feishu robot channel is running");
        String json = new ObjectMapper().writeValueAsString(response);
        assertThat(json).doesNotContain("app-secret-plaintext");
        assertThat(json).doesNotContain("verification-token-plaintext");
        assertThat(json).doesNotContain("encrypt-key-plaintext");
    }

    @Test
    void shouldWriteBitableDebugRecordWithDefaultFieldsWhenFieldsEmpty() {
        FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
        when(bitableCliService.createRecord(any(BitableRecordCreateRequest.class)))
                .thenReturn(FeishuCliCommandResult.success("created", new ObjectMapper().createObjectNode(), 25L, 1));
        FeishuAgentAdminService service = newService(bitableCliService);
        FeishuBitableWriteTestRequest request = new FeishuBitableWriteTestRequest();
        request.setAppToken("app_token");
        request.setTableId("tbl_abc");

        FeishuCliDebugResponse response = service.writeBitableTest(request);

        ArgumentCaptor<BitableRecordCreateRequest> captor = ArgumentCaptor.forClass(BitableRecordCreateRequest.class);
        verify(bitableCliService).createRecord(captor.capture());
        assertThat(captor.getValue().getAppToken()).isEqualTo("app_token");
        assertThat(captor.getValue().getTableId()).isEqualTo("tbl_abc");
        assertThat(captor.getValue().getFields())
                .containsEntry("debugSource", "feishu-agent-admin")
                .containsKey("debugMessage");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCostMillis()).isEqualTo(25L);
        assertThat(response.getAttempts()).isEqualTo(1);
        assertThat(response.getStdout()).isEqualTo("created");
    }

    @Test
    void shouldWriteBitableDebugRecordWithProvidedFields() {
        FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
        when(bitableCliService.createRecord(any(BitableRecordCreateRequest.class)))
                .thenReturn(FeishuCliCommandResult.failure(1, "", "bad", "failed", 30L, 2));
        FeishuAgentAdminService service = newService(bitableCliService);
        FeishuBitableWriteTestRequest request = new FeishuBitableWriteTestRequest();
        request.setAppToken("app_token");
        request.setTableId("tbl_abc");
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", "debug record");
        request.setFields(fields);

        FeishuCliDebugResponse response = service.writeBitableTest(request);

        ArgumentCaptor<BitableRecordCreateRequest> captor = ArgumentCaptor.forClass(BitableRecordCreateRequest.class);
        verify(bitableCliService).createRecord(captor.capture());
        assertThat(captor.getValue().getFields()).containsExactlyEntriesOf(fields);
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getExitCode()).isEqualTo(1);
        assertThat(response.getStderr()).isEqualTo("bad");
        assertThat(response.getErrorMessage()).isEqualTo("failed");
    }

    @Test
    void shouldRejectBitableWriteWhenRequiredFieldsBlank() {
        FeishuAgentAdminService service = newService(mock(FeishuBitableCliService.class));
        FeishuBitableWriteTestRequest request = new FeishuBitableWriteTestRequest();
        request.setAppToken(" ");
        request.setTableId("tbl_abc");

        assertThatThrownBy(() -> service.writeBitableTest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("appToken must not be blank");

        request.setAppToken("app_token");
        request.setTableId("");

        assertThatThrownBy(() -> service.writeBitableTest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tableId must not be blank");
    }

    @Test
    void shouldTruncateCliDebugOutputToTwoThousandCharacters() {
        FeishuBitableCliService bitableCliService = mock(FeishuBitableCliService.class);
        String longText = "x".repeat(2050);
        when(bitableCliService.createRecord(any(BitableRecordCreateRequest.class)))
                .thenReturn(FeishuCliCommandResult.failure(2, longText, longText, longText, 40L, 3));
        FeishuAgentAdminService service = newService(bitableCliService);
        FeishuBitableWriteTestRequest request = new FeishuBitableWriteTestRequest();
        request.setAppToken("app_token");
        request.setTableId("tbl_abc");

        FeishuCliDebugResponse response = service.writeBitableTest(request);

        assertThat(response.getStdout()).hasSize(2000);
        assertThat(response.getStderr()).hasSize(2000);
        assertThat(response.getErrorMessage()).hasSize(2000);
    }

    @Test
    void shouldDelegateMessageLogPageQuery() {
        FeishuMessageLogService messageLogService = mock(FeishuMessageLogService.class);
        FeishuMessageLogPageRequest request = new FeishuMessageLogPageRequest();
        Page<FeishuMessageLogEntity> expected = new Page<>(2, 5);
        when(messageLogService.page(request)).thenReturn(expected);
        FeishuAgentAdminService service = new FeishuAgentAdminService(
                mock(FeishuAgentCredentialService.class),
                mock(FeishuConfigService.class),
                mock(FeishuChannelClientManager.class),
                mock(FeishuBitableCliService.class),
                messageLogService);

        Page<FeishuMessageLogEntity> response = service.pageMessageLogs(request);

        assertThat(response).isSameAs(expected);
        verify(messageLogService).page(request);
    }

    @Test
    void shouldEnableRobotAndStartChannelWithTargetConfig() {
        FeishuConfigService configService = mock(FeishuConfigService.class);
        FeishuChannelClientManager channelManager = mock(FeishuChannelClientManager.class);
        FeishuConfigResponse enabledConfig = new FeishuConfigResponse();
        enabledConfig.setId(10L);
        enabledConfig.setEnabled(1);
        when(configService.enable(10L)).thenReturn(enabledConfig);
        when(channelManager.isRunning()).thenReturn(true);
        FeishuAgentAdminService service = newService(configService, channelManager);
        FeishuRobotSwitchRequest request = new FeishuRobotSwitchRequest();
        request.setEnabled(true);
        request.setConfigId(10L);

        FeishuRobotSwitchResponse response = service.switchRobot(request);

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.isRunning()).isTrue();
        assertThat(response.getConfigId()).isEqualTo(10L);
        assertThat(response.getMessage()).isEqualTo("飞书机器人已启用");
        verify(configService).enable(10L);
        verify(channelManager).start();
    }

    @Test
    void shouldEnableRobotWithActiveSummaryWhenConfigIdMissing() {
        FeishuConfigService configService = mock(FeishuConfigService.class);
        FeishuChannelClientManager channelManager = mock(FeishuChannelClientManager.class);
        FeishuConfigResponse activeConfig = new FeishuConfigResponse();
        activeConfig.setId(11L);
        activeConfig.setEnabled(1);
        when(configService.getActiveConfigSummary()).thenReturn(activeConfig);
        when(channelManager.isRunning()).thenReturn(true);
        FeishuAgentAdminService service = newService(configService, channelManager);
        FeishuRobotSwitchRequest request = new FeishuRobotSwitchRequest();
        request.setEnabled(true);

        FeishuRobotSwitchResponse response = service.switchRobot(request);

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.isRunning()).isTrue();
        assertThat(response.getConfigId()).isEqualTo(11L);
        assertThat(response.getMessage()).isEqualTo("飞书机器人已启用");
        verify(configService).getActiveConfigSummary();
        verify(channelManager).start();
    }

    @Test
    void shouldNotStartRobotWhenNoActiveConfigExists() {
        FeishuConfigService configService = mock(FeishuConfigService.class);
        FeishuChannelClientManager channelManager = mock(FeishuChannelClientManager.class);
        when(configService.getActiveConfigSummary()).thenReturn(null);
        FeishuAgentAdminService service = newService(configService, channelManager);
        FeishuRobotSwitchRequest request = new FeishuRobotSwitchRequest();
        request.setEnabled(true);

        FeishuRobotSwitchResponse response = service.switchRobot(request);

        assertThat(response.isEnabled()).isFalse();
        assertThat(response.isRunning()).isFalse();
        assertThat(response.getConfigId()).isNull();
        assertThat(response.getMessage()).isEqualTo("未配置启用中的飞书应用");
        verify(configService).getActiveConfigSummary();
        verify(channelManager, never()).start();
    }

    @Test
    void shouldDisableRobotAndStopChannel() {
        FeishuConfigService configService = mock(FeishuConfigService.class);
        FeishuChannelClientManager channelManager = mock(FeishuChannelClientManager.class);
        FeishuConfigResponse disabledConfig = new FeishuConfigResponse();
        disabledConfig.setId(10L);
        disabledConfig.setEnabled(0);
        when(configService.disableActive()).thenReturn(disabledConfig);
        when(channelManager.isRunning()).thenReturn(false);
        FeishuAgentAdminService service = newService(configService, channelManager);
        FeishuRobotSwitchRequest request = new FeishuRobotSwitchRequest();
        request.setEnabled(false);

        FeishuRobotSwitchResponse response = service.switchRobot(request);

        assertThat(response.isEnabled()).isFalse();
        assertThat(response.isRunning()).isFalse();
        assertThat(response.getConfigId()).isEqualTo(10L);
        assertThat(response.getMessage()).isEqualTo("飞书机器人已停用");
        verify(channelManager).stop();
        verify(configService).disableActive();
    }

    private FeishuAgentAdminService newService(FeishuAgentCredentialService credentialService) {
        return new FeishuAgentAdminService(
                credentialService,
                mock(FeishuConfigService.class),
                mock(FeishuChannelClientManager.class),
                mock(FeishuBitableCliService.class),
                mock(FeishuMessageLogService.class));
    }

    private FeishuAgentAdminService newService(FeishuConfigService configService,
                                              FeishuChannelClientManager channelManager) {
        return new FeishuAgentAdminService(
                mock(FeishuAgentCredentialService.class),
                configService,
                channelManager,
                mock(FeishuBitableCliService.class),
                mock(FeishuMessageLogService.class));
    }

    private FeishuAgentAdminService newService(FeishuBitableCliService bitableCliService) {
        return new FeishuAgentAdminService(
                mock(FeishuAgentCredentialService.class),
                mock(FeishuConfigService.class),
                mock(FeishuChannelClientManager.class),
                bitableCliService,
                mock(FeishuMessageLogService.class));
    }
}
