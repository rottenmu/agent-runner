package com.zimo.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zimo.module.ai.management.AiPromptTemplate;

/**
 * AI 提示词模板 Mapper，对应表 {@code ai_prompt_template}。
 *
 * <p>仅提供单表数据访问能力，CoSTAR 校验与生成逻辑由 Service 层负责。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
public interface AiPromptTemplateMapper extends BaseMapper<AiPromptTemplate> {
}
