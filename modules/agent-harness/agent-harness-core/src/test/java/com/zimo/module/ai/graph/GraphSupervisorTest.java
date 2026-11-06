package com.zimo.module.ai.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.ai.mapper.WfWorkflowRunLogMapper;
import com.zimo.module.ai.mapper.WfWorkflowRunMapper;
import com.zimo.module.ai.workflow.WfWorkflow;
import com.zimo.module.ai.workflow.WfWorkflowRun;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 主管/worker 图式多智能体单测（M4）。
 *
 * <p>核心语义：worker 失败只影响自己那条回执，其余 worker 结果<b>保留</b>，
 * 图终态记 {@code conflict}（部分失败）而不是 {@code failed}。
 * 这与现有 {@code MultiAgentCollaborationService} 的"子任务失败 → 父任务 conflict 等人工处理"一致。</p>
 *
 * <p>每条用例都配反向断言：只验证"冲突时能拿到 conflict"不够，
 * 还必须证明"失败 worker 的兄弟结果仍在""补偿分支才算走对"，
 * 否则一个"冲突就丢弃全部结果"的实现同样能通过。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
class GraphSupervisorTest {

    /** 运行实例 ID（mock 的 insert 回填值）。 */
    private static final long RUN_ID = 11L;

    private final ObjectMapper mapper = new ObjectMapper();
    private WfWorkflowRunMapper runMapper;
    private GraphEngine engine;

    @BeforeEach
    void setUp() {
        runMapper = mock(WfWorkflowRunMapper.class);
        when(runMapper.insert(any(WfWorkflowRun.class))).thenAnswer(inv -> {
            WfWorkflowRun inserted = inv.getArgument(0);
            inserted.setId(RUN_ID);
            return 1;
        });
        WfWorkflowRunLogMapper logMapper = mock(WfWorkflowRunLogMapper.class);
        engine = new GraphEngine(runMapper, logMapper, new GraphNodeExecutor(null, mapper),
                mapper, Runnable::run);
    }

    @Test
    void allWorkersSucceedShouldFinishSuccess() throws Exception {
        WfWorkflowRun run = runGraph(graph(List.of("wA", "wB"), null));

        assertEquals(GraphEngine.STATUS_SUCCESS, run.getStatus());
        JsonNode outputs = nodeOutputs(run);
        assertTrue(outputs.has("wA"), "worker 输出必须落库，供复核与下游引用");
        assertTrue(outputs.has("wB"));
        JsonNode summary = outputs.path("sup");
        assertEquals("success", summary.path("status").asText());
        assertEquals(2, summary.path("total").asInt());
        assertEquals(2, summary.path("succeeded").asInt());
        assertEquals(2, summary.path("receipts").size(), "主管必须收到两路回执");
        assertEquals(0, summary.path("conflicts").size());
    }

    @Test
    void oneWorkerFailedShouldBecomeConflictAndKeepSiblingResult() throws Exception {
        WfWorkflowRun run = runGraph(graph(List.of("wA", "wB"), null, "wB:FAIL"));

        assertEquals(GraphEngine.STATUS_CONFLICT, run.getStatus(),
                "存在失败 worker 时终态必须是 conflict，而不是 success 或 failed");
        JsonNode outputs = nodeOutputs(run);
        assertEquals("A", outputs.path("wA").path("result").asText(),
                "失败 worker 的兄弟结果必须保留，否则人工无从比对");
        JsonNode summary = outputs.path("sup");
        assertEquals("conflict", summary.path("status").asText());
        assertEquals(1, summary.path("succeeded").asInt());
        assertEquals(1, summary.path("conflicts").size());
        assertEquals("wB", summary.path("conflicts").path(0).path("nodeId").asText());
        assertEquals("failed", summary.path("conflicts").path(0).path("status").asText());
    }

    @Test
    void workerSelfReportedConflictShouldPropagate() throws Exception {
        WfWorkflowRun run = runGraph(graph(List.of("wA", "wB"), null, "wB:CONFLICT"));

        assertEquals(GraphEngine.STATUS_CONFLICT, run.getStatus());
        JsonNode summary = nodeOutputs(run).path("sup");
        assertEquals("conflict", summary.path("conflicts").path(0).path("status").asText());
        assertEquals(1, summary.path("succeeded").asInt(), "自报冲突不影响另一 worker 的成功计数");
    }

    @Test
    void conflictTargetShouldRouteCompensationBranch() throws Exception {
        WfWorkflowRun run = runGraph(graph(List.of("wA", "wB"), "fix", "wB:CONFLICT"));

        assertEquals(GraphEngine.STATUS_CONFLICT, run.getStatus(),
                "即使走了补偿分支，终态仍应是 conflict：补偿不等于问题消失");
        assertEquals("FIX", nodeOutputs(run).path("fix").path("result").asText(),
                "配置了 conflictTarget 时必须进入补偿分支");
    }

