package com.zimo.module.ai.management;

import java.util.List;
import java.util.Optional;

/**
 * AI 智能体管理模块的智能体配置仓储接口，对应数据库表 ai_managed_agent。
 *
 * <p>该接口只负责智能体单表边界内的数据访问；智能体名称校验、技能绑定合法性、默认渠道选择和提示词模板约束由业务服务层处理。</p>
 *
 * @author xingju
 * @since 2026-07-21
 */
public interface AiManagedAgentRepository {
    /**
     * 查询所有未软删除的智能体配置。
     *
     * @return 未删除智能体列表；没有数据时返回空列表
     */
    List<AiManagedAgent> listActive();

    /**
     * 按智能体 ID 查询未软删除的智能体配置。
     *
     * @param id 智能体 ID，不允许为空
     * @return 命中的智能体配置；不存在或已删除时返回 Optional.empty()
     */
    Optional<AiManagedAgent> findActiveById(String id);

    /**
     * 判断智能体配置表中是否存在任何历史记录，包含已软删除记录。
     *
     * @return true 表示表中曾写入过智能体配置，false 表示表为空
     */
    boolean hasAny();

    /**
     * 保存智能体配置；ID 已存在时更新，ID 不存在时新增。
     *
     * @param agent 已通过业务层校验的智能体配置，不允许为空
     * @return 仓储保存后的智能体配置
     */
    AiManagedAgent save(AiManagedAgent agent);

    /**
     * 按智能体 ID 执行逻辑删除。
     *
     * @param id 智能体 ID，不允许为空
     * @return true 表示删除成功，false 表示配置不存在或已被删除
     */
    boolean softDelete(String id);
}