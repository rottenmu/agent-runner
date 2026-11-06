package com.zimo.module.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * Agent 运行时图定义解析器。
 *
 * <p>所属模块：agent-harness 运行时内核。将工作流定义 JSON（nodes + edges）解析为
 * 可执行的图结构，供 {@link GraphEngine} 做分支、回边循环与并行调度。</p>
 *
 * <p>兼容约束：沿用现有 {@code wf_workflow.definition_json} 格式，
 * 节点字段为 {@code id/type/name/config}，边字段为 {@code source/target/branch}，
 * 确保存量工作流无需改写即可被新引擎执行。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class GraphDefinition {

    /** 默认回边循环上限，防止图配置缺失 maxIterations 时无限循环。 */
    private static final int DEFAULT_MAX_ITERATIONS = 10;

    private final JsonNode root;
    private final Map<String, JsonNode> nodeIndex;
    private final List<JsonNode> edges;

    private GraphDefinition(JsonNode root) {
        this.root = root;
        this.nodeIndex = new LinkedHashMap<>();
        this.edges = new ArrayList<>();
        JsonNode nodes = root.path("nodes");
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                String id = node.path("id").asText("");
                if (StringUtils.hasText(id)) {
                    nodeIndex.put(id, node);
                }
            }
        }
        JsonNode edgeNodes = root.path("edges");
        if (edgeNodes.isArray()) {
            edgeNodes.forEach(edges::add);
        }
    }

    /**
     * 解析图定义 JSON。
     *
     * @param objectMapper JSON 映射器，不可为空
     * @param definitionJson 图定义 JSON，可为空（空视为空图）
     * @return 解析后的图定义
     * @throws IllegalArgumentException JSON 非法时抛出
     */
    public static GraphDefinition parse(ObjectMapper objectMapper, String definitionJson) {
        if (!StringUtils.hasText(definitionJson)) {
            return new GraphDefinition(objectMapper.createObjectNode());
        }
        try {
            return new GraphDefinition(objectMapper.readTree(definitionJson));
        } catch (Exception e) {
            throw new IllegalArgumentException("流程定义 JSON 解析失败", e);
        }
    }

    /** 返回指定节点，不存在返回 null。 */
    public JsonNode node(String nodeId) {
        return nodeId == null ? null : nodeIndex.get(nodeId);
    }

    /** 返回所有节点 ID（保持定义顺序）。 */
    public List<String> nodeIds() {
        return new ArrayList<>(nodeIndex.keySet());
    }

    /** 节点类型，缺省按 task 处理。 */
    public String type(String nodeId) {
        JsonNode node = node(nodeId);
        return node == null ? "" : node.path("type").asText("task");
    }

    /** 节点展示名称，缺省回退节点 ID。 */
    public String name(String nodeId) {
        JsonNode node = node(nodeId);
        return node == null ? nodeId : node.path("name").asText(nodeId);
    }

    /** 查找 start 节点 ID，缺失时抛出异常。 */
    public String startNodeId() {
        for (Map.Entry<String, JsonNode> entry : nodeIndex.entrySet()) {
            if ("start".equals(entry.getValue().path("type").asText())) {
                return entry.getKey();
            }
        }
        throw new IllegalArgumentException("流程定义缺少 start 节点");
    }

    /**
     * 普通出边目标：取第一条无 branch 的出边。
     *
     * @return 下一节点 ID；无出边返回 null（表示流程结束）
     */
    public String nextTarget(String sourceId) {
        for (JsonNode edge : edges) {
            if (sourceId.equals(edge.path("source").asText())
                    && !edge.hasNonNull("branch")) {
                return edge.path("target").asText();
            }
        }
        return null;
    }

    /**
     * 条件分支目标：优先匹配 branch 值与布尔结果一致的边，未命中时回退普通出边。
     *
     * @param result 条件表达式结果
     * @return 下一节点 ID；无匹配返回 null
     */
    public String branchTarget(String sourceId, boolean result) {
        String want = String.valueOf(result);
        for (JsonNode edge : edges) {
            if (sourceId.equals(edge.path("source").asText())
                    && want.equals(edge.path("branch").asText())) {
                return edge.path("target").asText();
            }
        }
        return nextTarget(sourceId);
    }

    /**
     * 条件分支目标（严格匹配）：只认 {@code branch} 值与布尔结果一致的边。
     *
     * <p>与 {@link #branchTarget} 的区别是**不回退普通出边**。人工审批需要这个区别：
     * 驳回时若没有专门的驳回分支，必须判失败，而不能顺着正常成功路径继续往下走——
     * 那种「驳回后照常执行」的错不是报错，而是静默地做错事。</p>
     *
     * @param sourceId 源节点 ID
     * @param result 条件结果
     * @return 匹配的分支目标；无匹配返回 null
     */
    public String branchTargetExact(String sourceId, boolean result) {
        String want = String.valueOf(result);
        for (JsonNode edge : edges) {
            if (sourceId.equals(edge.path("source").asText())
                    && want.equals(edge.path("branch").asText())) {
                return edge.path("target").asText();
            }
        }
        return null;
    }

    /**
     * 人工审批节点的决策出口。
     *
     * <p>解析优先级（逐级收窄，都不命中才算配置缺失）：</p>
     * <ol>
     *   <li>节点配置显式 {@code config.approveTarget} / {@code config.rejectTarget}；</li>
     *   <li>分支边 {@code branch=true} / {@code branch=false}；</li>
     *   <li>批准时回退普通出边；驳回时返回 null（由引擎判失败）。</li>
     * </ol>
     *
     * @param nodeId 人工节点 ID
     * @param approved 是否批准
     * @return 出口节点 ID；驳回且未配置驳回出口时返回 null
     */
    public String approvalTarget(String nodeId, boolean approved) {
        JsonNode node = node(nodeId);
        String configured = node == null ? ""
                : node.path("config").path(approved ? "approveTarget" : "rejectTarget").asText("");
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        String branch = branchTargetExact(nodeId, approved);
        if (branch != null) {
            return branch;
        }
        return approved ? nextTarget(nodeId) : null;
    }

    /**
     * 并行节点子节点列表：读取 {@code config.children} 数组。
     *
     * <p>配置示例：{@code "config": {"children": ["a", "b"]}}。</p>
     *
     * @return 子节点 ID 列表，未配置时返回空列表
     */
    public List<String> parallelChildren(String nodeId) {
        List<String> children = new ArrayList<>();
        JsonNode node = node(nodeId);
        if (node == null) {
            return children;
        }
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

    /**
     * 节点级回边循环上限：读取 {@code config.maxIterations}。
     *
     * <p>语义为节点**总执行次数**上限：首次执行计 1 次，回边重复进入继续累加，
     * 超过该值即退出循环。</p>
     *
     * @return 上限值；未配置或非正数时返回默认 10
     */
    public int maxIterations(String nodeId) {
        JsonNode node = node(nodeId);
        if (node == null) {
            return DEFAULT_MAX_ITERATIONS;
        }
        int configured = node.path("config").path("maxIterations").asInt(DEFAULT_MAX_ITERATIONS);
        return configured > 0 ? configured : DEFAULT_MAX_ITERATIONS;
    }

    /**
     * 回边超限后的出口节点：读取 {@code config.exitTarget}。
     *
     * <p>未配置时返回 null，表示直接结束流程；配置后跳转到循环外节点继续执行。
     * 该出口是避免双向回边在超限后退回对方、形成无限循环的必要设计。</p>
     *
     * @return 出口节点 ID；未配置返回 null
     */
    public String exitTarget(String nodeId) {
        JsonNode node = node(nodeId);
        if (node == null) {
            return null;
        }
        String target = node.path("config").path("exitTarget").asText("");
        return StringUtils.hasText(target) ? target : null;
    }

    /**
     * 主管节点发生冲突时的出口：读取 {@code config.conflictTarget}。
     *
     * <p>缺省返回 null，表示"带伤继续"：沿普通出边往下走，图终态记为 conflict。
     * 只有需要走补偿/人工处理支路的图才配置它。</p>
     *
     * @param nodeId 主管节点 ID
     * @return 冲突出口节点 ID；未配置返回 null
     */
    public String conflictTarget(String nodeId) {
        JsonNode node = node(nodeId);
        if (node == null) {
            return null;
        }
        String target = node.path("config").path("conflictTarget").asText("");
        return StringUtils.hasText(target) ? target : null;
    }

    /**
     * 判断是否存在从 from 到 to 的边（用于识别图回边）。
     *
     * @return 存在出边返回 true
     */
    public boolean hasEdge(String from, String to) {
        for (JsonNode edge : edges) {
            if (from.equals(edge.path("source").asText())
                    && to.equals(edge.path("target").asText())) {
                return true;
            }
        }
        return false;
    }

    /** 判断节点是否为回边节点（其出边指回自身或已完成的祖先节点由引擎在运行时判定）。 */
    public int edgeCount(String nodeId) {
        int count = 0;
        for (JsonNode edge : edges) {
            if (nodeId.equals(edge.path("source").asText())) {
                count++;
            }
        }
        return count;
    }
}
