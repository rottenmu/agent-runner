package com.zimo.module.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * RAG 知识库（管控单元）。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
@TableName("rag_knowledge_base")
public class RagKnowledgeBase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识库名称。 */
    private String name;

    /** 描述。 */
    private String description;

    /** 标签（JSON 数组字符串）。 */
    private String tags;

    /** 可见性：private=私有（仅授权），shared=共享（全员可读）。 */
    private String visibility = "private";

    /** 创建者（拥有者）。 */
    private String createdBy;

    /** 租户。 */
    private String tenantId;

    /** 启用状态。 */
    private Boolean enabled = true;

    /** 定时自动更新开关。 */
    private Boolean autoSyncEnabled = false;

    /** 自动更新 cron 表达式（默认每 6 小时）。 */
    private String autoSyncCron = "0 0 */6 * * ?";

    /** 上次自动同步时间。 */
    private LocalDateTime lastSyncAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

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

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getAutoSyncEnabled() {
        return autoSyncEnabled;
    }

    public void setAutoSyncEnabled(Boolean autoSyncEnabled) {
        this.autoSyncEnabled = autoSyncEnabled;
    }

    public String getAutoSyncCron() {
        return autoSyncCron;
    }

    public void setAutoSyncCron(String autoSyncCron) {
        this.autoSyncCron = autoSyncCron;
    }

    public LocalDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(LocalDateTime lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
