package com.zimo.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zimo.module.ai.management.AiManagedSkillConfig;

/**
 * AI 技能配置 Mapper，对应表 {@code ai_agent_skill_config}。
 *
 * <p>仅提供单表数据访问能力，技能注册与参数校验由 Service 层负责。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
public interface AiManagedSkillConfigMapper extends BaseMapper<AiManagedSkillConfig> {
}
