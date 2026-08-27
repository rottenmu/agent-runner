package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 提示词模板版本 / 快照。
 *
 * <p>version 为保存模板时自动生成的版本快照；snapshot 为用户手动打点，用于后续回滚或 AB 测试对比。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@TableName("ai_prompt_version")
public class AiPromptVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的提示词模板主键 */
    private Long promptId;

    /** 版本号（自增，每个模板独立计数） */
    private Integer versionNo;

    /** 类型：version=自动版本，snapshot=手动快照 */
    private String versionType = "version";

    /** 快照名称（快照类型时展示） */
    private String snapshotName;

    /** 模板完整内容 JSON（含名称/编码/CoSTAR 六段等） */
    private String contentJson;

    private String createdBy;

    private String createdName;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPromptId() {
        return promptId;
    }

    public void setPromptId(Long promptId) {
        this.promptId = promptId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getVersionType() {
        return versionType;
    }

    public void setVersionType(String versionType) {
        this.versionType = versionType;
    }

    public String getSnapshotName() {
        return snapshotName;
    }

    public void setSnapshotName(String snapshotName) {
        this.snapshotName = snapshotName;
    }

    public String getContentJson() {
        return contentJson;
    }

    public void setContentJson(String contentJson) {
        this.contentJson = contentJson;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedName() {
        return createdName;
    }

    public void setCreatedName(String createdName) {
        this.createdName = createdName;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
