package com.zimo.module.feishu.config;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;

public interface FeishuConfigService {
    /**
     * 按原有查询条件分页查询飞书配置，不限制智能体绑定状态。
     *
     * @param current 当前页，从 1 开始
     * @param size 每页条数
     * @param configName 配置名称，允许为空
     * @param appId 飞书 App ID，允许为空
     * @param enabled 启用状态，允许为空
     * @return 脱敏后的飞书配置分页结果
     */
    default Page<FeishuConfigResponse> page(
            long current,
            long size,
            String configName,
            String appId,
            Integer enabled) {
        return page(current, size, configName, appId, enabled, null);
    }

    /**
     * 分页查询飞书配置，可按智能体绑定状态筛选。
     *
     * @param current 当前页，从 1 开始
     * @param size 每页条数
     * @param configName 配置名称，允许为空
     * @param appId 飞书 App ID，允许为空
     * @param enabled 启用状态，允许为空
     * @param bound 绑定状态；true 查询已绑定，false 查询未绑定，null 不筛选
     * @return 脱敏后的飞书配置分页结果
     */
    Page<FeishuConfigResponse> page(
            long current,
            long size,
            String configName,
            String appId,
            Integer enabled,
            Boolean bound);

    FeishuConfigResponse get(Long id);

    FeishuConfigResponse create(FeishuConfigRequest request);

    FeishuConfigResponse update(Long id, FeishuConfigRequest request);

    void delete(Long id);

    FeishuConfigResponse enable(Long id);

    /**
     * 更新指定飞书配置的智能体绑定，不修改配置凭据及其他业务字段。
     *
     * @param id 飞书配置主键，不允许为空且必须存在
     * @param agentId 智能体 ID；空值或空白字符串表示解除绑定
     * @return 已脱敏且包含最新绑定关系的飞书配置
     * @throws IllegalArgumentException 当配置不存在时抛出
     */
    FeishuConfigResponse bindAgent(Long id, String agentId);

    FeishuConfigResponse getActiveConfigSummary();

    FeishuConfigResponse disableActive();

    FeishuRuntimeConfig getActiveConfig();

    FeishuConfigEntity getRaw(Long id);

    void updateCredentialStatus(Long id, String status, LocalDateTime validateTime);
}
