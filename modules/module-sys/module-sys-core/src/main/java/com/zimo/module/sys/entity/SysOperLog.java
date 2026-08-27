package com.zimo.module.sys.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ps_sys_oper_log")
public class SysOperLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operUserId;

    private String operAccount;

    private String organizationId;

    private String organizationName;

    private String operType;

    private String operName;

    private String requestMethod;

    private String requestUri;

    private String operIp;

    private String requestParams;

    private Integer httpStatus;

    private Long costMillis;

    private String resultStatus;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String tableName = "sys_oper_log";
}
