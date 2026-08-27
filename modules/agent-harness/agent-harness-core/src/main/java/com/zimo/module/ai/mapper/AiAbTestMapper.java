package com.zimo.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zimo.module.ai.management.AiAbTest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提示词 AB 测试 Mapper。
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@Mapper
public interface AiAbTestMapper extends BaseMapper<AiAbTest> {
}
