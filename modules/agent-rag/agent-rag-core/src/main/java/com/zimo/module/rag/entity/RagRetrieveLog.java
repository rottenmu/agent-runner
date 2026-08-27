package com.zimo.module.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 检索日志。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
@TableName("rag_retrieve_log")
public class RagRetrieveLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识库 ID。 */
    private Long kbId;

    /** 查询内容。 */
    private String query;

    /** 是否限定文档。 */
    private Long docId;

    private Integer topK;

    /** 是否启用重排。 */
    private Boolean rerank;

    /** 返回结果数。 */
    private Integer resultCount;

    /** 耗时（毫秒）。 */
    private Long latencyMs;

    /** 来源：console=控制台，mcp=技能调用。 */
    private String source;

    /** 发起用户。 */
    private String userId;

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

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Boolean getRerank() {
        return rerank;
    }

    public void setRerank(Boolean rerank) {
        this.rerank = rerank;
    }

    public Integer getResultCount() {
        return resultCount;
    }

    public void setResultCount(Integer resultCount) {
        this.resultCount = resultCount;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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
