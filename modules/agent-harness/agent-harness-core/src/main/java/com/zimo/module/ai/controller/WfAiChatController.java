package com.zimo.module.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zimo.starter.ai.AiAgentReply;
import com.zimo.starter.ai.AiAgentService;
import com.zimo.framework.common.ApiResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作流 AI 对话生成接口：通过自然语言描述创建 / 修改工作流。
 *
 * <p>优先调用智能体 LLM 生成流程定义 JSON；模型不可用或解析失败时回退到内置规则模板。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
@RestController
@RequestMapping("/api/biz/wf/ai")
public class WfAiChatController {

    private static final String SYSTEM_PROMPT = """
            你是工作流编排助手。根据用户的中文描述，生成工作流 JSON 定义。

            工作流 JSON 结构：
            {
              "nodes": [
                {"id": "n1", "type": "start", "name": "开始", "config": {}},
                {"id": "n2", "type": "task", "name": "节点名", "config": {"taskType": "echo", "prompt": "执行说明，可含 {{input.xxx}}", "retry": 0, "breakpoint": false}},
                {"id": "n3", "type": "condition", "name": "判断名", "config": {"expression": "#ctx.input.xxx == true", "breakpoint": false}},
                {"id": "n4", "type": "loop", "name": "循环名", "config": {"maxIterations": 3, "bodyStart": "循环体起点节点id", "bodyEnd": "循环体终点节点id"}},
                {"id": "n5", "type": "async_task", "name": "异步任务名", "config": {"taskType": "echo", "prompt": "说明"}},
                {"id": "n6", "type": "manual", "name": "人工审批名", "config": {"prompt": "审批说明"}},
                {"id": "n7", "type": "fallback", "name": "异常兜底名", "config": {"prompt": "降级说明"}},
                {"id": "n8", "type": "end", "name": "结束", "config": {}}
              ],
              "edges": [
                {"id": "e1", "source": "n1", "target": "n2"},
                {"id": "e2", "source": "n3", "target": "n4", "branch": "true", "label": "是"},
                {"id": "e3", "source": "n3", "target": "n8", "branch": "false", "label": "否"}
              ]
            }

            节点类型说明：
            - start/end：流程起止，必须有且各一个
            - task：普通任务（可配置 retry 重试次数、breakpoint 断点）
            - async_task：异步任务
            - condition：条件分支，expression 用 SpEL 表达式，true/false 通过 branch 边路由
            - loop：循环节点，必须配置 bodyStart/bodyEnd 指向循环体内首尾节点
            - manual：人工介入节点，执行到此处等待人工审批
            - fallback：异常兜底节点，任务失败自动路由到此处

            规则：
            1. 只输出 JSON，不要任何解释文字，不要用 markdown 代码块
            2. 每个节点 id 唯一（n1, n2, n3...），name 用中文且贴合用户意图
            3. 边必须连通：从 start 出发能到达 end，不要遗漏 target
            4. 如果用户要求修改已有流程，基于给定的 currentDefinition 调整而不是重新生成
            5. 条件表达式中变量用 #ctx.input.xxx（如金额 #ctx.input.amount > 1000）
            """;

    private final AiAgentService aiAgentService;
    private final ObjectMapper objectMapper;

    public WfAiChatController(AiAgentService aiAgentService, ObjectMapper objectMapper) {
        this.aiAgentService = aiAgentService;
        this.objectMapper = objectMapper;
    }

