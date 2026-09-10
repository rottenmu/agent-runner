package com.zimo.module.ai.workflow;

import com.zimo.framework.ai.chat.AiChatClient;
import com.zimo.framework.ai.chat.AiChatRequest;
import com.zimo.framework.ai.chat.AiChatResponse;
import java.util.LinkedHashMap;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zimo.framework.common.validation.ValidationUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zimo.module.ai.mapper.WfWorkflowRunLogMapper;
import com.zimo.module.ai.mapper.WfWorkflowRunMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

/**
 * 工作流执行引擎。
 *
 * <p>支持节点类型：start / end / task / async_task / condition / loop / manual / fallback；
 * 支持断点调试（debug 模式 + 单步）、任务失败重试、异常兜底、人工介入（approve/reject）。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-08
 */
public class WfWorkflowEngine {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    private final WfWorkflowRunMapper runMapper;
    private final WfWorkflowRunLogMapper logMapper;
    private final ObjectMapper objectMapper;
    /** 聊天客户端（LLM 节点用；未配置时 LLM 节点报错提示）。 */
    private final AiChatClient chatClient;

    public WfWorkflowEngine(
            WfWorkflowRunMapper runMapper,
            WfWorkflowRunLogMapper logMapper,
            ObjectMapper objectMapper) {
        this(runMapper, logMapper, objectMapper, null);
    }

    public WfWorkflowEngine(
            WfWorkflowRunMapper runMapper,
            WfWorkflowRunLogMapper logMapper,
            ObjectMapper objectMapper,
            AiChatClient chatClient) {
        this.runMapper = runMapper;
        this.logMapper = logMapper;
        this.objectMapper = objectMapper;
        this.chatClient = chatClient;
    }

    /* ---------------- 对外控制方法 ---------------- */

    /** 启动执行（可指定调试模式）。 */
    public WfWorkflowRun start(WfWorkflow workflow, Map<String, Object> input, boolean debug) {
        WfWorkflowRun run = new WfWorkflowRun();
        run.setWorkflowId(workflow.getId());
        run.setVersionNo(workflow.getVersionNo());
        run.setDefinitionJson(workflow.getDefinitionJson());
        run.setStatus("running");
        run.setDebug(debug);
        run.setInputJson(writeJson(input));
        run.setStartedAt(LocalDateTime.now());
        run.setCreatedAt(LocalDateTime.now());
        runMapper.insert(run);

        WfContext ctx = new WfContext(run, input, logMapper);
        ctx.record("start", null, "流程启动", "enter", "pending");
        advance(run, ctx, findStartNode(run.getDefinitionJson()), false);
        return run;
    }

    /** 单步执行（断点暂停状态下前进一步）。 */
    public WfWorkflowRun step(Long runId) {
        WfWorkflowRun run = requireRun(runId);
        if (!"paused".equals(run.getStatus())) {
            throw new IllegalArgumentException("仅断点暂停状态可单步执行");
        }
        WfContext ctx = new WfContext(run, logMapper);
        resumePausedNode(run, ctx, true);
        return run;
    }

    /** 恢复执行（跳过当前断点，运行到下一断点或结束）。 */
    public WfWorkflowRun resume(Long runId) {
        WfWorkflowRun run = requireRun(runId);
        if (!"paused".equals(run.getStatus())) {
            throw new IllegalArgumentException("仅断点暂停状态可恢复执行");
        }
        WfContext ctx = new WfContext(run, logMapper);
        resumePausedNode(run, ctx, false);
        return run;
    }

