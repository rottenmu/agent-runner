package com.zimo.framework.common.skill;

import java.util.List;
import java.util.Map;

/**
 * 智能体技能契约（框架公共层，零 Spring 依赖）。
 *
 * <p>技能是智能体可调用的最小能力单元：每个技能声明名称、说明、只读属性，
 * 并接收结构化参数返回执行结果。任何模块均可实现本接口向智能体注册能力，
 * 无需依赖 AI 运行时实现。</p>
 *
 * <p>本接口位于 {@code framework-common} 而非 {@code framework-ai}，
 * 以便数据源、工具、渠道等业务模块在不引入 AI 运行时依赖的前提下实现技能。</p>
 */
public interface AiSkill {

    /** 技能名称（智能体调用时的唯一标识，建议 snake_case）。 */
    String name();

    /** 技能说明（供 LLM 判断何时调用）。 */
    String description();

    /** 是否为只读技能（只读技能可跳过审批直接执行）。 */
    boolean readOnly();

    /**
     * 执行技能。
     *
     * @param arguments 调用参数
     * @return 执行结果
     */
    AiSkillResult call(Map<String, Object> arguments);

    /** 可调用参数名声明（用于参数提示/示例生成）；默认无约束。 */
    default List<String> inputParameters() {
        return List.of();
    }
}
