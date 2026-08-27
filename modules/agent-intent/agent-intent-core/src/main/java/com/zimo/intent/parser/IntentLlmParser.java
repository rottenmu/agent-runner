package com.zimo.intent.parser;

import com.zimo.intent.model.IntentLlmResult;
import com.zimo.intent.model.IntentParseResult;
import java.util.List;

/**
 * 意图识别 LLM 精准解析层（混合架构的第二阶段）。
 *
 * <p>规则引擎命中置信度不足（0.5~0.8 或未命中业务意图）时调用本接口，
 * 交由大模型处理复杂口语化、模糊语义与多轮指代。实现可插拔：
 * 生产环境接入真实 LLM（SpringAI / HTTP 裸调用），测试环境可用启发式模拟实现。</p>
 *
 * <p>模式一（规则生成）与模式三（规则校验优化）同样经由本接口的默认方法调用 LLM，
 * 默认实现返回 null 表示不支持，引擎自动降级为启发式/静态校验。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-14
 */
public interface IntentLlmParser {

    /**
     * 调用 LLM 解析用户输入。
     *
     * @param query    用户原始输入，不允许为空
     * @param history  历史对话（最近在前），用于指代消解，允许为空列表
     * @param ruleHint 规则引擎的初步结果（可为 null），供 LLM 参考
     * @return LLM 解析结论；无法解析时返回 null（引擎将按置信度规则兜底）
     */
    IntentLlmResult parse(String query, List<String> history, IntentParseResult ruleHint);

    /**
     * 调用 LLM 解析用户输入，并注入当前规则库 JSON 用于提示词占位符渲染。
     *
     * @param query     用户原始输入，不允许为空
     * @param history   历史对话（最近在前），允许为空列表
     * @param ruleHint  规则引擎的初步结果（可为 null）
     * @param rulesJson 当前规则库 JSON 序列化文本，供 {rules} 占位符注入，允许为空
     * @return LLM 解析结论；无法解析时返回 null
     */
    default IntentLlmResult parse(String query, List<String> history, IntentParseResult ruleHint, String rulesJson) {
        return parse(query, history, ruleHint);
    }

    /**
     * 模式一：根据业务场景描述调用 LLM 生成意图规则库 JSON。
     *
     * @param scenario  业务场景描述，不允许为空
     * @param rulesJson 现有规则库 JSON，供 LLM 避免编码冲突，允许为空
     * @return LLM 返回的规则 JSON 数组文本；实现不支持或调用失败时返回 null
     */
    default String generateRules(String scenario, String rulesJson) {
        return null;
    }

    /**
     * 模式三：调用 LLM 对现有规则集执行冲突校验、补全与优化建议。
     *
     * @param rulesJson 待校验规则库 JSON，不允许为空
     * @return LLM 返回的校验结果 JSON 文本；实现不支持或调用失败时返回 null
     */
    default String reviewRules(String rulesJson) {
        return null;
    }
}
