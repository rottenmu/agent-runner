package com.zimo.module.tools.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * ToolDefinition 实体。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
@TableName("tool_definition")
public class ToolDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pluginId;
    private String name;
    private String description;
    private String type;
    private String method;
    private String target;
    private String paramsSchema;
    private Boolean defaultAllow;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPluginId() {
        return pluginId;
    }

    public void setPluginId(Long pluginId) {
        this.pluginId = pluginId;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getParamsSchema() {
        return paramsSchema;
    }

    public void setParamsSchema(String paramsSchema) {
        this.paramsSchema = paramsSchema;
    }

    public Boolean getDefaultAllow() {
        return defaultAllow;
    }

    public void setDefaultAllow(Boolean defaultAllow) {
        this.defaultAllow = defaultAllow;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
