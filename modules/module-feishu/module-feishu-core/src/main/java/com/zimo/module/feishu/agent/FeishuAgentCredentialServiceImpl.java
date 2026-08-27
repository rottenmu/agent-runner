package com.zimo.module.feishu.agent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zimo.module.feishu.agent.dto.FeishuCredentialRefreshRequest;
import com.zimo.module.feishu.agent.dto.FeishuCredentialValidateResponse;
import com.zimo.module.feishu.agent.dto.FeishuTenantCredentialResponse;
import com.zimo.module.feishu.agent.dto.FeishuTenantScanInitRequest;
import com.zimo.module.feishu.agent.dto.FeishuTenantScanInitResponse;
import com.zimo.module.feishu.config.FeishuConfigEntity;
import com.zimo.module.feishu.config.FeishuConfigRequest;
import com.zimo.module.feishu.config.FeishuConfigResponse;
import com.zimo.module.feishu.config.FeishuConfigService;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class FeishuAgentCredentialServiceImpl implements FeishuAgentCredentialService {
    private static final int DEFAULT_QUERY_SIZE = 1000;
    private static final String MASKED = "******";

    private final FeishuAppCreationClient appCreationClient;
    private final FeishuConfigService configService;
    private final FeishuAgentCredentialValidator validator;

    public FeishuAgentCredentialServiceImpl(
            FeishuAppCreationClient appCreationClient,
            FeishuConfigService configService) {
        this(appCreationClient, configService, new FeishuAgentCredentialValidator());
    }

    public FeishuAgentCredentialServiceImpl(
            FeishuAppCreationClient appCreationClient,
            FeishuConfigService configService,
            FeishuAgentCredentialValidator validator) {
        this.appCreationClient = Objects.requireNonNull(appCreationClient, "appCreationClient must not be null");
        this.configService = Objects.requireNonNull(configService, "configService must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    @Override
    public FeishuTenantScanInitResponse initTenantScan(FeishuTenantScanInitRequest request) {
        requireText(request.getTenantName(), "tenantName must not be blank");
        requireText(request.getAppName(), "appName must not be blank");
        FeishuAppCreationResult result = appCreationClient.initScan(FeishuAppCreationRequest.from(request));
        requireText(result.getAppId(), "feishu appId must not be blank");
        requireText(result.getAppSecret(), "feishu appSecret must not be blank");

        FeishuConfigRequest configRequest = new FeishuConfigRequest();
        configRequest.setConfigName(request.getTenantName());
        configRequest.setTenantName(request.getTenantName());
        configRequest.setAppId(result.getAppId());
        configRequest.setAppSecret(result.getAppSecret());
        configRequest.setEnabled(1);
        configRequest.setCredentialStatus("VALID");
        configRequest.setScanState("CREATED");
        configRequest.setScanTicket(result.getScanTicket());
        configRequest.setPermissionScopes(join(request.getPermissionScopes()));
        configRequest.setEventSubscriptions(join(request.getEventSubscriptions()));
        configRequest.setRemark("飞书 Agent 扫码创建");
        configService.create(configRequest);

        return new FeishuTenantScanInitResponse(
                result.getScanUrl(),
                result.getScanTicket(),
                result.getExpireSeconds(),
                nullToEmpty(request.getPermissionScopes()),
                nullToEmpty(request.getEventSubscriptions()));
    }

    @Override
    public List<FeishuTenantCredentialResponse> listCredentials() {
        Page<FeishuConfigResponse> page = configService.page(1, DEFAULT_QUERY_SIZE, null, null, null);
        return page.getRecords().stream().map(this::toCredentialResponse).toList();
    }

    @Override
    public FeishuTenantCredentialResponse getCredential(Long id) {
        return toCredentialResponse(configService.get(id));
    }

    @Override
    public void deleteCredential(Long id) {
        configService.delete(id);
    }

    @Override
    public FeishuCredentialValidateResponse validateCredential(Long id) {
        FeishuCredentialValidateResponse response = validator.validate(configService.getRaw(id));
        configService.updateCredentialStatus(id, response.isValid() ? "VALID" : "INVALID", response.getValidateTime());
        return response;
    }

    @Override
    public FeishuTenantCredentialResponse refreshSecret(Long id, FeishuCredentialRefreshRequest request) {
        requireText(request.getAppSecret(), "appSecret must not be blank");
        FeishuConfigEntity existing = configService.getRaw(id);
        FeishuConfigRequest updateRequest = new FeishuConfigRequest();
        updateRequest.setConfigName(existing.getConfigName());
        updateRequest.setTenantKey(existing.getTenantKey());
        updateRequest.setTenantName(existing.getTenantName());
        updateRequest.setAppId(existing.getAppId());
        updateRequest.setAppSecret(request.getAppSecret());
        updateRequest.setVerificationToken(request.getVerificationToken());
        updateRequest.setEncryptKey(request.getEncryptKey());
        updateRequest.setEnabled(existing.getEnabled());
        updateRequest.setCredentialStatus("VALID");
        updateRequest.setScanState(existing.getScanState());
        updateRequest.setScanTicket(existing.getScanTicket());
        updateRequest.setPermissionScopes(existing.getPermissionScopes());
        updateRequest.setEventSubscriptions(existing.getEventSubscriptions());
        updateRequest.setRemark(existing.getRemark());
        return toCredentialResponse(configService.update(id, updateRequest));
    }

    private FeishuTenantCredentialResponse toCredentialResponse(FeishuConfigResponse config) {
        FeishuTenantCredentialResponse response = new FeishuTenantCredentialResponse();
        response.setId(config.getId());
        response.setTenantKey(config.getTenantKey());
        response.setTenantName(StringUtils.hasText(config.getTenantName()) ? config.getTenantName() : config.getConfigName());
        response.setAppId(config.getAppId());
        response.setAppSecret(StringUtils.hasText(config.getAppSecret()) ? MASKED : "");
        response.setCredentialStatus(config.getCredentialStatus());
        response.setEnabled(config.getEnabled());
        response.setLastValidateTime(config.getLastValidateTime());
        response.setLastRefreshTime(config.getLastRefreshTime());
        return response;
    }

    private static String join(List<String> values) {
        return values == null ? "" : String.join(",", values);
    }

    private static List<String> nullToEmpty(List<String> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }
}
