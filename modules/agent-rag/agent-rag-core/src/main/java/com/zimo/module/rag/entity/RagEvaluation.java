package com.zimo.module.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 知识库测评用例（检索质量评估）。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
@TableName("rag_evaluation")
public class RagEvaluation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识库 ID。 */
    private Long kbId;

    /** 测评查询。 */
    private String query;

    /** 期望命中的文档名（或关键词）。 */
    private String expectedDoc;

    /** 最近运行是否命中。 */
    private Boolean hit;

    /** 最近运行命中分数。 */
    private Double hitScore;

    /** 最近运行时间。 */
    private LocalDateTime lastRunAt;

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

    public String getExpectedDoc() {
        return expectedDoc;
    }

    public void setExpectedDoc(String expectedDoc) {
        this.expectedDoc = expectedDoc;
    }

    public Boolean getHit() {
        return hit;
    }

    public void setHit(Boolean hit) {
        this.hit = hit;
    }

    public Double getHitScore() {
        return hitScore;
    }

    public void setHitScore(Double hitScore) {
        this.hitScore = hitScore;
    }

    public LocalDateTime getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(LocalDateTime lastRunAt) {
        this.lastRunAt = lastRunAt;
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
