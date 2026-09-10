package com.zimo.framework.ai.skill;

import java.util.List;

/**
 * 技能描述（含可调用参数名，供管理端/测试页生成参数提示）。
 *
 * @param name        技能名称
 * @param description 技能描述
 * @param readOnly    是否只读
 * @param parameters  可调用参数名列表（空表示无约束/自由参数）
 */
public record AiSkillDescriptor(String name, String description, boolean readOnly, List<String> parameters) {

    /** 兼容旧构造：无参数声明。 */
    public AiSkillDescriptor(String name, String description, boolean readOnly) {
        this(name, description, readOnly, List.of());
    }
}