package com.zimo.module.sys.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 智能体设置项。
 *
 * <p>以 key-value 形式持久化智能体运行配置（如长期记忆参数），key 对应
 * {@code ai.agent.*} 配置项，value 为覆盖值；未配置的项由默认值补齐。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@Data
@TableName("ps_sys_agent_setting")
public class AgentSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键，如 memory-enabled */
    private String configKey;

    /** 配置值，字符串形式 */
    private String configValue;

    /** 配置说明 */
    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
