package com.zimo.module.ai.management;

import java.util.List;
import java.util.Optional;

/**
 * AI 智能体管理模块的自定义技能配置仓储接口，对应数据库表 ai_agent_skill_config。
 *
 * <p>该接口只定义单表边界内的数据访问能力；技能名称唯一性、内置技能保护和 API 参数合法性由业务层完成。</p>
 *
 * @author xingju
 * @since 2026-07-10
 */
public interface AiManagedSkillConfigRepository {
    /**
     * 查询所有未软删除的自定义技能配置。
     *
     * @return 未删除技能配置列表；没有数据时返回空列表
     */
    List<AiManagedSkillConfig> listActive();

    /**
     * 按技能名称查询未软删除的自定义技能配置。
     *
     * @param skillName 技能名称，不允许为空
     * @return 命中的技能配置；不存在或已删除时返回 Optional.empty()
     */
    Optional<AiManagedSkillConfig> findActiveByName(String skillName);

    /**
     * 保存自定义技能配置，id 为空时新增，id 非空时更新未软删除记录。
     *
     * @param config 已通过业务层校验的技能配置，不允许为空
     * @return 仓储保存后的技能配置；新增时包含 MySQL 自增主键
     */
    AiManagedSkillConfig save(AiManagedSkillConfig config);

    /**
     * 按技能名称执行软删除。
     *
     * @param skillName 技能名称，不允许为空
     * @return true 表示删除成功，false 表示配置不存在或已被删除
     */
    boolean softDelete(String skillName);
}
