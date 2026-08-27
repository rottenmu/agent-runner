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
 * <p>内置 6 个长期记忆配置项的默认值，数据库保存用户覆盖值与自定义项；读取时合并返回。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@Service
public class AgentSettingServiceImpl extends ServiceImpl<AgentSettingMapper, AgentSetting>
        implements AgentSettingService {

    private static final List<SettingDefinition> DEFINITIONS = List.of(
            new SettingDefinition("memory-enabled", "true", "启用长期记忆"),
            new SettingDefinition("memory-flush-trigger-seconds", "600", "per-call flush 节流间隔（秒），默认10分钟"),
            new SettingDefinition("memory-consolidation-min-gap-minutes", "120", "后台 MEMORY.md 合并最小间隔（分钟），默认2小时"),
            new SettingDefinition("memory-consolidation-max-tokens", "4000", "MEMORY.md token 上限"),
            new SettingDefinition("memory-daily-retention-days", "90", "日流水账归档天数"),
            new SettingDefinition("memory-session-retention-days", "180", "会话日志保留天数"));

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
        Map<String, String> normalized = settings == null ? Map.of() : settings;
        List<AgentSetting> existing = list();
        Map<String, AgentSetting> byKey = existing.stream()
                .collect(Collectors.toMap(AgentSetting::getConfigKey, item -> item, (a, b) -> a));

        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            String key = entry.getKey().trim();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (key.isEmpty() || !isKnownKey(key)) {
                continue;
            }
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
        item.setConfigValue(configValue == null ? "" : configValue.trim());
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
}