    /** 人工介入确认 / 拒绝。 */
    public WfWorkflowRun approve(Long runId, boolean approved) {
        WfWorkflowRun run = requireRun(runId);
        if (!"manual_wait".equals(run.getStatus())) {
            throw new IllegalArgumentException("当前运行不在人工等待状态");
        }
        WfContext ctx = new WfContext(run, logMapper);
        String nodeId = run.getCurrentNodeId();
        log(run, nodeId, "manual_" + (approved ? "approved" : "rejected"),
                approved ? "success" : "failed",
                approved ? "人工介入：已批准" : "人工介入：已驳回");
        if (!approved) {
            run.setStatus("canceled");
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            ctx.record("end", null, "流程被人工驳回", "exit", "failed");
            return run;
        }
        run.setStatus("running");
        runMapper.updateById(run);
        advance(run, ctx, nextTarget(run.getDefinitionJson(), nodeId), false);
        return run;
    }

    /** 停止运行。 */
    public WfWorkflowRun stop(Long runId) {
        WfWorkflowRun run = requireRun(runId);
        if ("success".equals(run.getStatus()) || "failed".equals(run.getStatus())
                || "canceled".equals(run.getStatus())) {
            throw new IllegalArgumentException("运行已结束，无法停止");
        }
        run.setStatus("canceled");
        run.setFinishedAt(LocalDateTime.now());
        run.setErrorMessage("已手动停止");
        runMapper.updateById(run);
        ctxLog(run, null, "stop", "failed", "流程被手动停止");
        return run;
    }

    /* ---------------- 内部执行 ---------------- */

    /** 恢复暂停节点：单步时先执行暂停点节点再前进，恢复时直接前进。 */
    private void resumePausedNode(WfWorkflowRun run, WfContext ctx, boolean isStep) {
        String pausedNodeId = run.getCurrentNodeId();
        if (isStep) {
            run.setStatus("running");
            runMapper.updateById(run);
            JsonNode node = findNode(run.getDefinitionJson(), pausedNodeId);
            String nextId = executeNode(run, ctx, node);
            if (nextId == null) {
                return; // 暂停或结束由 executeNode 内部处理
            }
            advance(run, ctx, nextId, false);
        } else {
            run.setStatus("running");
            runMapper.updateById(run);
            advance(run, ctx, nextTarget(run.getDefinitionJson(), pausedNodeId), false);
        }
    }

    /** 从指定节点开始推进，直到暂停（断点/人工）或结束。 */
    private void advance(WfWorkflowRun run, WfContext ctx, String nodeId, boolean stepping) {
        while (nodeId != null) {
            if (Thread.currentThread().isInterrupted()) {
                fail(run, ctx, "执行被中断");
                return;
            }
            JsonNode node = findNode(run.getDefinitionJson(), nodeId);
            if (node == null) {
                fail(run, ctx, "节点不存在: " + nodeId);
                return;
            }
            String nodeType = node.path("type").asText("task");

            // 断点暂停：debug 模式下未执行过的断点节点
            if (Boolean.TRUE.equals(run.getDebug())
                    && isBreakpoint(node)
                    && !ctx.executedNodes.contains(nodeId)) {
                run.setCurrentNodeId(nodeId);
                run.setStatus("paused");
                run.setContextJson(ctx.toJson());
                runMapper.updateById(run);
                ctx.record(nodeId, node.path("name").asText(nodeId), "断点暂停",
                        "breakpoint", "waiting");
                return;
            }

            ctx.executedNodes.add(nodeId);
            String nextId;
            try {
                nextId = executeNode(run, ctx, node);
            } catch (Exception e) {
                nextId = handleError(run, ctx, node, e);
                if (nextId == null) {
                    return;
                }
            }
            if (nextId == null) {
                return; // 暂停（manual）或结束
            }
            nodeId = nextId;
        }
        finish(run, ctx);
    }

