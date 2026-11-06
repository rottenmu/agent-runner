package com.zimo.module.ai.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.ai.mapper.WfWorkflowRunLogMapper;
import com.zimo.module.ai.mapper.WfWorkflowRunMapper;
import com.zimo.module.ai.workflow.WfWorkflow;
import com.zimo.module.ai.workflow.WfWorkflowRun;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 人工审批（human 节点 suspend / resume）单测。
 *
 * <p>覆盖六条关键语义：批准后继续执行、令牌校验、挂起态不得走普通续跑、
 * 终态不得二次审批、驳回无出口判失败、驳回有出口走驳回分支。</p>
 *
 * <p>为什么这些断言必须成对出现：只验证「批准能过」无法证明审批有效——
 * 一个连令牌都不校验、把挂起当普通状态推进的实现同样能通过。因此每条正向断言
 * 都配了对应的反向断言（错误令牌被拒、挂起态 resume 被拒、重复审批被拒）。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
class GraphHumanApprovalTest {

    /** 含 true/false 双向分支的人工节点图。 */
    private static final String BRANCH_GRAPH = """
            {"nodes":[
              {"id":"s","type":"start"},
              {"id":"h","type":"human","name":"人工审批","config":{"prompt":"请审批 {{input.orderId}}"}},
              {"id":"t","type":"task","config":{"taskType":"echo","prompt":"通过"}},
              {"id":"r","type":"task","config":{"taskType":"echo","prompt":"驳回后续"}},
              {"id":"e","type":"end"}
            ],"edges":[
              {"source":"s","target":"h"},
              {"source":"h","target":"t","branch":"true"},
              {"source":"h","target":"r","branch":"false"},
              {"source":"t","target":"e"},
              {"source":"r","target":"e"}
            ]}""";

    /** 只有成功出边的人工节点图（驳回时无路可走）。 */
    private static final String LINEAR_GRAPH = """
            {"nodes":[
              {"id":"s","type":"start"},
              {"id":"h","type":"human","name":"人工审批","config":{"prompt":"请审批"}},
              {"id":"t","type":"task","config":{"taskType":"echo","prompt":"通过"}},
              {"id":"e","type":"end"}
            ],"edges":[
              {"source":"s","target":"h"},
              {"source":"h","target":"t"},
              {"source":"t","target":"e"}
            ]}""";

    private final ObjectMapper mapper = new ObjectMapper();
    private WfWorkflowRunMapper runMapper;
    private GraphEngine engine;

    @BeforeEach
    void setUp() {
        runMapper = mock(WfWorkflowRunMapper.class);
        WfWorkflowRunLogMapper logMapper = mock(WfWorkflowRunLogMapper.class);
        GraphNodeExecutor executor = new GraphNodeExecutor(null, mapper);
        engine = new GraphEngine(runMapper, logMapper, executor, mapper, Runnable::run);
    }

    @Test
    void approveShouldContinueExecutionAndRecordDecision() throws Exception {
        WfWorkflowRun run = givenSuspendedRun(BRANCH_GRAPH);

        engine.approve(7L, GraphApproval.of(true, "同意，金额合规", "human", "u100", "token-1"));

        assertEquals(GraphEngine.STATUS_SUCCESS, run.getStatus());
        JsonNode nodeOutputs = mapper.readTree(run.getOutputJson()).path("nodeOutputs");
        assertTrue(nodeOutputs.path("h").path("approved").asBoolean(),
                "人工节点输出必须落审批决策，否则下游读取不到裁决结果");
        assertEquals("同意，金额合规", nodeOutputs.path("h").path("comment").asText());
        assertEquals("通过", nodeOutputs.path("t").path("result").asText(),
                "批准后必须真的执行下游节点，而不是只把状态改成 success");
        assertFalse(nodeOutputs.has("r"), "批准不得顺带执行驳回分支");
    }

    @Test
    void approveWithWrongTokenShouldBeRejected() {
        WfWorkflowRun run = givenSuspendedRun(BRANCH_GRAPH);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> engine.approve(7L, GraphApproval.of(true, "", "human", "u100", "token-2")));

