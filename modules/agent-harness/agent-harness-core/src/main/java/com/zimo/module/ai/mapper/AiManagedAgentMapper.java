package com.zimo.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zimo.module.ai.management.AiManagedAgentEntity;
import org.apache.ibatis.annotations.Select;

/**
 * AI 智能体 Mapper，对应表 {@code ai_managed_agent}。
 *
 * <p>仅提供单表数据访问能力，业务校验由 Service 层负责。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
public interface AiManagedAgentMapper extends BaseMapper<AiManagedAgentEntity> {

    /**
     * 统计表内全部历史记录，包括已逻辑删除数据。
     *
     * @return 智能体配置历史记录总数
     */
    @Select("SELECT COUNT(*) FROM ai_managed_agent")
    long countAll();
}
