package com.zimo.module.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.ai.chat.AiChatClient;
import com.zimo.framework.ai.chat.AiChatRequest;
import com.zimo.framework.ai.chat.AiChatResponse;
import com.zimo.module.ai.runtime.AgentRunRequest;
import com.zimo.module.ai.runtime.RuntimeNodeHandler;
import com.zimo.module.ai.tool.ToolRegistryService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Agent 运行时节点执行器。
 *
 * <p>所属模块：agent-harness 运行时内核。负责单个图节点的具体执行（任务、LLM、HTTP、
 * 代码、模板、条件判断等），不负责图推进与状态持久化——这些由 {@link GraphEngine} 承担，
 * 以保持"节点能力"与"编排调度"职责分离。</p>
 *
 * <p>并行节点的子节点由引擎并发调度，本执行器只返回子节点清单。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class GraphNodeExecutor {

    /** 聊天客户端（LLM 节点使用）；未配置时 LLM 节点抛错提示。 */
    private final AiChatClient chatClient;

    /** JSON 映射器，用于模板输入解析与输出序列化。 */
    private final ObjectMapper objectMapper;

    /** 表达式与模板支持（条件求值、{{var}} 渲染、JSON 参数解析）。 */
    private final GraphExpressions expressions;

    /** 运行时节点处理器（agent / tool 节点）；未装配时这两类节点显式报错。 */
    private final RuntimeNodeHandler runtimeHandler;

    /**
     * 构造节点执行器（不含运行时节点能力）。
     *
     * @param chatClient 聊天客户端，可为 null（此时 LLM 节点不可用）
     * @param objectMapper JSON 映射器，不可为空
     */
    public GraphNodeExecutor(AiChatClient chatClient, ObjectMapper objectMapper) {
        this(chatClient, objectMapper, null);
    }

    /**
     * 构造节点执行器。
     *
     * @param chatClient 聊天客户端，可为 null（此时 LLM 节点不可用）
     * @param objectMapper JSON 映射器，不可为空
     * @param runtimeHandler 运行时节点处理器，可为 null（此时 agent / tool 节点不可用）
     */
    public GraphNodeExecutor(AiChatClient chatClient, ObjectMapper objectMapper,
                             RuntimeNodeHandler runtimeHandler) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.expressions = new GraphExpressions(objectMapper);
        this.runtimeHandler = runtimeHandler;
    }

    /**
     * 执行单个节点。
     *
     * <p>执行流程：按节点类型分发到对应能力方法，返回执行结果与调度提示。
     * 节点自身不决定下一跳，条件结果、并行子节点、人工挂起等提示交由引擎处理。</p>
     *
     * @param nodeId 节点 ID，不可为空
     * @param node 节点定义 JSON，不可为空
     * @param state 运行状态，用于读取入参、输出与循环序号
     * @return 节点执行结果
     * @throws IllegalArgumentException 节点类型不支持时抛出
     * @throws IllegalStateException 节点执行失败时抛出
     */
    public NodeOutcome execute(String nodeId, JsonNode node, GraphState state) {
        String type = node.path("type").asText("task");
        switch (type) {
            case "start":
                return NodeOutcome.output(Map.of("status", "started"));
            case "end":
                return NodeOutcome.finished(Map.of("status", "finished"));
            case "task":
            case "async_task":
                return NodeOutcome.output(executeTask(node, nodeId, state));
            case "llm":
                return NodeOutcome.output(executeLlm(node, state));
            case "http":
                return NodeOutcome.output(executeHttp(node, state));
            case "code":
                return NodeOutcome.output(executeCode(node, state));
            case "template":
                return NodeOutcome.output(executeTemplate(node, state));
            case "condition":
                return executeCondition(node, state);
            case "manual":
            case "human":
                return NodeOutcome.suspend(humanPayload(node, nodeId, state));
            case "fallback":
                return NodeOutcome.output(Map.of("fallback", true,
                        "reason", state.lastError() == null ? "" : state.lastError()));
            case "parallel":
                return NodeOutcome.parallel(readChildren(node));
            case "supervisor":
                return NodeOutcome.supervisor(readChildren(node), readStrategy(node));
            case "agent":
                return NodeOutcome.output(executeAgentNode(node, nodeId, state));
            case "tool":
                return NodeOutcome.output(executeToolNode(node, nodeId, state));
            default:
                throw new IllegalArgumentException("不支持的节点类型: " + type);
        }
    }

    /**
     * agent 节点：交给统一运行时执行（闸门 / 路由 / 脱敏 / 记忆留痕由运行时统一负责）。
     *
     * <p>参数优先级：节点 config 显式值 &gt; 图输入 {@code input} &gt; 缺省。
     * 这样同一张图既可以被"绑死某个智能体"复用，也可以在运行时按入参切换智能体。</p>
     *
     * @param node 节点定义
     * @param nodeId 节点 ID
     * @param state 运行状态
     * @return 节点输出
     */
    private Map<String, Object> executeAgentNode(JsonNode node, String nodeId, GraphState state) {
        if (runtimeHandler == null) {
            throw new IllegalStateException("agent 节点未装配运行时（RuntimeNodeHandler）");
        }
        JsonNode cfg = node.path("config");
        AgentRunRequest request = new AgentRunRequest(
                resolveParam(cfg, "agentId", state, ""),
                expressions.render(cfg.path("prompt").asText(""), state),
                resolveParam(cfg, "tenantId", state, null),
                resolveParam(cfg, "userId", state, null),
                resolveParam(cfg, "sessionId", state, null),
                resolveParam(cfg, "channel", state, "graph"),
                readTextList(cfg.path("toolIds")));
        return runtimeHandler.executeAgent(request);
    }

    /**
     * tool 节点：交给工具市场治理层执行（权限校验 + 审计留痕）。
     *
     * <p>{@code args} 支持模板渲染后解析为 JSON 对象，从而把上游节点输出作为工具入参。</p>
     *
     * @param node 节点定义
     * @param nodeId 节点 ID
     * @param state 运行状态
     * @return 节点输出
     */
    private Map<String, Object> executeToolNode(JsonNode node, String nodeId, GraphState state) {
        if (runtimeHandler == null) {
            throw new IllegalStateException("tool 节点未装配运行时（RuntimeNodeHandler）");
        }
        JsonNode cfg = node.path("config");
        String argsJson = expressions.render(cfg.path("args").asText("{}"), state);
        ToolRegistryService.ToolInvokeRequest request = new ToolRegistryService.ToolInvokeRequest(
                resolveParam(cfg, "toolId", state, ""),
                resolveParam(cfg, "tenantId", state, null),
                resolveParam(cfg, "userId", state, null),
                expressions.parseObject(argsJson),
                state.runId(),
                nodeId);
        return runtimeHandler.executeTool(request);
    }

    /** 读取参数：节点 config 优先，其次图输入，最后缺省值。 */
    private String resolveParam(JsonNode cfg, String name, GraphState state, String fallback) {
        String configured = cfg.path(name).asText("");
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        Object fromInput = state.input().get(name);
        if (fromInput != null && StringUtils.hasText(String.valueOf(fromInput))) {
            return String.valueOf(fromInput);
        }
        return fallback;
    }

    /** 读取字符串数组配置（如 toolIds）。 */
    private List<String> readTextList(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                String text = item.asText("");
                if (StringUtils.hasText(text)) {
                    values.add(text);
                }
            }
        }
        return values;
    }

    /**
     * 构造人工节点的挂起载荷（审批提示语 + 表单定义）。
     *
     * <p>载荷随 checkpoint 落库，待审批查询接口直接读它渲染审批界面，
     * 前端因此不需要额外再请求一次图定义，也不会读到「图已改版、运行是旧版」的错配提示语。</p>
     *
     * @param node 节点定义
     * @param nodeId 节点 ID
     * @param state 运行状态
     * @return 挂起载荷：nodeId/name/prompt/requireComment/form
     */
    private Map<String, Object> humanPayload(JsonNode node, String nodeId, GraphState state) {
        JsonNode cfg = node.path("config");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("name", node.path("name").asText(nodeId));
        payload.put("prompt", expressions.render(cfg.path("prompt").asText(""), state));
        payload.put("requireComment", cfg.path("requireComment").asBoolean(false));
        JsonNode form = cfg.path("form");
        if (form.isObject()) {
            payload.put("form", expressions.parseObject(expressions.render(form.toString(), state)));
        }
        return payload;
    }

    /** 条件节点：求值并返回布尔结果，引擎据此选择分支边。 */
    private NodeOutcome executeCondition(JsonNode node, GraphState state) {
        String expression = node.path("config").path("expression").asText("");
        boolean result = expressions.test(expression, state);
        return NodeOutcome.condition(Map.of("condition", result), result);
    }

    /** 任务节点：按配置重试，成功返回结果。 */
    private Map<String, Object> executeTask(JsonNode node, String nodeId, GraphState state) {
        JsonNode config = node.path("config");
        int retry = config.path("retry").asInt(0);
        long retryInterval = config.path("retryInterval").asLong(0);
        int attempt = 0;
        while (true) {
            try {
                return doTask(config, nodeId, state);
            } catch (Exception e) {
                attempt++;
                if (attempt > retry) {
                    throw new IllegalStateException("任务执行失败：" + e.getMessage(), e);
                }
                sleepQuietly(retryInterval);
            }
        }
    }

    /** 任务实际执行：按需延迟后渲染 prompt 模板，taskType=fail 时模拟失败。 */
    private Map<String, Object> doTask(JsonNode config, String nodeId, GraphState state) {
        long delayMs = config.path("delayMs").asLong(0);
        sleepQuietly(delayMs);
        String rendered = expressions.render(config.path("prompt").asText(""), state);
        String taskType = config.path("taskType").asText("echo");
        if ("fail".equals(taskType)) {
            throw new IllegalStateException("模拟任务失败（taskType=fail）");
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("taskType", taskType);
        output.put("result", StringUtils.hasText(rendered) ? rendered : "ok");
        output.put("nodeId", nodeId);
        if ("conflict".equals(taskType)) {
            // 模拟"该 worker 的结论与其他方冲突"：回执 status=conflict 由主管汇总为冲突。
            // 与 taskType=fail 同族——都是占位/演示用的确定性任务类型。
            output.put("status", "conflict");
        }
        return output;
    }

    /** LLM 节点：按 systemPrompt + inputMapping 调用聊天客户端。 */
    private Map<String, Object> executeLlm(JsonNode node, GraphState state) {
        if (chatClient == null) {
            throw new IllegalStateException("LLM 节点未配置聊天客户端（AiChatClient）");
        }
        JsonNode cfg = node.path("config");
        String model = cfg.path("model").asText("qwen-plus");
        String systemPrompt = cfg.path("systemPrompt").asText("");
        String mapping = expressions.render(cfg.path("inputMapping").asText("{}"), state);
        AiChatResponse response = chatClient.chat(new AiChatRequest(
                null, mapping, null, model, 0.7, 2000, systemPrompt, List.of()));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("model", model);
        output.put("result", response.success() ? response.content() : response.errorMessage());
        return output;
    }

    /** HTTP 节点：按 method/url/headers/body 发起请求。 */
    private Map<String, Object> executeHttp(JsonNode node, GraphState state) {
        JsonNode cfg = node.path("config");
        String method = cfg.path("method").asText("GET").toUpperCase();
        String url = expressions.render(cfg.path("url").asText(""), state);
        String body = expressions.render(cfg.path("body").asText(""), state);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String responseBody = requestHttp(method, url, headers, body);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("method", method);
        output.put("url", url);
        output.put("status", 200);
        output.put("result", responseBody == null ? "" : responseBody);
        return output;
    }

    /** 发起 HTTP 请求，按方法选择携带 body 或空体。 */
    private String requestHttp(String method, String url, HttpHeaders headers, String body) {
        RestClient client = RestClient.create();
        HttpMethod httpMethod = HttpMethod.valueOf(method);
        if (httpMethod == HttpMethod.GET || httpMethod == HttpMethod.DELETE) {
            return client.method(httpMethod).uri(url)
                    .headers(h -> h.addAll(headers))
                    .retrieve().body(String.class);
        }
        return client.method(httpMethod).uri(url)
                .headers(h -> h.addAll(headers))
                .body(StringUtils.hasText(body) ? body : "{}")
                .retrieve().body(String.class);
    }

    /** 代码节点：受限 JS 执行，输入为 input 对象。 */
    private Map<String, Object> executeCode(JsonNode node, GraphState state) {
        JsonNode cfg = node.path("config");
        String code = cfg.path("code").asText("");
        String inputVars = expressions.render(cfg.path("inputVars").asText("{}"), state);
        String outputVar = cfg.path("outputVar").asText("result");
        Object result = evalJs(code, expressions.parseObject(inputVars));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put(outputVar, result);
        return output;
    }

    /** 模板节点：渲染 template 配置。 */
    private Map<String, Object> executeTemplate(JsonNode node, GraphState state) {
        String template = node.path("config").path("template").asText("");
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("result", expressions.render(template, state));
        return output;
    }

    /** Nashorn 受限 JS 执行，包装为 IIFE 以支持顶层 return。 */
    private Object evalJs(String code, Map<String, Object> input) {
        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
            if (engine == null) {
                throw new IllegalStateException("JS 执行引擎不可用（缺少 nashorn-core 依赖）");
            }
            engine.getContext().setAttribute("input", input, ScriptContext.ENGINE_SCOPE);
            return engine.eval("(function(){" + code + "})()");
        } catch (Exception e) {
            throw new IllegalStateException("代码执行失败: " + e.getMessage(), e);
        }
    }

    /** 读取并行节点的 children 配置。 */
    private List<String> readChildren(JsonNode node) {
        List<String> children = new ArrayList<>();
        JsonNode array = node.path("config").path("children");
        if (array.isArray()) {
            for (JsonNode child : array) {
                String id = child.asText("");
                if (StringUtils.hasText(id)) {
                    children.add(id);
                }
            }
        }
        return children;
    }

    /** 读取主管节点执行策略：parallel（缺省）/ sequential。 */
    private String readStrategy(JsonNode node) {
        return node.path("config").path("strategy").asText(NodeFanout.STRATEGY_PARALLEL);
    }

    /** 可中断的退避等待，避免重试期间忙等。 */
    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("重试等待被中断", e);
        }
    }
}
