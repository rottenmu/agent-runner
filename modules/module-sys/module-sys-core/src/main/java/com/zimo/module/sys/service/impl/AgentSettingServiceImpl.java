package com.zimo.module.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zimo.module.sys.entity.AgentSetting;
import com.zimo.module.sys.mapper.AgentSettingMapper;
import com.zimo.module.sys.service.AgentSettingService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 智能体设置服务实现。
 *
 * <p>内置记忆运行配置项的默认值，数据库保存用户覆盖值与自定义项；读取时合并返回。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@Service
public class AgentSettingServiceImpl extends ServiceImpl<AgentSettingMapper, AgentSetting>
        implements AgentSettingService {

    /** 内置记忆配置定义，未持久化覆盖值时采用定义中的默认值。 */
    private static final List<SettingDefinition> DEFINITIONS = List.of(
            new SettingDefinition("memory-enabled", "true", "启用长期记忆"),
            new SettingDefinition("memory-flush-trigger-seconds", "600", "per-call flush 节流间隔（秒），默认10分钟"),
            new SettingDefinition("memory-consolidation-min-gap-minutes", "120", "后台 MEMORY.md 合并最小间隔（分钟），默认2小时"),
            new SettingDefinition("memory-consolidation-max-tokens", "4000", "MEMORY.md token 上限"),
            new SettingDefinition("memory-daily-retention-days", "90", "日流水账归档天数"),
            new SettingDefinition("memory-session-retention-days", "180", "会话日志保留天数"),
            new SettingDefinition("short-term-retention-days", "7", "短期会话记忆闲置清理天数（1 至 365 天）"));

    @Override
    public List<SettingDefinition> listDefinitions() {
        return DEFINITIONS;
    }

    @Override
    public Map<String, String> getEffectiveSettings() {
        Map<String, String> effective = new LinkedHashMap<>();
        DEFINITIONS.forEach(def -> effective.put(def.key(), def.defaultValue()));
        List<AgentSetting> overrides = list();
        overrides.forEach(item -> {
            if (isKnownKey(item.getConfigKey())) {
                effective.put(item.getConfigKey(), item.getConfigValue());
            }
        });
        return effective;
    }

    @Override
    public void saveSettings(Map<String, String> settings) {
        Map<String, String> normalized = normalizeSettings(settings);
        List<AgentSetting> existing = list();
        Map<String, AgentSetting> byKey = existing.stream()
                .collect(Collectors.toMap(AgentSetting::getConfigKey, item -> item, (a, b) -> a));

        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            AgentSetting item = byKey.get(key);
            if (item == null) {
                AgentSetting created = new AgentSetting();
                created.setConfigKey(key);
                created.setConfigValue(value);
                save(created);
            } else if (!value.equals(item.getConfigValue())) {
                item.setConfigValue(value);
                updateById(item);
            }
        }
    }

    @Override
    public AgentSetting createSetting(String configKey, String configValue, String remark) {
        String key = StringUtils.hasText(configKey) ? configKey.trim() : "";
        if (key.isEmpty()) {
            throw new IllegalArgumentException("配置键不能为空");
        }
        if (isKnownKey(key)) {
            throw new IllegalArgumentException("配置键已存在（预设项）: " + key);
        }
        boolean exists = count(new LambdaQueryWrapper<AgentSetting>()
                .eq(AgentSetting::getConfigKey, key)) > 0;
        if (exists) {
            throw new IllegalArgumentException("配置键已存在: " + key);
        }
        AgentSetting created = new AgentSetting();
        created.setConfigKey(key);
        created.setConfigValue(configValue == null ? "" : configValue.trim());
        created.setRemark(remark);
        save(created);
        return created;
    }

    @Override
    public AgentSetting updateSetting(Long id, String configValue, String remark) {
        AgentSetting item = getById(id);
        if (item == null) {
            throw new IllegalArgumentException("配置项不存在: id=" + id);
        }
        String normalizedValue = configValue == null ? "" : configValue.trim();
        validateSettingValue(item.getConfigKey(), normalizedValue);
        item.setConfigValue(normalizedValue);
        item.setRemark(remark);
        updateById(item);
        return item;
    }

    @Override
    public void deleteSetting(Long id) {
        AgentSetting item = getById(id);
        if (item == null) {
            throw new IllegalArgumentException("配置项不存在: id=" + id);
        }
        if (isKnownKey(item.getConfigKey())) {
            throw new IllegalArgumentException("预设配置项不可删除: " + item.getConfigKey());
        }
        removeById(id);
    }

    private boolean isKnownKey(String key) {
        return DEFINITIONS.stream().anyMatch(def -> def.key().equals(key));
    }

    /** 过滤未知设置键并在任何数据库写入前完成所有预设值校验。 */
    private Map<String, String> normalizeSettings(Map<String, String> settings) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (settings == null) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            if (key.isEmpty() || !isKnownKey(key)) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            validateSettingValue(key, value);
            normalized.put(key, value);
        }
        return normalized;
    }

    /** 限制短期会话保留期限为 1 至 365 的整数。 */
    private void validateSettingValue(String key, String value) {
        if (!"short-term-retention-days".equals(key)) {
            return;
        }
        try {
            int days = Integer.parseInt(value);
            if (days >= 1 && days <= 365) {
                return;
            }
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("短期会话记忆保留天数必须为 1 至 365 的整数");
        }
        throw new IllegalArgumentException("短期会话记忆保留天数必须为 1 至 365 的整数");
    }
}
