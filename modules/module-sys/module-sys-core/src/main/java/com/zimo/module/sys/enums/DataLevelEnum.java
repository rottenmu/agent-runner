package com.zimo.module.sys.enums;

/**
 * 数据密级枚举。
 *
 * <p>用于标识业务数据的敏感级别。当前模型控制到部门主管级即可，
 * 机密数据建议仅部门主管级及以上角色可访问。</p>
 */
public enum DataLevelEnum {

    /**
     * 公开数据。
     *
     * <p>授权用户均可查看。</p>
     */
    PUBLIC("public", "公开数据，授权用户均可访问"),

    /**
     * 内部数据。
     *
     * <p>制造组织内部授权岗位可访问。</p>
     */
    INTERNAL("internal", "内部数据，制造组织内部授权岗位可访问"),

    /**
     * 机密数据。
     *
     * <p>部门主管级及以上可访问。</p>
     */
    CONFIDENTIAL("confidential", "机密数据，部门主管级及以上可访问");

    private final String code;
    private final String description;

    DataLevelEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 解析数据密级。
     *
     * @param code 数据密级 code
     * @return 匹配的枚举；未知 code 返回 null
     */
    public static DataLevelEnum fromCode(String code) {
        for (DataLevelEnum item : values()) {
            if (item.matchCode(code)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 判断传入 code 是否匹配当前枚举。
     *
     * @param code 数据密级 code
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
