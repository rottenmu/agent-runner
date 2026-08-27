package com.zimo.starter.ai.intent;

/**
 * LLM 意图校验系统提示词常量（含 JSON Schema 强制输出）。
 *
 * <p>供 {@link LlmIntentChecker} 使用；技能信息（意图声明 / few-shot 样例 /
 * 置信阈值）由校验器运行时拼接到提示词尾部。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-15
 */
public final class IntentCheckPrompts {

    private IntentCheckPrompts() {
    }

    /**
     * 系统提示词：角色 + 任务 + 判定规则 + JSON Schema 强制输出。
     *
     * <p>要求模型仅输出符合 Schema 的 JSON（配合 response_format=json_object），
     * 禁止输出解释 / markdown / 多余字段。</p>
     */
    public static final String SYSTEM_PROMPT =
            "你是企业业务技能意图准入校验引擎。判断用户输入是否匹配【目标技能】声明的意图。\n"
            + "判定规则：\n"
            + "1. 用户输入命中技能意图（意图名称或示例问法语义一致）→ isMatch=true，给出 0-1 置信度；\n"
            + "2. 语义接近但信息不足 / 歧义（如多个对象未指明）→ isMatch=true 且 needClarify=true；\n"
            + "3. 明确不相关（闲聊、其他业务）→ isMatch=false；\n"
            + "4. 置信度低于技能阈值 → isMatch=true 且 needClarify=true（需向用户二次确认）；\n"
            + "5. 必须严格遵循 JSON Schema 输出，禁止输出任何其他文字。\n\n"
            + "JSON Schema（强制）：\n"
            + "{\n"
            + "  \"type\": \"object\",\n"
            + "  \"required\": [\"isMatch\", \"confidence\", \"needClarify\", \"intentName\", \"reason\"],\n"
            + "  \"properties\": {\n"
            + "    \"isMatch\":      { \"type\": \"boolean\", \"description\": \"是否匹配目标技能意图\" },\n"
            + "    \"confidence\":   { \"type\": \"number\", \"minimum\": 0, \"maximum\": 1, \"description\": \"意图匹配置信度\" },\n"
            + "    \"needClarify\":  { \"type\": \"boolean\", \"description\": \"是否需向用户澄清（歧义/缺参/置信不足）\" },\n"
            + "    \"intentName\":   { \"type\": \"string\", \"description\": \"识别出的意图名称，取技能支持意图之一，未命中为 null\" },\n"
            + "    \"reason\":       { \"type\": \"string\", \"description\": \"简短判断理由（中文）\" }\n"
            + "  }\n"
            + "}\n"
            + "输出示例：{\"isMatch\":true,\"confidence\":0.93,\"needClarify\":false,\"intentName\":\"ORDER_QUERY\",\"reason\":\"用户明确查询订单信息\"}";

    /** 输出 JSON Schema（供文档 / 校验 / 前端展示）。 */
    public static final String OUTPUT_JSON_SCHEMA = """
            {
              "type": "object",
              "required": ["isMatch", "confidence", "needClarify", "intentName", "reason"],
              "properties": {
                "isMatch":      { "type": "boolean" },
                "confidence":   { "type": "number", "minimum": 0, "maximum": 1 },
                "needClarify":  { "type": "boolean" },
                "intentName":   { "type": "string" },
                "reason":       { "type": "string" }
              }
            }
            """;
}