    /**
     * AI 对话生成 / 修改工作流。
     *
     * @param body 包含 message（必填）、sessionId（会话记忆，可空）、currentDefinition（当前流程，可空）
     * @return { reply: AI 回复文本, workflow: 生成的工作流定义（可为 null） }
     */
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String message = body == null ? null : (String) body.get("message");
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("消息不能为空");
        }
        String sessionId = body == null ? null : (String) body.get("sessionId");
        String currentDefinition = body == null ? null : (String) body.get("currentDefinition");

        StringBuilder prompt = new StringBuilder();
        if (StringUtils.hasText(currentDefinition)) {
            prompt.append("当前工作流定义如下，请在此基础上修改：\n")
                    .append(currentDefinition).append("\n\n");
        }
        prompt.append("用户需求：").append(message);

        String replyText;
        try {
            AiAgentReply reply = aiAgentService.chat(SYSTEM_PROMPT + "\n\n" + prompt, sessionId);
            replyText = reply == null ? "" : reply.content();
        } catch (Exception e) {
            replyText = "";
        }

        JsonNode workflow = extractWorkflow(replyText);
        Map<String, Object> result = new LinkedHashMap<>();
        if (workflow != null && hasValidNodes(workflow)) {
            result.put("reply", "已根据你的描述生成工作流，点击下方按钮应用到画布。\n\n" + replyText);
            result.put("workflow", workflow);
            result.put("source", "llm");
        } else {
            JsonNode fallback = fallbackWorkflow(message, currentDefinition);
            if (fallback != null) {
                result.put("reply", "AI 模型暂时无法生成，已使用内置模板生成工作流：" + message);
                result.put("workflow", fallback);
                result.put("source", "fallback");
            } else {
                result.put("reply", "抱歉，无法从描述中生成工作流：" + truncate(replyText, 200));
                result.put("workflow", null);
                result.put("source", "none");
            }
        }
        return ApiResponse.ok(result);
    }

    /** 从 LLM 回复文本中提取 JSON（容忍 markdown 代码块包裹）。 */
    private JsonNode extractWorkflow(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(text.trim());
        // 去掉 markdown ```json ... ``` 包裹
        String cleaned = text.replaceAll("(?s)```(?:json)?\\s*", "").replaceAll("(?s)\\s*```", "").trim();
        if (!cleaned.equals(text.trim())) {
            candidates.add(cleaned);
        }
        // 截取第一个 { 到最后一个 }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            candidates.add(text.substring(start, end + 1));
        }
        for (String candidate : candidates) {
            try {
                JsonNode node = objectMapper.readTree(candidate);
                if (node.isObject() && (node.has("nodes") || node.has("edges"))) {
                    return node;
                }
            } catch (Exception ignored) {
                // 尝试下一个候选
            }
        }
        return null;
    }

    private boolean hasValidNodes(JsonNode workflow) {
        JsonNode nodes = workflow.get("nodes");
        return nodes != null && nodes.isArray() && nodes.size() > 0;
    }

    /** 内置规则回退：按关键词生成简单流程。 */
    private JsonNode fallbackWorkflow(String message, String currentDefinition) {
        String text = message == null ? "" : message;
        if (text.contains("审批") || text.contains("复核") || text.contains("审核")) {
            return approveWorkflow(text);
        }
        if (text.contains("循环") || text.contains("批量") || text.contains("重复")) {
            return loopWorkflow(text);
        }
        return simpleWorkflow(text);
    }

    private JsonNode simpleWorkflow(String text) {
        ObjectNode def = objectMapper.createObjectNode();
        ArrayNode nodes = def.putArray("nodes");
        nodes.addObject().put("id", "n1").put("type", "start").put("name", "开始").putObject("config");
        nodes.addObject().put("id", "n2").put("type", "task").put("name", "执行任务")
                .putObject("config").put("taskType", "echo").put("prompt", text);
        nodes.addObject().put("id", "n3").put("type", "end").put("name", "结束").putObject("config");
        ArrayNode edges = def.putArray("edges");
        edges.addObject().put("id", "e1").put("source", "n1").put("target", "n2");
        edges.addObject().put("id", "e2").put("source", "n2").put("target", "n3");
        return def;
    }

    private JsonNode approveWorkflow(String text) {
        ObjectNode def = objectMapper.createObjectNode();
        ArrayNode nodes = def.putArray("nodes");
        nodes.addObject().put("id", "n1").put("type", "start").put("name", "开始").putObject("config");
        nodes.addObject().put("id", "n2").put("type", "task").put("name", "提交申请")
                .putObject("config").put("taskType", "echo").put("prompt", text);
        nodes.addObject().put("id", "n3").put("type", "condition").put("name", "金额是否超限")
                .putObject("config").put("expression", "#ctx.input.amount > 1000");
        nodes.addObject().put("id", "n4").put("type", "manual").put("name", "人工审批")
                .putObject("config").put("prompt", "请审批该申请");
        nodes.addObject().put("id", "n5").put("type", "task").put("name", "通知结果")
                .putObject("config").put("taskType", "echo").put("prompt", "审批完成，发送通知");
        nodes.addObject().put("id", "n6").put("type", "end").put("name", "结束").putObject("config");
        ArrayNode edges = def.putArray("edges");
        edges.addObject().put("id", "e1").put("source", "n1").put("target", "n2");
        edges.addObject().put("id", "e2").put("source", "n2").put("target", "n3");
        edges.addObject().put("id", "e3").put("source", "n3").put("target", "n4").put("branch", "true").put("label", "是");
        edges.addObject().put("id", "e4").put("source", "n3").put("target", "n5").put("branch", "false").put("label", "否");
        edges.addObject().put("id", "e5").put("source", "n4").put("target", "n5");
        edges.addObject().put("id", "e6").put("source", "n5").put("target", "n6");
        return def;
    }

    private JsonNode loopWorkflow(String text) {
        ObjectNode def = objectMapper.createObjectNode();
        ArrayNode nodes = def.putArray("nodes");
        nodes.addObject().put("id", "n1").put("type", "start").put("name", "开始").putObject("config");
        nodes.addObject().put("id", "n2").put("type", "loop").put("name", "批量循环")
                .putObject("config").put("maxIterations", 5).put("bodyStart", "n3").put("bodyEnd", "n4");
        nodes.addObject().put("id", "n3").put("type", "task").put("name", "处理单项")
                .putObject("config").put("taskType", "echo").put("prompt", "处理 {{ctx.loopIndex}} 项");
        nodes.addObject().put("id", "n4").put("type", "task").put("name", "记录结果")
                .putObject("config").put("taskType", "echo").put("prompt", "记录处理结果");
        nodes.addObject().put("id", "n5").put("type", "end").put("name", "结束").putObject("config");
        ArrayNode edges = def.putArray("edges");
        edges.addObject().put("id", "e1").put("source", "n1").put("target", "n2");
        edges.addObject().put("id", "e2").put("source", "n2").put("target", "n5");
        edges.addObject().put("id", "e3").put("source", "n3").put("target", "n4");
        return def;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