    /** 执行单个节点，返回下一节点 ID；null 表示暂停或流程结束。 */
    private String executeNode(WfWorkflowRun run, WfContext ctx, JsonNode node) {
        String id = node.path("id").asText();
        String type = node.path("type").asText("task");
        String name = node.path("name").asText(id);
        ctx.record(id, name, "进入节点", "enter", "pending");

        switch (type) {
            case "start":
                ctx.nodeOutputs.put(id, Map.of("status", "started"));
                return nextTarget(run.getDefinitionJson(), id);
            case "end": {
                ctx.nodeOutputs.put(id, Map.of("status", "finished"));
                ctx.record(id, name, "流程结束", "exit", "success");
                finish(run, ctx);
                return null;
            }
            case "task":
            case "async_task": {
                boolean async = "async_task".equals(type);
                executeTask(run, ctx, node, id, name, async);
                return nextTarget(run.getDefinitionJson(), id);
            }
            case "llm": {
                ctx.nodeOutputs.put(id, executeLlm(node, ctx));
                ctx.record(id, name, "LLM 调用完成", "exit", "success");
                return nextTarget(run.getDefinitionJson(), id);
            }
            case "http": {
                ctx.nodeOutputs.put(id, executeHttp(node, ctx));
                ctx.record(id, name, "HTTP 请求完成", "exit", "success");
                return nextTarget(run.getDefinitionJson(), id);
            }
            case "code": {
                ctx.nodeOutputs.put(id, executeCode(node, ctx));
                ctx.record(id, name, "代码执行完成", "exit", "success");
                return nextTarget(run.getDefinitionJson(), id);
            }
            case "template": {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("result", renderTemplate(node.path("config").path("template").asText(""), ctx));
                ctx.nodeOutputs.put(id, out);
                ctx.record(id, name, "模板转换完成", "exit", "success");
                return nextTarget(run.getDefinitionJson(), id);
            }
            case "condition": {
                String expr = node.path("config").path("expression").asText();
                boolean result = evaluateExpression(expr, ctx);
                ctx.nodeOutputs.put(id, Map.of("condition", result));
                ctx.record(id, name, "条件判断: " + expr + " => " + result, "exit", "success");
                return branchTarget(run.getDefinitionJson(), id, result);
            }
            case "loop": {
                int maxIterations = node.path("config").path("maxIterations").asInt(3);
                String bodyStart = node.path("config").path("bodyStart").asText();
                String bodyEnd = node.path("config").path("bodyEnd").asText();
                int executed = 0;
                for (int i = 0; i < maxIterations; i++) {
                    ctx.loopIndex = i;
                    executed = i + 1;
                    ctx.record(id, name, "循环迭代 #" + (i + 1), "enter", "pending");
                    if (StringUtils.hasText(bodyStart)) {
                        advanceSubgraph(run, ctx, bodyStart, bodyEnd);
                        if ("paused".equals(run.getStatus()) || "manual_wait".equals(run.getStatus())) {
                            return null;
                        }
                    }
                }
                ctx.nodeOutputs.put(id, Map.of("iterations", executed));
                ctx.record(id, name, "循环完成（" + executed + " 次）", "exit", "success");
                return nextTarget(run.getDefinitionJson(), id);
            }
            case "manual": {
                run.setCurrentNodeId(id);
                run.setStatus("manual_wait");
                run.setContextJson(ctx.toJson());
                runMapper.updateById(run);
                ctx.record(id, name, "等待人工介入", "manual_wait", "waiting");
                return null;
            }
            case "fallback": {
                ctx.nodeOutputs.put(id, Map.of("fallback", true, "reason", ctx.lastError));
                ctx.record(id, name, "执行异常兜底", "exit", "success");
                return nextTarget(run.getDefinitionJson(), id);
            }
            default:
                throw new IllegalArgumentException("不支持的节点类型: " + type);
        }
    }

