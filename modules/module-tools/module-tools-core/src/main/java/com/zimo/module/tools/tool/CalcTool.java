package com.zimo.module.tools.tool;

import com.zimo.starter.ai.skill.AiSkill;
import cn.hutool.core.util.StrUtil;
import com.zimo.starter.ai.skill.AiSkillResult;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

/**
 * 计算器工具：数学表达式求值。
 *
 * <p>支持四则运算、括号、幂（^）、函数 sqrt/abs/pow/log/sin/cos/tan/ceil/floor、
 * 常量 pi/e。参数：{@code expression}。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class CalcTool implements AiSkill {

    @Override
    public String name() {
        return "calc";
    }

    @Override
    public String description() {
        return "数学计算器：计算数学表达式。支持 + - * / 括号 幂(^)，"
                + "函数 sqrt/abs/pow/log/sin/cos/tan/ceil/floor，常量 pi/e。"
                + "参数：expression(数学表达式，如 (2+3)*4 或 sqrt(16)+pow(2,10))。";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public AiSkillResult call(Map<String, Object> arguments) {
        String expression = str(arguments == null ? null : arguments.get("expression"));
        if (StrUtil.isBlank(expression)) {
            return AiSkillResult.fail("请提供数学表达式（expression）");
        }
        try {
            Expression builder = new ExpressionBuilder(expression)
                    .variables("x", "y")
                    .build();
            if (arguments != null && arguments.containsKey("x")) {
                builder.setVariable("x", doubleValue(arguments.get("x")));
            }
            if (arguments != null && arguments.containsKey("y")) {
                builder.setVariable("y", doubleValue(arguments.get("y")));
            }
            double result = builder.evaluate();
            BigDecimal rounded = BigDecimal.valueOf(result).setScale(8, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros();
            return AiSkillResult.ok(expression + " = " + rounded.toPlainString());
        } catch (Exception e) {
            return AiSkillResult.fail("表达式求值失败：" + safeMessage(e));
        }
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return StrUtil.isBlank(message)
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}
