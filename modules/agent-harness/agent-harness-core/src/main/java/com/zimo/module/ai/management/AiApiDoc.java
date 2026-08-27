package com.zimo.module.ai.management;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * API 接口定义（API 管理）。
 *
 * <p>管理可被智能体调用的外部接口定义，支持手动新增或从 YApi 文档批量导入。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@TableName("ai_api_doc")
public class AiApiDoc {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接口名称（YApi title） */
    private String name;

    /** HTTP 方法：GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS */
    private String method;

    /** 接口路径（YApi path） */
    private String path;

    /** 接口描述 */
    private String description;

    /** 分组/分类名（YApi catname），可为空 */
    private String groupName;

    /** 请求头 JSON（YApi req_headers），可为空 */
    private String requestHeaders;

    /** 请求参数 JSON（YApi req_query/req_body_other），可为空 */
    private String requestParams;

    /** 响应体 JSON（YApi res_body），可为空 */
    private String responseSchema;

    /** 来源：manual=手动创建，yapi=YApi 导入 */
    private String source;

    /** 是否启用 */
    private Boolean enabled;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

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

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(String requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public String getRequestParams() {
        return requestParams;
    }

    public void setRequestParams(String requestParams) {
        this.requestParams = requestParams;
    }

    public String getResponseSchema() {
        return responseSchema;
    }

    public void setResponseSchema(String responseSchema) {
        this.responseSchema = responseSchema;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
