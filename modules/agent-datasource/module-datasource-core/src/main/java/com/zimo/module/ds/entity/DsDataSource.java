package com.zimo.module.ds.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 数据源定义。
 *
 * <p>类型：database=数据库，document=Excel/Word/PDF 文档，ocr=扫描件 OCR，web=网页，
 * api=接口数据，file_server=文件服务器，erp=ERP 数据表。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
@TableName("ds_data_source")
public class DsDataSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据源名称。 */
    private String name;

    /** 类型：database/document/ocr/web/api/file_server/erp。 */
    private String type;

    /** 描述。 */
    private String description;

    /** 连接配置 JSON（按类型不同字段）。 */
    private String configJson;

    /** 启用状态。 */
    private Boolean enabled = true;

    private String createdBy;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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
