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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/biz/feishu/admin")
public class FeishuAgentAdminController {

    private static final String ADMIN_TOKEN_HEADER = "X-Feishu-Admin-Token";

    private final FeishuAdminPermissionGuard permissionGuard;
    private final FeishuAgentAdminService adminService;

    public FeishuAgentAdminController(FeishuAdminPermissionGuard permissionGuard,
                                      FeishuAgentAdminService adminService) {
        this.permissionGuard = permissionGuard;
        this.adminService = adminService;
    }

    @PostMapping("/tenant-scan/init")
    public ApiResponse<FeishuTenantScanInitResponse> initTenantScan(
            @RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @RequestBody FeishuTenantScanInitRequest request) {
        if (!permissionGuard.isAllowed(adminToken)) {
            return ApiResponse.fail(403, "forbidden");
        }
        return ApiResponse.ok(adminService.initTenantScan(request));
    }

    @GetMapping("/channel/status")
    public ApiResponse<FeishuChannelStatusResponse> channelStatus(
            @RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String adminToken) {
        if (!permissionGuard.isAllowed(adminToken)) {
            return ApiResponse.fail(403, "forbidden");
        }
        return ApiResponse.ok(adminService.getChannelStatus());
    }

    @PostMapping("/cli/bitable/write-test")
    public ApiResponse<FeishuCliDebugResponse> writeBitableTest(
            @RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @RequestBody FeishuBitableWriteTestRequest request) {
        if (!permissionGuard.isAllowed(adminToken)) {
            return ApiResponse.fail(403, "forbidden");
        }
        return ApiResponse.ok(adminService.writeBitableTest(request));
    }

    @GetMapping("/message-logs/page")
    public ApiResponse<Page<FeishuMessageLogEntity>> messageLogs(
            @RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @ModelAttribute FeishuMessageLogPageRequest request) {
        if (!permissionGuard.isAllowed(adminToken)) {
            return ApiResponse.fail(403, "forbidden");
        }
        return ApiResponse.ok(adminService.pageMessageLogs(request));
    }

    @PutMapping("/robot/enabled")
    public ApiResponse<FeishuRobotSwitchResponse> switchRobot(
            @RequestHeader(value = ADMIN_TOKEN_HEADER, required = false) String adminToken,
            @RequestBody FeishuRobotSwitchRequest request) {
        if (!permissionGuard.isAllowed(adminToken)) {
            return ApiResponse.fail(403, "forbidden");
        }
        return ApiResponse.ok(adminService.switchRobot(request));
    }
}
