package com.zimo.framework.ai.agent.memory;

import com.zimo.framework.common.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AgentState task 分区的查询与清理接口（M4-3，PRD 标准 10）。
 *
 * <p><b>为什么要暴露查询接口</b>：标准 10 的判据是「任务结束后 task 分区条数为 0」。
 * 若只提供清理、不提供查询，这条判据就只能靠「代码里调了 clear」来声称通过 ——
 * 那正是 M3 偏差 3/4 那类「恒真守卫」的翻版。有了本接口，验证脚本可以直接观测条数。</p>
 *
 * <p>路径挂在 {@code /api/ai/memory/agentstate/**} 下，与 agent-memory 的治理接口同前缀但
 * 不同子路径 —— 之所以放在 framework-ai 而不是 agent-memory-core：
 * 探针依赖 {@code FileStorageService} 与 AgentScope 的 state 结构，
 * 而 agent-memory-core 反向依赖 framework-ai 会形成 Maven 循环。</p>
 *
 * @author WorkBuddy
 * @since 2026-09-17
 */
@RestController
@RequestMapping("/api/ai/memory/agentstate")
public class AgentStateTaskController {

    private final AgentStateTaskPartitionProbe probe;

    public AgentStateTaskController(AgentStateTaskPartitionProbe probe) {
        this.probe = probe;
    }

    /**
     * 查询会话的 task 分区条数。
     *
     * <p><b>响应刻意区分 {@code readable}</b>：条数为 0 有两种截然不同的含义 ——
     * 「确实清空了」与「压根读不到 task 分区」。若只回一个 count=0，
     * 验证脚本无法区分「清理生效」和「探针坏了」，标准 10 就退化成了恒真断言。</p>
     *
     * <p>{@code sessionId} 路径参数要传<b>完整会话隔离键</b>（{@code AiHarnessSessionKeyFactory}
     * 的产物，形如 {@code 6_e2e-m48_ai-agent7_console12_m4-sess4_u-m4}），
     * 不是裸的 conversationId —— 存储 key 的第二段就是它。</p>
     *
     * @param userId 用户标识（对应存储 key 的第一段）
     * @return {@code {readable, count, completed, subjects, cleared, reason}}
     */
    @GetMapping("/{sessionId}/task-count")
    public ApiResponse<Map<String, Object>> taskCount(
            @PathVariable("sessionId") String sessionId,
            @RequestParam("userId") String userId) {
        AgentStateTaskPartitionProbe.Snapshot snapshot = probe.inspect(userId, sessionId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("readable", snapshot.readable());
        body.put("count", snapshot.total());
        body.put("completed", snapshot.completed());
        body.put("subjects", snapshot.subjects());
        body.put("cleared", snapshot.cleared());
        body.put("reason", snapshot.reason());
        return ApiResponse.ok(body);
    }

    /**
     * 清空会话的 task 分区（任务终态清理的手动入口）。
     *
     * @param userId 用户标识（对应存储 key 的第一段）
     * @return {@code {cleared}} —— 是否有内容被删除
     */
    @DeleteMapping("/{sessionId}/task-partition")
    public ApiResponse<Map<String, Object>> clearTaskPartition(
            @PathVariable("sessionId") String sessionId,
            @RequestParam("userId") String userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cleared", probe.clear(userId, sessionId));
        return ApiResponse.ok(body);
    }
}
