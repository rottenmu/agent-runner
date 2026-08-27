package com.zimo.module.sys.enums;

/**
 * 接口操作类型枚举。
 *
 * <p>用于描述接口或按钮权限对应的业务动作，便于权限编码、操作日志和审计策略统一。</p>
 */
public enum PermOperTypeEnum {

    /**
     * 查询。
     */
    QUERY("query", "查询"),

    /**
     * 新增。
     */
    ADD("add", "新增"),

    /**
     * 编辑。
     */
    EDIT("edit", "编辑"),

    /**
     * 删除。
     */
    DELETE("delete", "删除"),

    /**
     * 导入。
     */
    IMPORT("import", "导入"),

    /**
     * 导出。
     */
    EXPORT("export", "导出"),

    /**
     * 审核。
     */
    AUDIT("audit", "审核"),

    /**
     * 反审核。
     */
    UNAUDIT("unaudit", "反审核"),

    /**
     * 启用。
     */
    ENABLE("enable", "启用"),

    /**
     * 禁用。
     */
    DISABLE("disable", "禁用"),

    /**
     * 分配。
     */
    ASSIGN("assign", "分配"),

    /**
     * 打印。
     */
    PRINT("print", "打印");

    private final String code;
    private final String description;

    PermOperTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 解析接口操作类型。
     *
     * @param code 操作类型 code
     * @return 匹配的枚举；未知 code 返回 null
     */
    public static PermOperTypeEnum fromCode(String code) {
        for (PermOperTypeEnum item : values()) {
            if (item.matchCode(code)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 判断传入 code 是否匹配当前枚举。
     *
     * @param code 操作类型 code
     * @return true 表示匹配
     */
    public boolean matchCode(String code) {
        return this.code.equalsIgnoreCase(String.valueOf(code));
    }

    /**
     * 获取枚举 code。
     *
     * @return code
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取中文描述。
     *
     * @return 中文描述
     */
    public String getDescription() {
        return description;
    }
}