        assertTrue(error.getMessage().contains("令牌"), "错误令牌必须被拒: " + error.getMessage());
        assertEquals(GraphEngine.STATUS_SUSPENDED, run.getStatus(), "拒绝后运行必须仍停在挂起态");
    }

    @Test
    void resumeShouldRejectSuspendedRun() {
        WfWorkflowRun run = givenSuspendedRun(BRANCH_GRAPH);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> engine.resume(7L));

        assertTrue(error.getMessage().contains("审批接口"), "挂起运行必须引导到审批接口: " + error.getMessage());
        assertEquals(GraphEngine.STATUS_SUSPENDED, run.getStatus(),
                "普通续跑不得消耗挂起现场，否则令牌会被悄悄换掉");
    }

    @Test
    void approveTwiceShouldBeRejected() {
        WfWorkflowRun run = givenSuspendedRun(BRANCH_GRAPH);
        engine.approve(7L, GraphApproval.of(true, "ok", "human", "u100", "token-1"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> engine.approve(7L, GraphApproval.of(true, "ok", "human", "u100", "token-1")));

        assertTrue(error.getMessage().contains("未处于待审批状态"), "重复审批必须被拒: " + error.getMessage());
        assertEquals(GraphEngine.STATUS_SUCCESS, run.getStatus());
    }

    @Test
    void rejectWithoutRejectTargetShouldFailRun() {
        WfWorkflowRun run = givenSuspendedRun(LINEAR_GRAPH);

        engine.approve(7L, GraphApproval.of(false, "材料不全", "human", "u100", "token-1"));

        assertEquals(GraphEngine.STATUS_FAILED, run.getStatus(),
                "驳回且没有驳回出口时必须判失败，绝不能顺着成功路径继续");
        assertNotNull(run.getErrorMessage());
        assertTrue(run.getErrorMessage().contains("驳回"), "失败原因需可追溯: " + run.getErrorMessage());
    }

    @Test
    void rejectWithBranchShouldFollowRejectPath() throws Exception {
        WfWorkflowRun run = givenSuspendedRun(BRANCH_GRAPH);

        engine.approve(7L, GraphApproval.of(false, "材料不全", "human", "u100", "token-1"));

        assertEquals(GraphEngine.STATUS_SUCCESS, run.getStatus());
        JsonNode nodeOutputs = mapper.readTree(run.getOutputJson()).path("nodeOutputs");
        assertFalse(nodeOutputs.path("h").path("approved").asBoolean());
        assertEquals("驳回后续", nodeOutputs.path("r").path("result").asText(),
                "驳回后必须走驳回分支");
        assertFalse(nodeOutputs.has("t"), "驳回不得执行成功分支");
    }

    @Test
    void startShouldStopAtHumanNodeWithoutBeingFinished() {
        WfWorkflowRun run = startLinearGraph(LINEAR_GRAPH);

        assertEquals(GraphEngine.STATUS_SUSPENDED, run.getStatus(),
                "推进循环结束后不得把挂起覆盖成 success，否则审批现场就丢了");
        assertEquals("h", run.getCurrentNodeId());
        assertNotNull(run.getContextJson());
        assertTrue(run.getContextJson().contains("resumeToken"), "挂起现场必须落 checkpoint");
        assertTrue(run.getFinishedAt() == null, "挂起不是终态，不应写完成时间");
    }

    @Test
    void failedRunShouldKeepFailedStatus() {
        String failingGraph = """
                {"nodes":[
                  {"id":"s","type":"start"},
                  {"id":"f","type":"task","config":{"taskType":"fail"}}
                ],"edges":[{"source":"s","target":"f"}]}""";
        WfWorkflowRun run = startLinearGraph(failingGraph);

        assertEquals(GraphEngine.STATUS_FAILED, run.getStatus(),
                "失败也不得被收尾覆盖成 success：那会让失败静默变成成功");
        assertNotNull(run.getErrorMessage());
    }

    /** 用 start 走一遍线性图，返回落库后的运行实例。 */
    private WfWorkflowRun startLinearGraph(String definitionJson) {
        WfWorkflow workflow = new WfWorkflow();
        workflow.setId(1L);
        workflow.setVersionNo(1);
        workflow.setDefinitionJson(definitionJson);
        when(runMapper.insert(org.mockito.ArgumentMatchers.any(WfWorkflowRun.class))).thenAnswer(inv -> {
            WfWorkflowRun inserted = inv.getArgument(0);
            inserted.setId(9L);
            return 1;
        });
        return engine.start(workflow, Map.of(), false);
    }

    /** 构造一个停在 h 节点、令牌为 token-1 的挂起运行。 */
    private WfWorkflowRun givenSuspendedRun(String definitionJson) {        GraphState state = new GraphState(Map.of("orderId", "A100"));
        state.runId(7L);
        state.resumeToken("token-1");
        state.pending("h", Map.of("nodeId", "h", "name", "人工审批", "prompt", "请审批 A100"));
        WfWorkflowRun run = new WfWorkflowRun();
        run.setId(7L);
        run.setWorkflowId(1L);
        run.setDefinitionJson(definitionJson);
        run.setStatus(GraphEngine.STATUS_SUSPENDED);
        run.setCurrentNodeId("h");
        run.setContextJson(state.toJson(mapper));
        when(runMapper.selectById(7L)).thenReturn(run);
        return run;
    }
}
