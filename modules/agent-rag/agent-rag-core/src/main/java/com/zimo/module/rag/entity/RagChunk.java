package com.zimo.module.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * RAG 知识库切片（含向量）。
 *
 * @author WorkBuddy
 * @since 2026-08-09
 */
@TableName("rag_chunk")
public class RagChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属文档 ID。 */
    private Long docId;

    /** 切片序号。 */
    private Integer seq;

    /** 切片内容。 */
    private String content;

    /** 表格信息（markdown 表格或空）。 */
    private String tableInfo;

    /** 切片元数据（标题/来源页等 JSON）。 */
    private String metadata;

    /** 向量（JSON 数组字符串）。 */
    private String vectorJson;

    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTableInfo() {
        return tableInfo;
    }

    public void setTableInfo(String tableInfo) {
        this.tableInfo = tableInfo;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getVectorJson() {
        return vectorJson;
    }

    public void setVectorJson(String vectorJson) {
        this.vectorJson = vectorJson;
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
