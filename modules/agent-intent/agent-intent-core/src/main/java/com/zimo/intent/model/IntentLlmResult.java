package com.zimo.intent.model;

import java.util.List;
import java.util.Map;

/**
 * LLM 解析结论（引擎将据此合并到最终结果）。
 *
 * <p>字段对齐模式二实时解析输出规范：意图编码/名称/置信度/实体/缺失槽位/
 * 工具需求/追问/风险拒绝/路由策略；引擎侧对缺失字段使用保守默认值，
 * 最终路由与工具依赖以规则库配置为准，LLM 输出仅作补强与审计。</p>
 *
 * @param intentCode          意图编码
 * @param intentName          意图名称
 * @param confidence          置信度 0-1
 * @param entities            抽取实体（引擎会按 Schema 白名单过滤）
 * @param requiredSlotMissing LLM 判定的缺失必填槽位（引擎会以规则库复核）
 * @param needClarify         LLM 判定是否需追问
 * @param clarifyPrompt       LLM 生成的友好追问话术，无缺失为空
 * @param isReject            LLM 判定是否风险拒绝
 * @param rejectReason        拒绝原因，无拒绝为空
 * @param needTool            LLM 判定是否需要工具调用
 * @param toolList            LLM 建议工具列表（引擎以规则库 supportTool 为准）
 * @param routeStrategy       LLM 建议路由（引擎以规则库 routeStrategy 为准）
 * @param reason              解析依据/说明（用于审计与 BadCase 分析）
 * @author WorkBuddy
 * @since 2026-08-14
 */
public record IntentLlmResult(
        String intentCode,
        String intentName,
        double confidence,
        Map<String, String> entities,
        List<String> requiredSlotMissing,
        boolean needClarify,
        String clarifyPrompt,
        boolean isReject,
        String rejectReason,
        boolean needTool,
        List<String> toolList,
        String routeStrategy,
        String reason) {

    /** 兼容工厂：仅提供核心字段，其余按保守默认值填充。 */
    public static IntentLlmResult of(String intentCode, String intentName, double confidence,
                                     Map<String, String> entities, String reason) {
        return new IntentLlmResult(intentCode, intentName, confidence,
                entities == null ? Map.of() : Map.copyOf(entities),
                List.of(), false, "", false, "", false, List.of(), null, reason);
    }

    /** 完整工厂：对齐模式二全部输出字段。 */
    public static IntentLlmResult full(String intentCode, String intentName, double confidence,
                                       Map<String, String> entities, List<String> requiredSlotMissing,
                                       boolean needClarify, String clarifyPrompt,
                                       boolean isReject, String rejectReason,
                                       boolean needTool, List<String> toolList,
                                       String routeStrategy, String reason) {
        return new IntentLlmResult(intentCode, intentName, confidence,
                entities == null ? Map.of() : Map.copyOf(entities),
                requiredSlotMissing == null ? List.of() : List.copyOf(requiredSlotMissing),
                needClarify, clarifyPrompt == null ? "" : clarifyPrompt,
                isReject, rejectReason == null ? "" : rejectReason,
                needTool, toolList == null ? List.of() : List.copyOf(toolList),
                routeStrategy, reason);
    }
}
