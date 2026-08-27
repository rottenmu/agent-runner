package com.zimo.module.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * RAG 知识库文档。
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
@TableName("rag_document")
public class RagDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档名称。 */
    private String name;

    /** 文档类型：pdf/docx/xlsx/txt/md/csv。 */
    private String docType;

    /** 来源路径或内容。 */
    private String sourcePath;

    /** 所属知识库 ID。 */
    private Long kbId;

    /** 标签（JSON 数组字符串）。 */
    private String tags;

    /** 当前处理版本号。 */
    private Integer version;

    /** 处理状态：pending=待处理，processing=处理中，done=已完成，failed=失败。 */
    private String status = "pending";

    /** 解析出的总字符数。 */
    private Integer totalChars;

    /** 识别出的表格数。 */
    private Integer tableCount;

    /** 切片数。 */
    private Integer chunkCount;

    /** 是否已向量化。 */
    private Boolean vectorized = false;

    /** 处理失败原因。 */
    private String error;

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

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalChars() {
        return totalChars;
    }

    public void setTotalChars(Integer totalChars) {
        this.totalChars = totalChars;
    }

    public Integer getTableCount() {
        return tableCount;
    }

    public void setTableCount(Integer tableCount) {
        this.tableCount = tableCount;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public Boolean getVectorized() {
        return vectorized;
    }

    public void setVectorized(Boolean vectorized) {
        this.vectorized = vectorized;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
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
