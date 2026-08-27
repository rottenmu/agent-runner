package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * MCP 服务器配置。
 *
 * <p>管理可连接的外部 MCP 服务器（stdio/http/sse 传输），以及本地暴露的 MCP 服务。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@TableName("ai_mcp_config")
public class AiMcpConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** MCP 名称 */
    private String name;

    /** MCP 描述 */
    private String description;

    /** 连接类型：stdio / http / sse */
    private String mcpType;

    /** 端点地址（http/sse 类型必填） */
    private String endpoint;

    /** 传输参数（stdio 命令、headers 等，JSON 字符串） */
    private String transportConfig;

    /** 是否启用 */
    private Boolean enabled;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMcpType() {
        return mcpType;
    }

    public void setMcpType(String mcpType) {
        this.mcpType = mcpType;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getTransportConfig() {
        return transportConfig;
    }

    public void setTransportConfig(String transportConfig) {
        this.transportConfig = transportConfig;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
