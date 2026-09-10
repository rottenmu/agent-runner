package com.zimo.module.feishu.autoconfig;

import com.zimo.module.feishu.config.FeishuConfigProvider;
import com.zimo.module.feishu.config.FeishuConfigResponse;
import com.zimo.module.feishu.config.FeishuConfigService;
import com.zimo.module.feishu.config.FeishuRuntimeConfig;
import com.zimo.module.feishu.event.FeishuEventProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

/**
 * 从飞书配置服务读取当前活动配置，并在数据库配置不可用时回退到应用属性。
 */
public class RuntimeFeishuConfigProvider implements FeishuConfigProvider, FeishuEventProperties {
    private final ObjectProvider<FeishuConfigService> configServiceProvider;
    private final FeishuProperties properties;

    public RuntimeFeishuConfigProvider(
            ObjectProvider<FeishuConfigService> configServiceProvider,
            FeishuProperties properties) {
        this.configServiceProvider = configServiceProvider;
        this.properties = properties;
    }

    @Override
    public FeishuRuntimeConfig getActiveConfig() {
        FeishuConfigService configService = configServiceProvider.getIfAvailable();
        if (configService != null) {
            FeishuRuntimeConfig databaseConfig = configService.getActiveConfig();
            if (hasAppCredentials(databaseConfig)) {
                return databaseConfig;
            }
        }
        return new FeishuRuntimeConfig(
                properties.getAppId(),
                properties.getAppSecret(),
                properties.getVerificationToken(),
                properties.getEncryptKey());
    }

    @Override
    public String getActiveAgentId() {
        FeishuConfigResponse activeConfig = activeConfigSummary();
        return activeConfig == null ? null : activeConfig.getAgentId();
    }

    @Override
    public String getActiveAgentId(String tenantKey) {
        FeishuConfigResponse activeConfig = activeConfigSummary();
        if (activeConfig == null
                || !StringUtils.hasText(tenantKey)
                || !StringUtils.hasText(activeConfig.getTenantKey())
                || !activeConfig.getTenantKey().trim().equals(tenantKey.trim())) {
            return null;
        }
        return activeConfig.getAgentId();
    }
    @Override
    public String getVerificationToken() {
        FeishuRuntimeConfig activeConfig = getActiveConfig();
        return activeConfig == null ? null : activeConfig.getVerificationToken();
    }

    @Override
    public String getEncryptKey() {
        FeishuRuntimeConfig activeConfig = getActiveConfig();
        return activeConfig == null ? null : activeConfig.getEncryptKey();
    }

    private FeishuConfigResponse activeConfigSummary() {
        FeishuConfigService configService = configServiceProvider.getIfAvailable();
        return configService == null ? null : configService.getActiveConfigSummary();
    }

    private static boolean hasAppCredentials(FeishuRuntimeConfig config) {
        return config != null
                && StringUtils.hasText(config.getAppId())
                && StringUtils.hasText(config.getAppSecret());
    }
}
