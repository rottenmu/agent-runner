package com.zimo.module.ai.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.util.StringUtils;

/**
 * Agent 运行时图执行状态（可序列化 checkpoint）。
 *
 * <p>所属模块：agent-harness 运行时内核。承载一次图运行的全部中间态，
 * 并在每个节点执行完成后序列化到 {@code wf_workflow_run.context_json}，
 * 从而支持进程崩溃后的断点续跑与回放。</p>
 *
 * <p>兼容约束：JSON 字段名沿用现有 {@code WfWorkflowEngine} 的上下文格式
 * （{@code input/nodeOutputs/executedNodes/lastError}），保证新旧引擎可读取同一份
 * checkpoint，迁移期不丢历史运行数据。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class GraphState {

    /** 所属运行实例 ID：供工具调用等旁路审计关联到具体一次运行。 */
    private volatile Long runId;

    /** 图输入参数（请求级，运行期只读）。 */
    private final Map<String, Object> input;

    /** 各节点输出，键为节点 ID；并行执行时多线程写入，故使用并发容器。 */
    private final Map<String, Object> nodeOutputs = new ConcurrentHashMap<>();

    /** 已执行完成的节点 ID（按完成顺序追加，并行场景允许乱序）。 */
    private final List<String> completedNodes = Collections.synchronizedList(new ArrayList<>());

    /** 回边进入次数，键为回边目标节点 ID，用于 maxIterations 保护。 */
    private final Map<String, Integer> loopCounters = new ConcurrentHashMap<>();

    /**
     * 发生冲突的编排节点 ID（主管节点存在非成功回执）。
     *
     * <p>非空表示本次运行是"部分失败"：流程走完了，但有人没交差，
     * 需要人工裁决 —— 与 {@code MultiAgentCollaborationService} 的 conflict 语义一致。</p>
     */
    private final List<String> conflictedNodes = Collections.synchronizedList(new ArrayList<>());

    /** 最近一次节点异常信息，供 fallback 节点读取。 */
    private volatile String lastError;

    /** 当前循环迭代序号（节点内循环使用），-1 表示不在循环中。 */
    private volatile int loopIndex = -1;

    /** 人工节点恢复令牌：suspended 状态下由外部携带该令牌恢复执行。 */
    private volatile String resumeToken;

    /** 待人工处理的节点 ID；非挂起态为 null。 */
    private volatile String pendingNodeId;

    /** 待人工处理节点的元数据（提示语、表单定义等），由 human 节点自身提供。 */
    private volatile Map<String, Object> pendingMeta;

    /**
     * 构造执行状态。
     *
     * @param input 图输入参数，可为 null（按空 Map 处理）
     */
    public GraphState(Map<String, Object> input) {
        this.input = input == null ? new LinkedHashMap<>() : new LinkedHashMap<>(input);
    }

    /** 返回只读输入参数。 */
    public Map<String, Object> input() {
        return input;
    }

    /** 运行实例 ID。 */
    public Long runId() {
        return runId;
    }

    /** 设置运行实例 ID（引擎启动/恢复时写入）。 */
    public void runId(Long runId) {
        this.runId = runId;
    }

    /** 写入节点输出。 */
    public void putOutput(String nodeId, Object output) {
        if (nodeId != null) {
            nodeOutputs.put(nodeId, output);
        }
    }

    /** 读取节点输出，不存在返回 null。 */
    public Object output(String nodeId) {
        return nodeOutputs.get(nodeId);
    }

    /** 全部节点输出（只读视图）。 */
    public Map<String, Object> outputs() {
        return Collections.unmodifiableMap(nodeOutputs);
    }

    /** 标记节点已完成。 */
    public void markCompleted(String nodeId) {
        if (nodeId != null && !completedNodes.contains(nodeId)) {
            completedNodes.add(nodeId);
        }
    }

    /** 判断节点是否已在本次运行中完成。 */
    public boolean isCompleted(String nodeId) {
        return completedNodes.contains(nodeId);
    }

    /** 已完成节点列表快照。 */
    public List<String> completedNodes() {
        synchronized (completedNodes) {
            return new ArrayList<>(completedNodes);
        }
    }

    /** 回边计数加一并返回新值。 */
    public int incrementLoop(String nodeId) {
        return loopCounters.merge(nodeId, 1, Integer::sum);
    }

    /**
     * 标记某编排节点发生冲突（主管节点存在失败/拒绝/冲突回执）。
     *
     * @param nodeId 节点 ID
     */
    public void markConflict(String nodeId) {
        if (nodeId != null && !conflictedNodes.contains(nodeId)) {
            conflictedNodes.add(nodeId);
        }
    }

    /** 本次运行是否发生过冲突（决定终态是 conflict 还是 success）。 */
    public boolean hasConflicts() {
        return !conflictedNodes.isEmpty();
    }

    /** 冲突节点列表快照。 */
    public List<String> conflictedNodes() {
        synchronized (conflictedNodes) {
            return new ArrayList<>(conflictedNodes);
        }
    }

    /** 读取回边计数。 */
    public int loopCount(String nodeId) {
        return loopCounters.getOrDefault(nodeId, 0);
    }

    /** 最近一次错误信息。 */
    public String lastError() {
        return lastError;
    }

    /** 记录最近一次错误信息。 */
    public void lastError(String message) {
        this.lastError = message;
    }

    /** 当前循环迭代序号。 */
    public int loopIndex() {
        return loopIndex;
    }

    /** 设置当前循环迭代序号。 */
    public void loopIndex(int index) {
        this.loopIndex = index;
    }

    /** 恢复令牌。 */
    public String resumeToken() {
        return resumeToken;
    }

    /** 设置恢复令牌。 */
    public void resumeToken(String token) {
        this.resumeToken = token;
    }

    /** 待人工处理的节点 ID。 */
    public String pendingNodeId() {
        return pendingNodeId;
    }

    /** 待人工处理节点的元数据（只读视图）。 */
    public Map<String, Object> pendingMeta() {
        return pendingMeta == null ? Map.of() : pendingMeta;
    }

    /** 记录挂起现场：待处理节点与展示元数据。 */
    public void pending(String nodeId, Map<String, Object> meta) {
        this.pendingNodeId = nodeId;
        this.pendingMeta = meta == null ? Map.of() : new LinkedHashMap<>(meta);
    }

    /**
     * 清除挂起现场（审批完成后调用）。
     *
     * <p>令牌与挂起信息必须一并清空：只清其中一个会让「挂起态」出现两种表示，
     * 下一次审批就无法靠状态判断是否可恢复。</p>
     */
    public void clearPending() {
        this.pendingNodeId = null;
        this.pendingMeta = null;
        this.resumeToken = null;
    }

    /**
     * 序列化为 checkpoint JSON（写入 context_json）。
     *
     * @param objectMapper JSON 映射器
     * @return JSON 字符串；序列化失败时抛出 IllegalStateException
     */
    public String toJson(ObjectMapper objectMapper) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("runId", runId);
        snapshot.put("input", input);
        snapshot.put("nodeOutputs", nodeOutputs);
        snapshot.put("executedNodes", completedNodes());
        snapshot.put("loopCounters", loopCounters);
        snapshot.put("conflictedNodes", conflictedNodes());
        snapshot.put("lastError", lastError);
        snapshot.put("loopIndex", loopIndex);
        snapshot.put("resumeToken", resumeToken);
        snapshot.put("pendingNodeId", pendingNodeId);
        snapshot.put("pendingMeta", pendingMeta);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("执行状态序列化失败", e);
        }
    }

    /**
     * 从 checkpoint JSON 恢复状态。
     *
     * <p>对历史 checkpoint 做兼容：缺失字段按空值处理，不因未知字段报错。</p>
     *
     * @param objectMapper JSON 映射器
     * @param json checkpoint JSON，可为空（返回空状态）
     * @return 恢复后的状态
     */
    @SuppressWarnings("unchecked")
    public static GraphState fromJson(ObjectMapper objectMapper, String json) {
        GraphState state = new GraphState(null);
        if (!StringUtils.hasText(json)) {
            return state;
        }
        Map<String, Object> restored;
        try {
            restored = objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return state;
        }
        Object inputObj = restored.get("input");
        if (restored.get("runId") instanceof Number runIdValue) {
            state.runId = runIdValue.longValue();
        }
        if (inputObj instanceof Map<?, ?> map) {
            state.input.clear();
            state.input.putAll((Map<String, Object>) map);
        }
        Object outputs = restored.get("nodeOutputs");
        if (outputs instanceof Map<?, ?> map) {
            state.nodeOutputs.putAll((Map<String, Object>) map);
        }
        Object executed = restored.get("executedNodes");
        if (executed instanceof List<?> list) {
            for (Object item : list) {
                state.completedNodes.add(String.valueOf(item));
            }
        }
        Object counters = restored.get("loopCounters");
        if (counters instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (value instanceof Number number) {
                    state.loopCounters.put(String.valueOf(key), number.intValue());
                }
            });
        }
        Object conflicts = restored.get("conflictedNodes");
        if (conflicts instanceof List<?> list) {
            for (Object item : list) {
                state.markConflict(String.valueOf(item));
            }
        }
        state.lastError = restored.get("lastError") == null ? null : String.valueOf(restored.get("lastError"));
        state.loopIndex = restored.get("loopIndex") instanceof Number n ? n.intValue() : -1;
        state.resumeToken = restored.get("resumeToken") == null ? null : String.valueOf(restored.get("resumeToken"));
        state.pendingNodeId = restored.get("pendingNodeId") == null
                ? null : String.valueOf(restored.get("pendingNodeId"));
        Object pendingMeta = restored.get("pendingMeta");
        if (pendingMeta instanceof Map<?, ?> map) {
            state.pendingMeta = (Map<String, Object>) map;
        }
        return state;
    }
}
