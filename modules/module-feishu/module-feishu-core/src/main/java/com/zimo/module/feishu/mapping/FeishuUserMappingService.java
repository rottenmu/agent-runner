package com.zimo.module.feishu.mapping;

import com.zimo.module.feishu.channel.FeishuAgentCommandMessage;
import com.zimo.module.sys.enums.DataScopeEnum;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class FeishuUserMappingService {
    private final FeishuUserMappingMapper mapper;

    public FeishuUserMappingService(FeishuUserMappingMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<FeishuInternalUserSnapshot> findInternalUser(FeishuAgentCommandMessage message) {
        if (message == null || mapper == null) {
            return Optional.empty();
        }
        String tenantKey = clean(message.getTenantKey());
        String feishuUserId = clean(message.getSenderUserId());
        String openId = clean(message.getSenderOpenId());
        String unionId = clean(message.getSenderUnionId());
        if (tenantKey == null || (feishuUserId == null && openId == null && unionId == null)) {
            return Optional.empty();
        }
        FeishuUserMappingEntity entity = mapper.selectBestMapping(
                tenantKey,
                feishuUserId,
                openId,
                unionId);
        if (entity == null || !Objects.equals(entity.getEnabled(), 1)) {
            return Optional.empty();
        }
        return Optional.of(new FeishuInternalUserSnapshot(
                entity.getInternalUserId(),
                entity.getInternalAccount(),
                entity.getOrganizationId(),
                entity.getOrganizationName(),
                splitPermissions(entity.getPermissions()),
                parseDataScope(entity.getDataScope())
        ));
    }

    private static Set<String> splitPermissions(String permissions) {
        Set<String> result = new LinkedHashSet<>();
        if (permissions == null || permissions.trim().isEmpty()) {
            return result;
        }
        for (String permission : permissions.split(",")) {
            if (permission != null && !permission.trim().isEmpty()) {
                result.add(permission.trim());
            }
        }
        return result;
    }

    private static DataScopeEnum parseDataScope(String dataScope) {
        if (dataScope == null || dataScope.trim().isEmpty()) {
            return DataScopeEnum.SELF;
        }
        try {
            return DataScopeEnum.valueOf(dataScope.trim());
        } catch (IllegalArgumentException ignored) {
            return DataScopeEnum.SELF;
        }
    }

    private static String clean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