    /** 执行任务节点（带重试），成功则记录输出。 */
    private void executeTask(WfWorkflowRun run, WfContext ctx, JsonNode node,
            String id, String name, boolean async) {
        JsonNode config = node.path("config");
        int retry = config.path("retry").asInt(0);
        long retryInterval = config.path("retryInterval").asLong(0);
        int attempt = 0;
        while (true) {
            try {
                Map<String, Object> output = doTask(config, ctx);
                ctx.nodeOutputs.put(id, output);
                ctx.record(id, name, (async ? "异步任务" : "任务") + "执行成功"
                        + (attempt > 0 ? "（第" + (attempt + 1) + "次重试成功）" : ""),
                        "exit", "success");
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt <= retry) {
                    ctx.record(id, name, "执行失败，自动重试 " + attempt + "/" + retry
                            + "：" + e.getMessage(), "retry", "retrying");
                    if (retryInterval > 0) {
                        try {
                            Thread.sleep(retryInterval);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("重试被中断", ie);
                        }
                    }
                } else {
                    throw new IllegalStateException(name + " 执行失败：" + e.getMessage(), e);
                }
            }
        }
    }

    /** LLM 节点：按 systemPrompt + inputMapping 调用聊天客户端。 */
    private Map<String, Object> executeLlm(JsonNode node, WfContext ctx) {
        if (chatClient == null) {
            throw new IllegalStateException("LLM 节点未配置聊天客户端（AiChatClient）");
        }
        JsonNode cfg = node.path("config");
        String model = cfg.path("model").asText("qwen-plus");
        String systemPrompt = cfg.path("systemPrompt").asText("");
        String mapping = renderTemplate(cfg.path("inputMapping").asText("{}"), ctx);
        AiChatResponse resp = chatClient.chat(new AiChatRequest(
                null, mapping, null, model, 0.7, 2000, systemPrompt, List.of()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("model", model);
        out.put("result", resp.success() ? resp.content() : resp.errorMessage());
        return out;
    }

    /** HTTP 节点：按 method/url/headers/body 发起请求。 */
    private Map<String, Object> executeHttp(JsonNode node, WfContext ctx) {
        JsonNode cfg = node.path("config");
        String method = cfg.path("method").asText("GET").toUpperCase();
        String url = renderTemplate(cfg.path("url").asText(""), ctx);
        String headersJson = renderTemplate(cfg.path("headers").asText("{}"), ctx);
        String body = renderTemplate(cfg.path("body").asText(""), ctx);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        RestClient client = RestClient.create();
        String responseBody;
        switch (method) {
            case "POST":
            case "PUT":
            case "PATCH":
                responseBody = client.method(org.springframework.http.HttpMethod.valueOf(method))
                        .uri(url).headers(h -> h.addAll(headers))
                        .body(StringUtils.hasText(body) ? body : "{}")
                        .retrieve().body(String.class);
                break;
            case "DELETE":
                responseBody = client.method(org.springframework.http.HttpMethod.DELETE)
                        .uri(url).headers(h -> h.addAll(headers))
                        .retrieve().body(String.class);
                break;
            default:
                responseBody = client.get().uri(url)
                        .headers(h -> h.addAll(headers))
                        .retrieve().body(String.class);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("method", method);
        out.put("url", url);
        out.put("status", 200);
        out.put("result", responseBody == null ? "" : responseBody);
        return out;
    }

    /** 代码节点：受限 JS 执行（输入 input 对象，取 return 值）。 */
    private Map<String, Object> executeCode(JsonNode node, WfContext ctx) {
        JsonNode cfg = node.path("config");
        String code = cfg.path("code").asText("");
        String inputVars = renderTemplate(cfg.path("inputVars").asText("{}"), ctx);
        String outputVar = cfg.path("outputVar").asText("result");
        Map<String, Object> input = parseJsonObject(inputVars);
        Object result = evalJs(code, input);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(outputVar, result);
        return out;
    }

    /** Nashorn 受限 JS 执行（JDK17 + nashorn-core 独立版）。 */
    private Object evalJs(String code, Map<String, Object> input) {
        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
            if (engine == null) {
                throw new IllegalStateException("JS 执行引擎不可用（缺少 nashorn-core 依赖）");
            }
            engine.getContext().setAttribute("input", input, ScriptContext.ENGINE_SCOPE);
            // 包装为 IIFE：顶层 return 在 ScriptEngine 中非法
            return engine.eval("(function(){" + code + "})()");
        } catch (Exception e) {
            throw new IllegalStateException("代码执行失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> parseJsonObject(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            JsonNode n = objectMapper.readTree(json);
            if (n != null && n.isObject()) {
                return objectMapper.convertValue(n,
                        new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
            }
        } catch (Exception e) {
            /* 非 JSON 时忽略 */
        }
        return Map.of();
    }

    /** 任务实际执行：渲染 prompt 模板并构造输出（简化实现，后续可接入技能 / API / LLM）。 */
    private Map<String, Object> doTask(JsonNode config, WfContext ctx) {
        String prompt = config.path("prompt").asText("");
        String rendered = renderTemplate(prompt, ctx);
        String taskType = config.path("taskType").asText("echo");
        if ("fail".equals(taskType)) {
            throw new IllegalStateException("模拟任务失败（taskType=fail）");
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("taskType", taskType);
        output.put("result", StringUtils.hasText(rendered) ? rendered : "ok");
        output.put("nodeId", ctx.currentRunNodeId());
        return output;
    }

    /** 异常处理：失败 → 兜底节点；无兜底则失败结束。 */
    private String handleError(WfWorkflowRun run, WfContext ctx, JsonNode node, Exception e) {
        ctx.lastError = e.getMessage();
        String id = node.path("id").asText();
        String name = node.path("name").asText(id);
        ctx.record(id, name, "节点异常: " + e.getMessage(), "error", "failed");
        String fallbackId = findFallbackNode(run.getDefinitionJson(), ctx);
        if (fallbackId != null) {
            ctx.record(id, name, "已路由到异常兜底节点", "error", "failed");
            return fallbackId;
        }
        fail(run, ctx, name + " 执行失败: " + e.getMessage());
        return null;
    }

    /** 执行子图（循环体），从 bodyStart 推进到 bodyEnd。 */
    private void advanceSubgraph(WfWorkflowRun run, WfContext ctx, String startId, String endId) {
        String nodeId = startId;
        while (nodeId != null) {
            JsonNode node = findNode(run.getDefinitionJson(), nodeId);
            if (node == null) {
                fail(run, ctx, "子图节点不存在: " + nodeId);
                return;
            }
            if (Boolean.TRUE.equals(run.getDebug()) && isBreakpoint(node)
                    && !ctx.executedNodes.contains(nodeId)) {
                run.setCurrentNodeId(nodeId);
                run.setStatus("paused");
                run.setContextJson(ctx.toJson());
                runMapper.updateById(run);
                ctx.record(nodeId, node.path("name").asText(nodeId), "断点暂停（循环体内）",
                        "breakpoint", "waiting");
                return;
            }
            ctx.executedNodes.add(nodeId);
            String nextId;
            try {
                nextId = executeNode(run, ctx, node);
            } catch (Exception e) {
                nextId = handleError(run, ctx, node, e);
            }
            if (nextId == null) {
                return;
            }
            if (nodeId.equals(endId)) {
                return;
            }
            nodeId = nextId;
        }
    }

    /** 正常结束。 */
    private void finish(WfWorkflowRun run, WfContext ctx) {
        run.setStatus("success");
        run.setCurrentNodeId(null);
        run.setOutputJson(ctx.outputJson());
        run.setFinishedAt(LocalDateTime.now());
        runMapper.updateById(run);
        ctx.record("end", null, "运行成功", "exit", "success");
    }

    /** 失败结束。 */
    private void fail(WfWorkflowRun run, WfContext ctx, String message) {
        run.setStatus("failed");
        run.setErrorMessage(message);
        run.setFinishedAt(LocalDateTime.now());
        run.setOutputJson(ctx.outputJson());
        runMapper.updateById(run);
        ctx.record("end", null, message, "error", "failed");
    }

    /* ---------------- 定义解析 ---------------- */

    private String findStartNode(String definitionJson) {
        JsonNode def = parse(definitionJson);
        JsonNode nodes = def.path("nodes");
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                if ("start".equals(node.path("type").asText())) {
                    return node.path("id").asText();
                }
            }
        }
        throw new IllegalArgumentException("流程定义缺少 start 节点");
    }

    private JsonNode findNode(String definitionJson, String nodeId) {
        JsonNode def = parse(definitionJson);
        JsonNode nodes = def.path("nodes");
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                if (nodeId.equals(node.path("id").asText())) {
                    return node;
                }
            }
        }
        return null;
    }

    /** 普通出边目标。 */
    private String nextTarget(String definitionJson, String sourceId) {
        JsonNode def = parse(definitionJson);
        JsonNode edges = def.path("edges");
        if (edges.isArray()) {
            for (JsonNode edge : edges) {
                if (sourceId.equals(edge.path("source").asText())
                        && !edge.hasNonNull("branch")) {
                    return edge.path("target").asText();
                }
            }
        }
        return null;
    }

    /** 条件分支目标。 */
    private String branchTarget(String definitionJson, String sourceId, boolean result) {
        JsonNode def = parse(definitionJson);
        JsonNode edges = def.path("edges");
        String want = String.valueOf(result);
        if (edges.isArray()) {
            for (JsonNode edge : edges) {
                if (sourceId.equals(edge.path("source").asText())
                        && want.equals(edge.path("branch").asText())) {
                    return edge.path("target").asText();
                }
            }
        }
        return nextTarget(definitionJson, sourceId);
    }

    private String findFallbackNode(String definitionJson, WfContext ctx) {
        JsonNode def = parse(definitionJson);
        JsonNode nodes = def.path("nodes");
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                if ("fallback".equals(node.path("type").asText())
                        && !ctx.fallbackUsed) {
                    ctx.fallbackUsed = true;
                    return node.path("id").asText();
                }
            }
        }
        return null;
    }

    private boolean isBreakpoint(JsonNode node) {
        return node.path("config").path("breakpoint").asBoolean(false);
    }

    /* ---------------- 表达式与模板 ---------------- */

    private boolean evaluateExpression(String expression, WfContext ctx) {
        if (!StringUtils.hasText(expression)) {
            return true;
        }
        Object value = evaluateSpel(expression, ctx);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s) || "true".equalsIgnoreCase(s);
        }
        return value != null;
    }

    private Object evaluateSpel(String expression, WfContext ctx) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.addPropertyAccessor(new MapAccessor());
        context.setVariable("ctx", ctx.toMap());
        context.setVariable("input", ctx.input);
        return PARSER.parseExpression(expression).getValue(context);
    }

    /** 渲染 {{var}} / {{ctx.key}} 模板。 */
    private String renderTemplate(String template, WfContext ctx) {
        if (!StringUtils.hasText(template)) {
            return "";
        }
        String result = template;
        int idx = result.indexOf("{{");
        while (idx >= 0) {
            int end = result.indexOf("}}", idx);
            if (end < 0) {
                break;
            }
            String expr = result.substring(idx + 2, end).trim();
            String value = String.valueOf(resolveVariable(expr, ctx));
            result = result.substring(0, idx) + value + result.substring(end + 2);
            idx = result.indexOf("{{");
        }
        return result;
    }

    private Object resolveVariable(String expr, WfContext ctx) {
        try {
            if (expr.startsWith("ctx.")) {
                return evaluateSpel("#" + expr, ctx);
            }
            if (expr.startsWith("input.")) {
                return evaluateSpel("#input." + expr.substring("input.".length()), ctx);
            }
            Object direct = evaluateSpel(expr, ctx);
            return direct != null ? direct : "";
        } catch (Exception e) {
            return expr;
        }
    }

    /* ---------------- 日志与工具 ---------------- */

    private void ctxLog(WfWorkflowRun run, String nodeId, String action, String result, String message) {
        log(run, nodeId, action, result, message);
    }

    private void log(WfWorkflowRun run, String nodeId, String action, String result, String message) {
        WfWorkflowRunLog log = new WfWorkflowRunLog();
        log.setRunId(run.getId());
        log.setNodeId(nodeId);
        log.setNodeName(nodeId);
        log.setAction(action);
        log.setResult(result);
        log.setMessage(message);
        log.setBreakpoint("breakpoint".equals(action));
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    private WfWorkflowRun requireRun(Long runId) {
        WfWorkflowRun run = runMapper.selectById(runId);
        ValidationUtil.requireNotNull(run, "运行实例不存在: id=");
        return run;
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("流程定义 JSON 解析失败", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 执行上下文。 */
    static class WfContext {
        final WfWorkflowRun run;
        final WfWorkflowRunLogMapper logMapper;
        final Map<String, Object> input;
        final Map<String, Object> output = new LinkedHashMap<>();
        final Map<String, Object> nodeOutputs = new LinkedHashMap<>();
        final List<String> executedNodes = new ArrayList<>();
        String lastError;
        boolean fallbackUsed;
        int loopIndex = -1;

        WfContext(WfWorkflowRun run, Map<String, Object> input, WfWorkflowRunLogMapper logMapper) {
            this.run = run;
            this.logMapper = logMapper;
            this.input = input == null ? new LinkedHashMap<>() : input;
        }

        WfContext(WfWorkflowRun run, WfWorkflowRunLogMapper logMapper) {
            this.run = run;
            this.logMapper = logMapper;
            Map<String, Object> restored = readJson(run.getContextJson());
            Object inputObj = restored.get("input");
            this.input = inputObj instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>();
            Object outputs = restored.get("nodeOutputs");
            if (outputs instanceof Map<?, ?> m) {
                nodeOutputs.putAll((Map<String, Object>) m);
            }
            Object exec = restored.get("executedNodes");
            if (exec instanceof List<?> l) {
                for (Object o : l) {
                    executedNodes.add(String.valueOf(o));
                }
            }
            this.lastError = restored.get("lastError") == null ? null : String.valueOf(restored.get("lastError"));
        }

        String currentRunNodeId() {
            return run.getCurrentNodeId();
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("input", input);
            map.put("nodeOutputs", nodeOutputs);
            map.put("loopIndex", loopIndex);
            map.put("lastError", lastError);
            map.put("executedNodes", executedNodes);
            return map;
        }

        String toJson() {
            try {
                return new ObjectMapper().writeValueAsString(toMap());
            } catch (Exception e) {
                return "{}";
            }
        }

        String outputJson() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("output", output);
            out.put("nodeOutputs", nodeOutputs);
            try {
                return new ObjectMapper().writeValueAsString(out);
            } catch (Exception e) {
                return "{}";
            }
        }

        private Map<String, Object> readJson(String json) {
            if (!StringUtils.hasText(json)) {
                return new LinkedHashMap<>();
            }
            try {
                return new ObjectMapper().readValue(json, Map.class);
            } catch (Exception e) {
                return new LinkedHashMap<>();
            }
        }

        void record(String nodeId, String nodeName, String message, String action, String result) {
            WfWorkflowRunLog log = new WfWorkflowRunLog();
            log.setRunId(run.getId());
            log.setNodeId(nodeId);
            log.setNodeName(nodeName);
            log.setNodeType(nodeId);
            log.setAction(action);
            log.setResult(result);
            log.setMessage(message);
            log.setBreakpoint("breakpoint".equals(action));
            log.setCreatedAt(LocalDateTime.now());
            this.logMapper.insert(log);
        }
    }
}
