package com.zimo.module.feishu.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zimo.module.feishu.mapper.FeishuConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.time.LocalDateTime;

@Service
public class FeishuConfigServiceImpl implements FeishuConfigService {
    private static final String MASKED = "******";
    private static final int MAX_AGENT_ID_LENGTH = 64;

    private final FeishuConfigMapper mapper;

    public FeishuConfigServiceImpl(FeishuConfigMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    /**
     * 按名称、App ID、启用状态和绑定状态分页查询飞书配置，并对凭据字段脱敏。
     *
     * @param current 当前页，从 1 开始
     * @param size 每页条数
     * @param configName 配置名称，允许为空
     * @param appId 飞书 App ID，允许为空
     * @param enabled 启用状态，允许为空
     * @param bound 绑定状态；true 查询已绑定，false 查询未绑定，null 不筛选
     * @return 脱敏后的飞书配置分页结果
     */
    @Override
    public Page<FeishuConfigResponse> page(
            long current,
            long size,
            String configName,
            String appId,
            Integer enabled,
            Boolean bound) {
        LambdaQueryWrapper<FeishuConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(configName), FeishuConfigEntity::getConfigName, configName)
                .like(StringUtils.hasText(appId), FeishuConfigEntity::getAppId, appId)
                .eq(enabled != null, FeishuConfigEntity::getEnabled, enabled);
        if (Boolean.TRUE.equals(bound)) {
            wrapper.isNotNull(FeishuConfigEntity::getAgentId);
        } else if (Boolean.FALSE.equals(bound)) {
            wrapper.isNull(FeishuConfigEntity::getAgentId);
        }
        wrapper.orderByDesc(FeishuConfigEntity::getUpdateTime);
        Page<FeishuConfigEntity> entityPage = mapper.selectPage(new Page<>(current, size), wrapper);
        Page<FeishuConfigResponse> responsePage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        responsePage.setRecords(entityPage.getRecords().stream().map(this::toMaskedResponse).toList());
        return responsePage;
    }

    @Override
    public FeishuConfigResponse get(Long id) {
        return toMaskedResponse(requireExisting(id));
    }

    @Override
    public FeishuConfigResponse create(FeishuConfigRequest request) {
        validateCreateRequest(request);
        FeishuConfigEntity entity = new FeishuConfigEntity();
        applyCreateFields(entity, request);
        if (isEnabled(request.getEnabled())) {
            mapper.disableAll();
        }
        int affectedRows = mapper.insert(entity);
        if (affectedRows != 1) {
            throw new IllegalStateException("feishu config was not saved");
        }
        return toMaskedResponse(entity);
    }

    @Override
    public FeishuConfigResponse update(Long id, FeishuConfigRequest request) {
        validateUpdateRequest(request);
        FeishuConfigEntity existing = requireExisting(id);
        existing.setConfigName(request.getConfigName());
        existing.setAppId(request.getAppId());
        existing.setAppSecret(StringUtils.hasText(request.getAppSecret()) ? request.getAppSecret() : existing.getAppSecret());
        existing.setVerificationToken(StringUtils.hasText(request.getVerificationToken())
                ? request.getVerificationToken()
                : existing.getVerificationToken());
        existing.setEncryptKey(StringUtils.hasText(request.getEncryptKey()) ? request.getEncryptKey() : existing.getEncryptKey());
        existing.setEnabled(normalizeEnabled(request.getEnabled()));
        existing.setTenantKey(request.getTenantKey());
        existing.setTenantName(request.getTenantName());
        existing.setCredentialStatus(request.getCredentialStatus());
        existing.setScanState(request.getScanState());
        existing.setScanTicket(request.getScanTicket());
        existing.setPermissionScopes(request.getPermissionScopes());
        existing.setEventSubscriptions(request.getEventSubscriptions());
        existing.setRemark(request.getRemark());
        if (isEnabled(existing.getEnabled())) {
            mapper.disableAll();
        }
        mapper.updateById(existing);
        return toMaskedResponse(existing);
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public FeishuConfigResponse enable(Long id) {
        FeishuConfigEntity existing = requireExisting(id);
        mapper.disableAll();
        existing.setEnabled(1);
        mapper.updateById(existing);
        return toMaskedResponse(existing);
    }

    /**
     * 更新飞书配置的智能体绑定；空白 ID 统一转换为 {@code null}，其余配置字段保持不变。
     *
     * @param id 飞书配置主键，不允许为空且必须存在
     * @param agentId 智能体 ID；允许为空或包含首尾空白
     * @return 已脱敏且包含最新绑定关系的飞书配置
     * @throws IllegalArgumentException 当智能体 ID 超过 64 个字符、配置不存在或绑定更新失败时抛出
     */
    @Override
    public FeishuConfigResponse bindAgent(Long id, String agentId) {
        String normalizedAgentId = StringUtils.hasText(agentId) ? agentId.trim() : null;
        if (normalizedAgentId != null && normalizedAgentId.length() > MAX_AGENT_ID_LENGTH) {
            throw new IllegalArgumentException("agentId must not exceed 64 characters");
        }
        FeishuConfigEntity existing = requireExisting(id);
        int affectedRows = mapper.updateAgentBinding(id, normalizedAgentId);
        if (affectedRows != 1) {
            throw new IllegalArgumentException("feishu config not found or agent binding update failed: " + id);
        }
        existing.setAgentId(normalizedAgentId);
        return toMaskedResponse(existing);
    }
    @Override
    public FeishuRuntimeConfig getActiveConfig() {
        FeishuConfigEntity active = findActiveConfig();
        if (active == null) {
            return null;
        }
        return new FeishuRuntimeConfig(
                active.getAppId(),
                active.getAppSecret(),
                active.getVerificationToken(),
                active.getEncryptKey());
    }

    @Override
    public FeishuConfigResponse getActiveConfigSummary() {
        FeishuConfigEntity active = findActiveConfig();
        return active == null ? null : toMaskedResponse(active);
    }

    @Override
    public FeishuConfigResponse disableActive() {
        FeishuConfigEntity active = findActiveConfig();
        if (active == null) {
            return null;
        }
        active.setEnabled(0);
        active.setUpdateTime(LocalDateTime.now());
        mapper.updateById(active);
        return toMaskedResponse(active);
    }

    @Override
    public FeishuConfigEntity getRaw(Long id) {
        return requireExisting(id);
    }

    @Override
    public void updateCredentialStatus(Long id, String status, LocalDateTime validateTime) {
        FeishuConfigEntity existing = requireExisting(id);
        existing.setCredentialStatus(status);
        existing.setLastValidateTime(validateTime);
        mapper.updateById(existing);
    }

    private FeishuConfigEntity requireExisting(Long id) {
        FeishuConfigEntity existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("feishu config not found: " + id);
        }
        return existing;
    }

