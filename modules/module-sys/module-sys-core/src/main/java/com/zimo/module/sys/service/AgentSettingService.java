package com.zimo.module.sys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zimo.module.sys.entity.AgentSetting;
import java.util.List;
import java.util.Map;

/**
 * 智能体设置服务。
 *
 * <p>提供配置项的默认值定义、读取（合并默认值与数据库覆盖值）、批量保存，
 * 以及自定义配置项的创建、编辑、删除能力。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
public interface AgentSettingService extends IService<AgentSetting> {

    /**
     * 获取全部预设设置项的定义（键、默认值、说明）。
     *
     * @return 设置项定义列表
     */
    List<SettingDefinition> listDefinitions();

    /**
     * 获取当前生效配置：默认值 + 数据库覆盖值（仅预设项）。
     *
     * @return 配置项键值对
     */
    Map<String, String> getEffectiveSettings();

    /**
     * 批量保存预设配置项（新增或更新）。
     *
     * @param settings 配置键值对
     */
    void saveSettings(Map<String, String> settings);

    /**
     * 创建自定义配置项。
     *
     * @param configKey 配置键，不允许为空
     * @param configValue 配置值
     * @param remark 说明
     * @return 新建的配置项
     * @throws IllegalArgumentException 键为空、已存在或为预设键时抛出
     */
    AgentSetting createSetting(String configKey, String configValue, String remark);

    /**
     * 更新配置项（预设项与自定义项均可）。
     *
     * @param id 配置项 ID
     * @param configValue 配置值
     * @param remark 说明
     * @return 更新后的配置项
     * @throws IllegalArgumentException 配置项不存在时抛出
     */
    AgentSetting updateSetting(Long id, String configValue, String remark);

    /**
     * 删除配置项（仅自定义项可删除）。
     *
     * @param id 配置项 ID
     * @throws IllegalArgumentException 配置项不存在或为预设键时抛出
     */
    void deleteSetting(Long id);

    /**
     * 设置项定义。
     *
     * @param key 配置键
     * @param defaultValue 默认值
     * @param remark 说明
     */
    record SettingDefinition(String key, String defaultValue, String remark) {
    }
}
