package com.zimo.module.feishu.mapping;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface FeishuUserMappingMapper extends BaseMapper<FeishuUserMappingEntity> {

    @Select("""
            SELECT *
            FROM ps_feishu_user_mapping
            WHERE deleted = 0
              AND tenant_key = #{tenantKey}
              AND enabled = 1
              AND (
                feishu_user_id = #{feishuUserId}
                OR feishu_open_id = #{openId}
                OR feishu_union_id = #{unionId}
              )
            ORDER BY
              CASE
                WHEN feishu_user_id = #{feishuUserId} THEN 1
                WHEN feishu_open_id = #{openId} THEN 2
                WHEN feishu_union_id = #{unionId} THEN 3
                ELSE 4
              END
            LIMIT 1
            """)
    FeishuUserMappingEntity selectBestMapping(
            @Param("tenantKey") String tenantKey,
            @Param("feishuUserId") String feishuUserId,
            @Param("openId") String openId,
            @Param("unionId") String unionId);
}