    private FeishuConfigEntity findActiveConfig() {
        List<FeishuConfigEntity> records = mapper.selectList(new LambdaQueryWrapper<FeishuConfigEntity>()
                .eq(FeishuConfigEntity::getEnabled, 1)
                .eq(FeishuConfigEntity::getDeleted, 0)
                .last("LIMIT 1"));
        if (records == null || records.isEmpty()) {
            return null;
        }
        return records.get(0);
    }

    private void applyCreateFields(FeishuConfigEntity entity, FeishuConfigRequest request) {
        entity.setConfigName(request.getConfigName());
        entity.setAppId(request.getAppId());
        entity.setAppSecret(request.getAppSecret());
        entity.setVerificationToken(request.getVerificationToken());
        entity.setEncryptKey(request.getEncryptKey());
        entity.setEnabled(normalizeEnabled(request.getEnabled()));
        entity.setTenantKey(request.getTenantKey());
        entity.setTenantName(request.getTenantName());
        entity.setCredentialStatus(request.getCredentialStatus());
        entity.setScanState(request.getScanState());
        entity.setScanTicket(request.getScanTicket());
        entity.setPermissionScopes(request.getPermissionScopes());
        entity.setEventSubscriptions(request.getEventSubscriptions());
        entity.setRemark(request.getRemark());
    }

    private static void validateCreateRequest(FeishuConfigRequest request) {
        validateUpdateRequest(request);
        requireText(request.getAppSecret(), "appSecret must not be blank");
    }

    private static void validateUpdateRequest(FeishuConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        requireText(request.getConfigName(), "configName must not be blank");
        requireText(request.getAppId(), "appId must not be blank");
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private FeishuConfigResponse toMaskedResponse(FeishuConfigEntity entity) {
        FeishuConfigResponse response = new FeishuConfigResponse();
        response.setId(entity.getId());
        response.setConfigName(entity.getConfigName());
        response.setAppId(entity.getAppId());
        response.setAppSecret(mask(entity.getAppSecret()));
        response.setVerificationToken(mask(entity.getVerificationToken()));
        response.setEncryptKey(mask(entity.getEncryptKey()));
        response.setEnabled(entity.getEnabled());
        response.setAgentId(entity.getAgentId());
        response.setTenantKey(entity.getTenantKey());
        response.setTenantName(entity.getTenantName());
        response.setCredentialStatus(entity.getCredentialStatus());
        response.setLastValidateTime(entity.getLastValidateTime());
        response.setLastRefreshTime(entity.getLastRefreshTime());
        response.setScanState(entity.getScanState());
        response.setRemark(entity.getRemark());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    private static String mask(String value) {
        return StringUtils.hasText(value) ? MASKED : "";
    }

    private static Integer normalizeEnabled(Integer enabled) {
        return isEnabled(enabled) ? 1 : 0;
    }

    private static boolean isEnabled(Integer enabled) {
        return Integer.valueOf(1).equals(enabled);
    }
}
