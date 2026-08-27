package com.zimo.module.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 知识库权限条目（用户/角色级访问控制）。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
@TableName("rag_kb_permission")
public class RagKbPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识库 ID。 */
    private Long kbId;

    /** 主体类型：user=用户，role=角色。 */
    private String principalType;

    /** 主体标识（用户 ID 或角色 key）。 */
    private String principalId;

    /** 访问级别：read=只读，write=可写，manage=管理。 */
    private String accessLevel = "read";

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

    public String getPrincipalType() {
        return principalType;
    }

    public void setPrincipalType(String principalType) {
        this.principalType = principalType;
    }

    public String getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(String principalId) {
        this.principalId = principalId;
    }

    public String getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(String accessLevel) {
        this.accessLevel = accessLevel;
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
