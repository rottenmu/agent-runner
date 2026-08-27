package com.zimo.module.sys.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ps_sys_role")
public class SysRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleKey;
    private String roleName;
    private String remark;
    private Integer status;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
