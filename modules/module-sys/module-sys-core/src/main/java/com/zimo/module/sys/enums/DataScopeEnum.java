package com.zimo.module.sys.enums;

/**
 * 制造业数据权限范围枚举。
 *
 * <p>适配制造业组织层级，覆盖全部、集团、工厂、车间、本人五级数据可见范围。</p>
 */
public enum DataScopeEnum {

    /**
     * 全部数据权限。
     *
     * <p>通常用于超级管理员或平台级审计角色，可查看所有组织、所有业务数据。</p>
     */
    ALL("all", "全部数据权限"),

    /**
     * 集团级数据权限。
     *
     * <p>可查看集团范围内的跨工厂、跨车间数据。</p>
     */
    GROUP("group", "集团级数据权限"),

    /**
     * 工厂级数据权限。
     *
     * <p>可查看所属工厂范围内的数据。</p>
     */
    FACTORY("factory", "工厂级数据权限"),

    /**
     * 车间级数据权限。
     *
     * <p>可查看所属车间范围内的数据。</p>
     */
    WORKSHOP("workshop", "车间级数据权限"),

    /**
     * 本人数据权限。
     *
     * <p>仅可查看本人创建、负责或被授权的数据。</p>
     */
    SELF("self", "本人数据权限");

    private final String code;
    private final String description;

    DataScopeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 解析数据权限范围。
     *
     * @param code 数据权限 code
     * @return 匹配的枚举；未知 code 返回 null
     */
    public static DataScopeEnum fromCode(String code) {
        for (DataScopeEnum item : values()) {
            if (item.matchCode(code)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 判断传入 code 是否匹配当前枚举。
     *
     * @param code 数据权限 code
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
