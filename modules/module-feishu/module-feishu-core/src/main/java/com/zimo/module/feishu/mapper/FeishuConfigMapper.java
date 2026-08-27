package com.zimo.module.feishu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zimo.module.feishu.config.FeishuConfigEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 飞书配置数据访问接口，负责 {@code ps_feishu_config} 表的单表读写。
 */
public interface FeishuConfigMapper extends BaseMapper<FeishuConfigEntity> {
    /**
     * 禁用全部未删除的飞书配置。
     *
     * @return 实际更新的配置行数，没有可禁用配置时返回 {@code 0}
     */
    @Update("UPDATE ps_feishu_config SET enabled = 0 WHERE deleted = 0")
    int disableAll();

    /**
     * 仅更新指定飞书配置的智能体绑定，不改动其他配置字段。
     *
     * @param id 飞书配置主键，不允许为空
     * @param agentId 智能体 ID，传入 {@code null} 表示解除绑定
     * @return 实际更新行数；配置不存在或已删除时返回 {@code 0}
     */
    @Update("UPDATE ps_feishu_config SET agent_id = #{agentId} WHERE id = #{id} AND deleted = 0")
    int updateAgentBinding(@Param("id") Long id, @Param("agentId") String agentId);
}
