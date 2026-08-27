package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 提示词 AB 测试。
 *
 * <p>同一提示词模板的两个版本（版本 A / 版本 B）分组对比，支持草稿 / 运行中 / 已完成状态流转。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@TableName("ai_ab_test")
public class AiAbTest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 测试名称 */
    private String testName;

    /** 测试说明 */
    private String description;

    /** 关联的提示词模板主键 */
    private Long promptId;

    /** 版本 A 的 ai_prompt_version.id */
    private Long versionA;

    /** 版本 B 的 ai_prompt_version.id */
    private Long versionB;

    /** 状态：draft=草稿，running=运行中，finished=已完成 */
    private String status = "draft";

    /** 获胜版本的 ai_prompt_version.id，可为空 */
    private Long winner;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getPromptId() {
        return promptId;
    }

    public void setPromptId(Long promptId) {
        this.promptId = promptId;
    }

    public Long getVersionA() {
        return versionA;
    }

    public void setVersionA(Long versionA) {
        this.versionA = versionA;
    }

    public Long getVersionB() {
        return versionB;
    }

    public void setVersionB(Long versionB) {
        this.versionB = versionB;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getWinner() {
        return winner;
    }

    public void setWinner(Long winner) {
        this.winner = winner;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
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
