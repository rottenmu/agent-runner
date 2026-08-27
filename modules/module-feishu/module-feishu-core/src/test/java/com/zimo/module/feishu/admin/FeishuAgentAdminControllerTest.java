package com.zimo.module.feishu.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zimo.framework.common.ApiResponse;
import com.zimo.module.feishu.admin.dto.FeishuBitableWriteTestRequest;
import com.zimo.module.feishu.admin.dto.FeishuChannelStatusResponse;
import com.zimo.module.feishu.admin.dto.FeishuCliDebugResponse;
import com.zimo.module.feishu.admin.dto.FeishuMessageLogPageRequest;
import com.zimo.module.feishu.admin.dto.FeishuRobotSwitchRequest;
import com.zimo.module.feishu.admin.dto.FeishuRobotSwitchResponse;
import com.zimo.module.feishu.agent.dto.FeishuTenantScanInitRequest;
import com.zimo.module.feishu.agent.dto.FeishuTenantScanInitResponse;
import com.zimo.module.feishu.log.FeishuMessageLogEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeishuAgentAdminControllerTest {

    @Test
    void shouldRejectWhenAdminTokenInvalid() {
        FeishuAgentAdminController controller = new FeishuAgentAdminController(
                new FeishuAdminPermissionGuard("admin-token"),
                mock(FeishuAgentAdminService.class));

        ApiResponse<FeishuChannelStatusResponse> response = controller.channelStatus("wrong-token");

        assertThat(response.getCode()).isEqualTo(403);
        assertThat(response.getMsg()).isEqualTo("forbidden");
    }

    @Test
    void shouldDelegateTenantScanInitWhenAllowed() {
        FeishuAgentAdminService service = mock(FeishuAgentAdminService.class);
        FeishuTenantScanInitRequest request = new FeishuTenantScanInitRequest();
        FeishuTenantScanInitResponse expected = new FeishuTenantScanInitResponse();
        when(service.initTenantScan(request)).thenReturn(expected);
        FeishuAgentAdminController controller = newAllowedController(service);

        ApiResponse<FeishuTenantScanInitResponse> response = controller.initTenantScan(null, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isSameAs(expected);
        verify(service).initTenantScan(request);
    }

    @Test
    void shouldDelegateChannelStatusWhenAllowed() {
        FeishuAgentAdminService service = mock(FeishuAgentAdminService.class);
        FeishuChannelStatusResponse expected = new FeishuChannelStatusResponse();
        expected.setRunning(true);
        when(service.getChannelStatus()).thenReturn(expected);
        FeishuAgentAdminController controller = newAllowedController(service);

        ApiResponse<FeishuChannelStatusResponse> response = controller.channelStatus(null);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isSameAs(expected);
        verify(service).getChannelStatus();
    }

    @Test
    void shouldDelegateBitableWriteTestWhenAllowed() {
        FeishuAgentAdminService service = mock(FeishuAgentAdminService.class);
        FeishuBitableWriteTestRequest request = new FeishuBitableWriteTestRequest();
        FeishuCliDebugResponse expected = new FeishuCliDebugResponse();
        when(service.writeBitableTest(request)).thenReturn(expected);
        FeishuAgentAdminController controller = newAllowedController(service);

        ApiResponse<FeishuCliDebugResponse> response = controller.writeBitableTest(null, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isSameAs(expected);
        verify(service).writeBitableTest(request);
    }

    @Test
    void shouldDelegateMessageLogsWhenAllowed() {
        FeishuAgentAdminService service = mock(FeishuAgentAdminService.class);
        FeishuMessageLogPageRequest request = new FeishuMessageLogPageRequest();
        Page<FeishuMessageLogEntity> expected = new Page<>(1, 10);
        when(service.pageMessageLogs(request)).thenReturn(expected);
        FeishuAgentAdminController controller = newAllowedController(service);

        ApiResponse<Page<FeishuMessageLogEntity>> response = controller.messageLogs(null, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isSameAs(expected);
        verify(service).pageMessageLogs(request);
    }

    @Test
    void shouldDelegateRobotSwitchWhenAllowed() {
        FeishuAgentAdminService service = mock(FeishuAgentAdminService.class);
        FeishuRobotSwitchRequest request = new FeishuRobotSwitchRequest();
        FeishuRobotSwitchResponse expected = new FeishuRobotSwitchResponse();
        when(service.switchRobot(request)).thenReturn(expected);
        FeishuAgentAdminController controller = newAllowedController(service);

        ApiResponse<FeishuRobotSwitchResponse> response = controller.switchRobot(null, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isSameAs(expected);
        verify(service).switchRobot(request);
    }

    private static FeishuAgentAdminController newAllowedController(FeishuAgentAdminService service) {
        return new FeishuAgentAdminController(new FeishuAdminPermissionGuard(""), service);
    }
}
