package com.zimo.module.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 知识库处理版本快照（用于版本回溯）。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
@TableName("rag_version")
public class RagVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属知识库 ID。 */
    private Long kbId;

    /** 文档 ID。 */
    private Long docId;

    /** 版本号。 */
    private Integer version;

    /** 快照摘要（字符数/表格数/切片数）。 */
    private String summary;

    /** 切片快照（JSON：RagChunk 列表或恢复数据）。 */
    private String snapshotJson;

    /** 操作：process=处理快照，rollback=回滚点。 */
    private String action;

    private String createdBy;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
