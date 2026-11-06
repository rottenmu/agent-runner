package com.zimo.module.ai.tool;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图内工具调用审计 Mapper。
 *
 * <p>仅使用 MyBatis-Plus 基础能力：审计表要求 append-only，业务层不得调用
 * 任何 update / delete 方法。</p>
 *
 * <p>类名带 {@code Graph} 前缀是刻意的：本工程 {@code module-tools} 已有一个
 * {@code ToolInvokeLogMapper}，同名 Bean 会直接让容器启动失败
 * （{@code ConflictingBeanDefinitionException}）。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
@Mapper
public interface GraphToolInvokeLogMapper extends BaseMapper<GraphToolInvokeLog> {
}
