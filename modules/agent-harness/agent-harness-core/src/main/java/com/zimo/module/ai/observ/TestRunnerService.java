package com.zimo.module.ai.observ;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zimo.framework.common.validation.ValidationUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.starter.ai.AiAgentReply;
import com.zimo.starter.ai.AiAgentService;
import com.zimo.module.rag.service.RagRetrieveService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 自动化测试服务：测试用例管理 + 批量测评。
 *
 * <p>测评类别：intent(意图准确率)、recall(召回精度)、hallucination(幻觉检测)、
 * pressure(异常压力测试)、boundary(边界场景测试)。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class TestRunnerService {

    private final ObservTestCaseMapper caseMapper;
    private final ObservTestRunMapper runMapper;
    private final ObservTestCaseResultMapper resultMapper;
    private final AiAgentService aiAgentService;
    private final RagRetrieveService retrieveService;
    private final ObjectMapper objectMapper;

    public TestRunnerService(ObservTestCaseMapper caseMapper,
                             ObservTestRunMapper runMapper,
                             ObservTestCaseResultMapper resultMapper,
                             AiAgentService aiAgentService,
                             RagRetrieveService retrieveService,
                             ObjectMapper objectMapper) {
        this.caseMapper = caseMapper;
        this.runMapper = runMapper;
        this.resultMapper = resultMapper;
        this.aiAgentService = aiAgentService;
        this.retrieveService = retrieveService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /* ---------------- 用例管理 ---------------- */

    public List<ObservTestCase> listCases(String category) {
        return caseMapper.selectList(Wrappers.<ObservTestCase>lambdaQuery()
                .eq(StringUtils.hasText(category), ObservTestCase::getCategory, category)
                .orderByDesc(ObservTestCase::getId));
    }

    public ObservTestCase createCase(String name, String category, String input, String expected,
                                     String agentId, List<String> tags, boolean enabled) {
        if (!StringUtils.hasText(name) || !StringUtils.hasText(input)) {
            throw new IllegalArgumentException("名称与输入不能为空");
        }
        ObservTestCase testCase = new ObservTestCase();
        testCase.setName(name.trim());
        testCase.setCategory(StringUtils.hasText(category) ? category : "intent");
        testCase.setInput(input);
        testCase.setExpected(expected);
        testCase.setAgentId(agentId);
        testCase.setTags(toJson(tags));
        testCase.setEnabled(enabled);
        testCase.setCreatedAt(LocalDateTime.now());
        caseMapper.insert(testCase);
        return testCase;
    }

    public ObservTestCase updateCase(Long id, Map<String, Object> body) {
        ObservTestCase testCase = caseMapper.selectById(id);
        ValidationUtil.requireNotNull(testCase, "用例不存在: ");
        if (StringUtils.hasText(str(body.get("name")))) {
            testCase.setName(str(body.get("name")));
        }
        if (body.get("category") != null) {
            testCase.setCategory(str(body.get("category")));
        }
        if (body.get("input") != null) {
            testCase.setInput(str(body.get("input")));
        }
        if (body.get("expected") != null) {
            testCase.setExpected(str(body.get("expected")));
        }
        if (body.get("agentId") != null) {
            testCase.setAgentId(str(body.get("agentId")));
        }
        if (body.get("enabled") != null) {
            testCase.setEnabled(Boolean.parseBoolean(String.valueOf(body.get("enabled"))));
        }
        caseMapper.updateById(testCase);
        return testCase;
    }

    public boolean deleteCase(Long id) {
        return caseMapper.deleteById(id) > 0;
    }

    /* ---------------- 批量测评 ---------------- */

    /**
     * 运行测评批次。
     *
     * @param name 批次名称
     * @param caseIds 指定用例（空则全部启用用例）
     * @return 运行报告
     */
    public Map<String, Object> runTests(String name, List<Long> caseIds) {
        List<ObservTestCase> cases = caseIds == null || caseIds.isEmpty()
                ? caseMapper.selectList(Wrappers.<ObservTestCase>lambdaQuery().eq(ObservTestCase::getEnabled, true))
                : caseMapper.selectList(Wrappers.<ObservTestCase>lambdaQuery().in(ObservTestCase::getId, caseIds));

        ObservTestRun run = new ObservTestRun();
        run.setName(StringUtils.hasText(name) ? name.trim() : "批量测评 " + LocalDateTime.now().toLocalDate());
        run.setCaseCount(cases.size());
        run.setPassedCount(0);
        run.setStatus("running");
        run.setStartedAt(LocalDateTime.now().toString());
        run.setCreatedAt(LocalDateTime.now());
        runMapper.insert(run);

        int passed = 0;
        Map<String, int[]> categoryStats = new LinkedHashMap<>();
        for (ObservTestCase testCase : cases) {
            long start = System.currentTimeMillis();
            ObservTestCaseResult result = new ObservTestCaseResult();
            result.setRunId(run.getId());
            result.setCaseId(testCase.getId());
            result.setCategory(testCase.getCategory());
            result.setInput(testCase.getInput());
            result.setExpected(testCase.getExpected());
            try {
                CaseOutcome outcome = executeCase(testCase);
                result.setActual(truncate(outcome.output, 2000));
                result.setPassed(outcome.passed);
                result.setScore(outcome.score);
                if (!outcome.passed) {
                    result.setError(outcome.error);
                }
                if (outcome.passed) {
                    passed++;
                }
            } catch (Exception e) {
                result.setPassed(false);
                result.setError(safeMessage(e));
            }
            result.setLatencyMs(System.currentTimeMillis() - start);
            result.setCreatedAt(LocalDateTime.now());
            resultMapper.insert(result);
            categoryStats.computeIfAbsent(testCase.getCategory(), k -> new int[]{0, 0})[0]++;
            if (Boolean.TRUE.equals(result.getPassed())) {
                categoryStats.computeIfAbsent(testCase.getCategory(), k -> new int[]{0, 0})[1]++;
            }
        }

        run.setPassedCount(passed);
        run.setStatus("done");
        run.setEndedAt(LocalDateTime.now().toString());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("total", cases.size());
        report.put("passed", passed);
        report.put("rate", cases.isEmpty() ? 0 : Math.round(passed * 10000.0 / cases.size()) / 100.0);
        Map<String, Object> category = new LinkedHashMap<>();
        categoryStats.forEach((key, counts) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("total", counts[0]);
            item.put("passed", counts[1]);
            item.put("rate", counts[0] == 0 ? 0 : Math.round(counts[1] * 10000.0 / counts[0]) / 100.0);
            category.put(key, item);
        });
        report.put("categories", category);
        run.setReport(toJson(report));
        runMapper.updateById(run);
        report.put("runId", run.getId());
        return report;
    }

    private record CaseOutcome(boolean passed, String output, double score, String error) {
    }

    /** 执行单条用例（按类别）。 */
    private CaseOutcome executeCase(ObservTestCase testCase) {
        String input = testCase.getInput();
        String expected = testCase.getExpected();
        return switch (testCase.getCategory() == null ? "intent" : testCase.getCategory()) {
            case "recall" -> recallTest(input, expected);
            case "hallucination" -> hallucinationTest(input, expected);
            case "pressure" -> pressureTest(input);
            case "boundary" -> boundaryTest(input);
            default -> intentTest(input, expected, testCase.getAgentId());
        };
    }

    /** 意图准确率：Agent 对话回复包含期望关键词。 */
    private CaseOutcome intentTest(String input, String expected, String agentId) {
        String reply = chat(input, agentId);
        if (!StringUtils.hasText(expected)) {
            return new CaseOutcome(StringUtils.hasText(reply), reply, StringUtils.hasText(reply) ? 1 : 0, "无期望关键词，仅验证有回复");
        }
        boolean passed = reply != null && reply.contains(expected);
        return new CaseOutcome(passed, reply, passed ? 1 : 0, passed ? null : "回复未包含期望: " + expected);
    }

    /** 召回精度：知识库检索结果包含期望文档/内容。 */
    private CaseOutcome recallTest(String input, String expected) {
        List<Map<String, Object>> results = retrieveService.retrieve(input, null, null, 5, true);
        if (!StringUtils.hasText(expected)) {
            boolean passed = !results.isEmpty();
            return new CaseOutcome(passed, summarize(results), passed ? 1 : 0, passed ? null : "未召回任何内容");
        }
        boolean passed = results.stream().anyMatch(r ->
                String.valueOf(r.getOrDefault("docName", "")).contains(expected)
                        || String.valueOf(r.getOrDefault("content", "")).contains(expected));
        return new CaseOutcome(passed, summarize(results), passed ? 1 : 0,
                passed ? null : "召回结果未命中期望: " + expected);
    }

    /** 幻觉检测：知识库无关问题应拒绝回答（不臆造）；有资料问题应基于资料。 */
    private CaseOutcome hallucinationTest(String input, String expected) {
        List<Map<String, Object>> results = retrieveService.retrieve(input, null, null, 5, true);
        if (results.isEmpty() && !StringUtils.hasText(expected)) {
            // 无资料：Agent 应拒绝回答
            String reply = chat("请基于企业知识库回答：" + input, null);
            boolean rejected = reply != null && (reply.contains("无法") || reply.contains("未检索")
                    || reply.contains("没有") || reply.contains("不存在") || reply.contains("无法回答"));
            return new CaseOutcome(rejected, reply, rejected ? 1 : 0,
                    rejected ? null : "无资料时未拒绝回答（疑似幻觉）");
        }
        if (!StringUtils.hasText(expected)) {
            return new CaseOutcome(true, summarize(results), 1, null);
        }
        String context = summarize(results);
        String reply = chat("根据以下资料回答：" + context + "\n问题：" + input, null);
        boolean passed = reply != null && reply.contains(expected);
        return new CaseOutcome(passed, reply, passed ? 1 : 0, passed ? null : "回答未包含期望事实: " + expected);
    }

    /** 异常压力测试：连续调用 N 次，成功率不低于 80%。 */
    private CaseOutcome pressureTest(String input) {
        int attempts = 5;
        int success = 0;
        StringBuilder errors = new StringBuilder();
        for (int i = 0; i < attempts; i++) {
            try {
                String reply = chat(input, null);
                if (StringUtils.hasText(reply)) {
                    success++;
                } else {
                    errors.append("第").append(i + 1).append("次无回复; ");
                }
            } catch (Exception e) {
                errors.append("第").append(i + 1).append("次异常: ").append(safeMessage(e)).append("; ");
            }
        }
        double rate = success * 100.0 / attempts;
        boolean passed = rate >= 80;
        return new CaseOutcome(passed, "成功率 " + rate + "%", Math.round(rate) / 100.0,
                passed ? null : errors.toString());
    }

    /** 边界场景：空输入/超长/特殊字符不崩溃。 */
    private CaseOutcome boundaryTest(String input) {
        List<String> scenarios = new ArrayList<>();
        if (StrUtil.isBlank(input)) {
            scenarios.add("空输入");
        } else if (input.length() > 5000) {
            scenarios.add("超长输入(" + input.length() + "字)");
        } else if (input.contains("\u0000") || input.contains("\ufffd")) {
            scenarios.add("特殊字符");
        } else {
            scenarios.add("常规边界");
        }
        boolean allOk = true;
        for (String scenario : scenarios) {
            try {
                String reply = chat(input == null ? "" : input, null);
                if (!StringUtils.hasText(reply)) {
                    allOk = false;
                }
            } catch (Exception e) {
                allOk = false;
            }
        }
        return new CaseOutcome(allOk, "场景: " + String.join(", ", scenarios) + "，未崩溃", allOk ? 1 : 0,
                allOk ? null : "边界场景异常");
    }

    private String chat(String message, String agentId) {
        try {
            if (StringUtils.hasText(agentId)) {
                AiAgentReply reply = aiAgentService.chat(message,
                        new com.zimo.starter.ai.agent.AiAgentRouteRequest("default", "test",
                                "tester", "test-session", null, null));
                return reply == null ? "" : reply.content();
            }
            AiAgentReply reply = aiAgentService.chat(message, "test-session");
            return reply == null ? "" : reply.content();
        } catch (Exception e) {
            return "调用异常: " + safeMessage(e);
        }
    }

    private String summarize(List<Map<String, Object>> results) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(results.size(), 3); i++) {
            Map<String, Object> result = results.get(i);
            builder.append("[").append(i + 1).append("] ").append(result.get("docName"))
                    .append(" #").append(result.get("seq")).append(": ")
                    .append(truncate(String.valueOf(result.get("content")), 300)).append("\n");
        }
        return builder.toString();
    }

    /* ---------------- 批次与报告 ---------------- */

    public List<ObservTestRun> listRuns(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return runMapper.selectList(Wrappers.<ObservTestRun>lambdaQuery()
                .orderByDesc(ObservTestRun::getId)
                .last("LIMIT " + safeLimit));
    }

    public Map<String, Object> runReport(Long runId) {
        ObservTestRun run = runMapper.selectById(runId);
        ValidationUtil.requireNotNull(run, "批次不存在: ");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("run", run);
        result.put("results", resultMapper.selectList(Wrappers.<ObservTestCaseResult>lambdaQuery()
                .eq(ObservTestCaseResult::getRunId, runId)
                .orderByAsc(ObservTestCaseResult::getId)));
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safeMessage(Throwable exception) {
        String message = exception == null ? "" : exception.getMessage();
        return StrUtil.isBlank(message)
                ? (exception == null ? "未知错误" : exception.getClass().getSimpleName())
                : message;
    }
}
