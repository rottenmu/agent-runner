package com.zimo.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zimo.module.ai.management.AiPromptVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提示词模板版本 / 快照 Mapper。
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@Mapper
public interface AiPromptVersionMapper extends BaseMapper<AiPromptVersion> {
}
