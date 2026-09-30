package com.zimo.framework.common.skill;

/**
 * 技能执行结果（框架公共层，零 Spring 依赖）。
 *
 * @param success 是否执行成功
 * @param content 文本内容（成功时为结果数据，失败时为错误说明）
 */
public record AiSkillResult(boolean success, String content) {

    /** 构造成功结果。 */
    public static AiSkillResult ok(String content) {
        return new AiSkillResult(true, content == null ? "" : content);
    }

    /** 构造失败结果。 */
    public static AiSkillResult fail(String content) {
        return new AiSkillResult(false, content == null ? "" : content);
    }
}
