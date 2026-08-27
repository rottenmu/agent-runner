package com.zimo.intent.model;

import java.util.List;

/**
 * 意图解析结果（标准输出结构，对应规则文档第三节）。
 *
 * @param query               用户原始输入
 * @param intentCode          匹配的意图编码
 * @param intentName          意图名称
 * @param confidence          置信度 0-1
 * @param entities            抽取的业务实体
 * @param requiredSlotMissing 缺失的必填槽位
 * @param needTool            是否需要工具调用
 * @param toolList            待调用工具列表
 * @param needClarify         是否需追问
 * @param clarifyPrompt       追问话术
 * @param isReject            是否拒绝
 * @param rejectReason        拒绝原因
 * @param routeStrategy       最终路由方式
 * @param ambiguous           模糊识别标记（0.5~0.8 置信度档，需二次确认/降级提示）
 * @param historyUsed         是否使用了历史上下文消解补全
 * @author WorkBuddy
 * @since 2026-08-14
 */
public record IntentParseResult(
        String query,
        String intentCode,
        String intentName,
        double confidence,
        java.util.Map<String, String> entities,
        List<String> requiredSlotMissing,
        boolean needTool,
        List<String> toolList,
        boolean needClarify,
        String clarifyPrompt,
        boolean isReject,
        String rejectReason,
        String routeStrategy,
        boolean ambiguous,
        boolean historyUsed) {

    /** 风险拒绝工厂（默认置信度 0.99，兼容历史调用）。 */
    public static IntentParseResult reject(String query, String reason) {
        return reject(query, reason, 0.99);
    }

    /** 风险拒绝工厂：置信度由配置注入。 */
    public static IntentParseResult reject(String query, String reason, double confidence) {
        return new IntentParseResult(query, "RISK_REJECT", "风险违规拒绝", confidence,
                java.util.Map.of(), List.of(), false, List.of(),
                false, "", true, reason, "REJECT", false, false);
    }

    /** 超出能力范围工厂（默认置信度 0.7，兼容历史调用）。 */
    public static IntentParseResult fallback(String query, String reason) {
        return fallback(query, reason, 0.7);
    }

    /** 超出能力范围工厂：置信度由配置注入。 */
    public static IntentParseResult fallback(String query, String reason, double confidence) {
        return new IntentParseResult(query, "UNSUPPORTED", "超出能力范围", confidence,
                java.util.Map.of(), List.of(), false, List.of(),
                false, "", false, reason, "FALLBACK", false, false);
    }

    /** 通用闲聊工厂（默认置信度 0.6，兼容历史调用）。 */
    public static IntentParseResult chat(String query) {
        return chat(query, 0.6);
    }

    /** 通用闲聊工厂：置信度由配置注入。 */
    public static IntentParseResult chat(String query, double confidence) {
        return new IntentParseResult(query, "GENERAL_CHAT", "通用闲聊", confidence,
                java.util.Map.of(), List.of(), false, List.of(),
                false, "", false, "", "CHAT_REPLY", false, false);
    }

    /** 参数澄清追问工厂（默认置信度 0.85，兼容历史调用）。 */
    public static IntentParseResult clarify(String query, String intentCode, String intentName,
                                            List<String> missing, String prompt) {
        return clarify(query, intentCode, intentName, missing, prompt, 0.85);
    }

    /** 参数澄清追问工厂：置信度由配置注入。 */
    public static IntentParseResult clarify(String query, String intentCode, String intentName,
                                            List<String> missing, String prompt, double confidence) {
        return new IntentParseResult(query, "PARAM_CLARIFY", "参数澄清追问", confidence,
                java.util.Map.of(), missing == null ? List.of() : List.copyOf(missing),
                false, List.of(), true, prompt, false, "", "CLARIFY", false, false);
    }

    public static IntentParseResult business(String query, String code, String name, double confidence,
                                             java.util.Map<String, String> entities,
                                             List<String> missing, List<String> tools,
                                             boolean needClarify, String clarifyPrompt,
                                             String routeStrategy) {
        return business(query, code, name, confidence, entities, missing, tools,
                needClarify, clarifyPrompt, routeStrategy, false, false);
    }

    /** 带模糊标记与上下文消解标记的完整业务结果。 */
    public static IntentParseResult business(String query, String code, String name, double confidence,
                                             java.util.Map<String, String> entities,
                                             List<String> missing, List<String> tools,
                                             boolean needClarify, String clarifyPrompt,
                                             String routeStrategy, boolean ambiguous, boolean historyUsed) {
        return new IntentParseResult(query, code, name, confidence,
                entities == null ? java.util.Map.of() : java.util.Map.copyOf(entities),
                missing == null ? List.of() : List.copyOf(missing),
                tools != null && !tools.isEmpty(), tools == null ? List.of() : List.copyOf(tools),
                needClarify, clarifyPrompt, false, "", routeStrategy, ambiguous, historyUsed);
    }
}
