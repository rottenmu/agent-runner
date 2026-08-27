package com.zimo.module.feishu.admin;

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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class FeishuAgentAdminService {
    private static final int CLI_OUTPUT_MAX_LENGTH = 2000;

    private final FeishuAgentCredentialService credentialService;
    private final FeishuConfigService configService;
    private final FeishuChannelClientManager channelClientManager;
    private final FeishuBitableCliService bitableCliService;
    private final FeishuMessageLogService messageLogService;

    public FeishuAgentAdminService(
            FeishuAgentCredentialService credentialService,
            FeishuConfigService configService,
            FeishuChannelClientManager channelClientManager,
            FeishuBitableCliService bitableCliService,
            FeishuMessageLogService messageLogService) {
        this.credentialService = credentialService;
        this.configService = configService;
        this.channelClientManager = channelClientManager;
        this.bitableCliService = bitableCliService;
        this.messageLogService = messageLogService;
    }

    public FeishuTenantScanInitResponse initTenantScan(FeishuTenantScanInitRequest request) {
        return credentialService.initTenantScan(request);
    }

    public FeishuChannelStatusResponse getChannelStatus() {
        FeishuConfigResponse config = configService.getActiveConfigSummary();
        boolean configured = config != null && hasText(config.getAppId());
        boolean enabled = config != null && Integer.valueOf(1).equals(config.getEnabled());
        boolean running = channelClientManager.isRunning();

        FeishuChannelStatusResponse response = new FeishuChannelStatusResponse();
        response.setConfigured(configured);
        response.setEnabled(enabled);
        response.setRunning(running);
        response.setAppId(configured ? config.getAppId() : null);
        response.setTenantKey(configured ? config.getTenantKey() : null);
        response.setTenantName(configured ? config.getTenantName() : null);
        response.setCredentialStatus(configured ? config.getCredentialStatus() : null);
        response.setMessage(toChannelStatusMessage(configured, running));
        return response;
    }

    public FeishuCliDebugResponse writeBitableTest(FeishuBitableWriteTestRequest request) {
        String appToken = requireText(request == null ? null : request.getAppToken(), "appToken must not be blank");
        String tableId = requireText(request.getTableId(), "tableId must not be blank");
        Map<String, Object> fields = request.getFields();
        if (fields == null || fields.isEmpty()) {
            fields = defaultDebugFields();
        }
        FeishuCliCommandResult result = bitableCliService.createRecord(
                new BitableRecordCreateRequest(appToken, tableId, fields));
        return toCliDebugResponse(result);
    }

    public Page<FeishuMessageLogEntity> pageMessageLogs(FeishuMessageLogPageRequest request) {
        return messageLogService.page(request);
    }

    public FeishuRobotSwitchResponse switchRobot(FeishuRobotSwitchRequest request) {
        if (request != null && request.isEnabled()) {
            FeishuConfigResponse config = request.getConfigId() == null
                    ? configService.getActiveConfigSummary()
                    : configService.enable(request.getConfigId());
            if (config == null) {
                return toSwitchResponse(null, false, false, "未配置启用中的飞书应用");
            }
            channelClientManager.start();
            return toSwitchResponse(config, channelClientManager.isRunning(), true, "飞书机器人已启用");
        }
        channelClientManager.stop();
        FeishuConfigResponse config = configService.disableActive();
        return toSwitchResponse(config, channelClientManager.isRunning(), false, "飞书机器人已停用");
    }

    private static FeishuRobotSwitchResponse toSwitchResponse(FeishuConfigResponse config,
                                                              boolean running,
                                                              boolean enabled,
                                                              String message) {
        FeishuRobotSwitchResponse response = new FeishuRobotSwitchResponse();
        response.setEnabled(enabled);
        response.setRunning(running);
        response.setConfigId(config == null ? null : config.getId());
        response.setMessage(message);
        return response;
    }

    private static String toChannelStatusMessage(boolean configured, boolean running) {
        if (!configured) {
            return "Feishu robot channel is not configured";
        }
        if (running) {
            return "Feishu robot channel is running";
        }
        return "Feishu robot channel is stopped";
    }

    private static Map<String, Object> defaultDebugFields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("debugSource", "feishu-agent-admin");
        fields.put("debugMessage", "Feishu bitable write test");
        fields.put("debugAt", LocalDateTime.now().toString());
        return fields;
    }

    private static FeishuCliDebugResponse toCliDebugResponse(FeishuCliCommandResult result) {
        FeishuCliDebugResponse response = new FeishuCliDebugResponse();
        response.setSuccess(result.isSuccess());
        response.setExitCode(result.getExitCode());
        response.setStdout(truncate(result.getStdout()));
        response.setStderr(truncate(result.getStderr()));
        response.setErrorMessage(truncate(result.getErrorMessage()));
        response.setJson(result.getJson());
        response.setCostMillis(result.getCostMillis());
        response.setAttempts(result.getAttempts());
        return response;
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= CLI_OUTPUT_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, CLI_OUTPUT_MAX_LENGTH);
    }
}
