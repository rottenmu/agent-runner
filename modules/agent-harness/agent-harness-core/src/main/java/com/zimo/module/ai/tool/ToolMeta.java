package com.zimo.module.ai.tool;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 工具目录元数据（工具市场条目）。
 *
 * <p>所属模块：agent-harness 运行时内核的工具治理层。本类只承载<b>治理元数据</b>
 * （谁可以用、走哪个执行后端、需要什么权限），<b>不持有执行能力</b> ——
 * 真正的执行委托给 {@link ToolProvider} 背后的 {@code framework-ai}
 * 沙箱与 MCP 工具桥。</p>
 *
 * <p>唯一性口径为 {@code (tenantId, toolId)}：不同租户可以注册同名工具，
 * 但同一租户内工具标识必须唯一，避免"调用到了别的租户的同名工具"这类越权事故。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
@TableName("ai_tool_meta")
public class ToolMeta {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工具业务标识（租户内唯一），如图节点的 tool 节点引用它。 */
    private String toolId;

    /** 租户标识：工具归属的隔离维度。 */
    private String tenantId;

    /** 展示名称。 */
    private String name;

    /** 能力描述：给管理端与模型看的用途说明。 */
    private String description;

    /**
     * 执行后端类型：{@code local}（内置函数）/ {@code mcp}（MCP 工具桥）/
     * {@code sandbox}（命令沙箱）。
     */
    private String providerType;

    /** 后端引用：local=函数名，mcp=工具名，sandbox=可执行文件名。 */
    private String providerRef;

    /** 版本号，便于工具升级时保留调用方可追溯性。 */
    private String version;

    /** 参数 Schema（JSON 字符串），供管理与前端渲染表单。 */
    private String schemaJson;

    /** 调用所需权限标识；为空表示不再追加权限校验（仍受租户隔离约束）。 */
    private String requiredPermission;

    /** 是否启用。 */
    private Boolean enabled;

    /** 逻辑删除标记。 */
    @TableLogic
    private Integer deleted;

    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public void setProviderRef(String providerRef) {
        this.providerRef = providerRef;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSchemaJson() {
        return schemaJson;
    }

    public void setSchemaJson(String schemaJson) {
        this.schemaJson = schemaJson;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }

    public void setRequiredPermission(String requiredPermission) {
        this.requiredPermission = requiredPermission;
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