    @Test
    void controlNodeAsWorkerShouldYieldFailedReceiptNotCrash() throws Exception {
        WfWorkflowRun run = runGraph(graph(List.of("wA", "p"), null));

        assertEquals(GraphEngine.STATUS_CONFLICT, run.getStatus(),
                "非法 worker 不应让整图炸掉，而应形成冲突回执");
        JsonNode conflicts = nodeOutputs(run).path("sup").path("conflicts");
        assertEquals(1, conflicts.size(), "只有非法 worker 进冲突列表: " + conflicts);
        assertEquals("p", conflicts.path(0).path("nodeId").asText());
        assertTrue(conflicts.toString().contains("编排类节点"),
                "必须给出可读原因，而不是一个空失败: " + conflicts);
    }

    @Test
    void sequentialWorkersShouldSeePreviousOutput() {
        String definition = """
                {"nodes":[
                  {"id":"s","type":"start"},
                  {"id":"sup","type":"supervisor","config":{"children":["w1","w2"],"strategy":"sequential"}},
                  {"id":"w1","type":"task","config":{"prompt":"A"}},
                  {"id":"w2","type":"task","config":{"prompt":"{{ctx.nodeOutputs.w1.result}}-B"}},
                  {"id":"e","type":"end"}
                ],"edges":[{"source":"s","target":"sup"},{"source":"sup","target":"e"}]}""";

        WfWorkflowRun run = runGraph(definition);

        assertEquals(GraphEngine.STATUS_SUCCESS, run.getStatus());
        JsonNode outputs = readOutputs(run).path("nodeOutputs");
        assertEquals("A-B", outputs.path("w2").path("result").asText(),
                "串行策略下后一个 worker 必须能读到前一个的输出");
    }

    @Test
    void conflictedRunIsTerminalAndCannotBeResumed() {
        WfWorkflowRun run = runGraph(graph(List.of("wA", "wB"), null, "wB:CONFLICT"));
        when(runMapper.selectById(RUN_ID)).thenReturn(run);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> engine.resume(RUN_ID));

        assertTrue(error.getMessage().contains("已结束"), "conflict 是终态，不允许续跑: " + error.getMessage());
        assertEquals(GraphEngine.STATUS_CONFLICT, run.getStatus());
    }

    /* ---------------- 辅助 ---------------- */

    private JsonNode nodeOutputs(WfWorkflowRun run) throws Exception {
        return readOutputs(run).path("nodeOutputs");
    }

    private JsonNode readOutputs(WfWorkflowRun run) {
        assertNotNull(run.getOutputJson(), "运行结束必须写输出快照");
        try {
            return mapper.readTree(run.getOutputJson());
        } catch (Exception e) {
            throw new IllegalStateException("输出快照不是合法 JSON", e);
        }
    }

    /** 构造主管图（默认两个 echo worker）。 */
    private String graph(List<String> workers, String conflictTarget) {
        return graph(workers, conflictTarget, null);
    }

    /**
     * 构造主管图。
     *
     * @param workers worker 节点 ID 列表；{@code "p"} 会被构造成 parallel 节点（非法 worker）
     * @param conflictTarget 冲突补偿出口，可为 null
     * @param override 形如 {@code "wB:FAIL"} / {@code "wB:CONFLICT"}，可为 null
     */
    private String graph(List<String> workers, String conflictTarget, String override) {
        String children = workers.stream().map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(",", "[", "]"));
        StringBuilder nodes = new StringBuilder();
        nodes.append("{\"id\":\"s\",\"type\":\"start\"},");
        nodes.append("{\"id\":\"sup\",\"type\":\"supervisor\",\"name\":\"主管\",\"config\":{\"children\":")
                .append(children);
        if (conflictTarget != null) {
            nodes.append(",\"conflictTarget\":\"").append(conflictTarget).append("\"");
        }
        nodes.append("}},");
        for (String worker : workers) {
            nodes.append(workerNode(worker, override)).append(',');
        }
        nodes.append("{\"id\":\"fix\",\"type\":\"task\",\"config\":{\"prompt\":\"FIX\"}},");
        nodes.append("{\"id\":\"e\",\"type\":\"end\"}");

        return "{\"nodes\":[" + nodes + "],"
                + "\"edges\":[{\"source\":\"s\",\"target\":\"sup\"},"
                + "{\"source\":\"sup\",\"target\":\"e\"},"
                + "{\"source\":\"fix\",\"target\":\"e\"}]}";
    }

    /** 单个 worker 节点定义：p 为并行节点（非法），其余为 task。 */
    private String workerNode(String worker, String override) {
        if ("p".equals(worker)) {
            return "{\"id\":\"p\",\"type\":\"parallel\",\"config\":{\"children\":[]}}";
        }
        String taskType = "echo";
        if (override != null && override.startsWith(worker + ":")) {
            String kind = override.substring(worker.length() + 1);
            taskType = "CONFLICT".equals(kind) ? "conflict" : "fail";
        }
        return "{\"id\":\"" + worker + "\",\"type\":\"task\",\"config\":{\"taskType\":\""
                + taskType + "\",\"prompt\":\"" + ("wA".equals(worker) ? "A" : "B") + "\"}}";
    }

    private WfWorkflowRun runGraph(String definitionJson) {
        WfWorkflow workflow = new WfWorkflow();
        workflow.setId(1L);
        workflow.setVersionNo(1);
        workflow.setDefinitionJson(definitionJson);
        return engine.start(workflow, Map.of(), false);
    }
}
