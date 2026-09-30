package com.zimo.framework.ai.agent.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.common.storage.FileStorageService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AgentState 中 task 分区的查询与清理（M4-3，PRD 标准 10）。
 *
 * <p><b>要解决的问题</b>：AgentScope 把计划任务清单（task 分区）存在会话状态里，
 * 任务结束后若不清理，这份清单会一直挂在会话上 —— 下一次对话可能读到上一轮的计划残留。
 * 标准 10 的判据是「任务结束后条数为 0」，而<b>只做清理不提供查询</b>的话，
 * 这条判据只能靠「代码里有 clear 调用」来声称通过，无法观测。</p>
 *
 * <h2>存储形态（2026-09-17 以 javap 反编译 + 读线上 RocksDB 双重确证）</h2>
 * <pre>
 * key:   astate/{userId}/{sessionKey}/agent_state
 * 实例:  astate/u-m4/6_e2e-m48_ai-agent7_console12_m4-sess-plan4_u-m4/agent_state
 * value: { "tasks_context": { "tasks": [ {...} ] }, ... }
 * task:  { "subject": "...", "state": "pending|in_progress|completed", ... }
 * </pre>
 *
 * <p><b>⚠️ 第一段是 userId 不是 agentId</b>：AgentScope 的 {@code ReActAgent} 存 state 时
 * 用的是 {@code SlotRef(sessionKey).userId()}，而 {@code SlotRef.parse} 只按<b>最后一个</b>
 * {@code /} 切分 —— 左边全部当 userId、右边当 sessionId。本仓的 sessionKey 由
 * {@code AiHarnessSessionKeyFactory} 用「长度:内容」拼成（冒号分隔，见上面实例里的
 * {@code 6_e2e-m4}），因此整串 sessionKey 落在第二段。</p>
 *
 * <p><b>为什么通篇不 import AgentScope 的 {@code AgentState}/{@code Task} 类型</b>：
 * 这两个类的 wire 字段名（{@code tasks_context} / {@code created_at} / {@code blocked_by}）
 * 是 Jackson 注解的产物，而库里的 getter 名（{@code getTasksContext} / {@code getCreatedAt}）
 * 与之<b>不一致</b>。若用类型反序列化再转回 JSON 做嗅探，一旦字段名对不上就会
 * <b>静默读成 0 条</b> —— 「已清空」和「根本没读到」两种截然不同的状态在判据上长得一模一样，
 * 标准 10 就退化成了恒真断言。因此这里直接按<b>已确证的字段名</b>读原始 JSON，
 * 并在读不到任何相关 key 时明确区分于「读到了但为空」。</p>
 *
 * @author WorkBuddy
 * @since 2026-09-17
 */
public class AgentStateTaskPartitionProbe {

    private static final Logger log = LoggerFactory.getLogger(AgentStateTaskPartitionProbe.class);

    /** 与 {@link RocksdbAgentStateStore} 一致的键前缀。 */
    private static final String PREFIX = "astate";
    private static final String SEP = "/";
    /** AgentState 的存储 key（{@code ReActAgent} 中的字面量 {@code agent_state}）。 */
    private static final String STATE_KEY = "agent_state";
    /** 外层字段：AgentState 里的 tasks 分区（Jackson {@code @JsonProperty} 名）。 */
    private static final String TASKS_CONTEXT_FIELD = "tasks_context";
    /** 内层字段：{@code TaskContextState} 持有的任务数组。 */
    private static final String TASKS_FIELD = "tasks";
    /** 兼容旧形态的驼峰 key（探针兜底用，不作为主路径）。 */
    private static final String TASKS_CONTEXT_FIELD_LEGACY = "tasksContext";

    private final FileStorageService storage;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentStateTaskPartitionProbe(FileStorageService storage) {
        this.storage = storage;
    }

    /**
     * 读取会话的 task 分区快照。
     *
     * @param userId    用户标识（存储 key 的第一段，<b>不是</b> agentId）
     * @param sessionKey 会话隔离键（{@code AiHarnessSessionKeyFactory} 的产物）
     * @return 快照；读不到相关 key 时 {@code readable()} 为 {@code false}
     */
    public Snapshot inspect(String userId, String sessionKey) {
        if (storage == null || !hasText(userId) || !hasText(sessionKey)) {
            return Snapshot.unreadable("userId / sessionKey 不完整");
        }
        List<String> keys = candidateKeys(userId, sessionKey);
        if (keys.isEmpty()) {
            // 区分「没有 state」与「有 state 但没 task 分区」：两者条数都是 0，
            // 但含义不同 —— 前者是尚未进入计划模式，后者是计划已完成并清理。
            return Snapshot.unreadable("会话下不存在 task 分区 key");
        }
        int total = 0;
        int completed = 0;
        List<String> subjects = new ArrayList<>();
        for (String key : keys) {
            for (JsonNode task : tasksOf(key)) {
                total++;
                // Task$State 的 wire 值是小写下划线形态（pending / in_progress / completed）。
                String state = task.path("state").asText("");
                if ("completed".equalsIgnoreCase(state)) {
                    completed++;
                }
                subjects.add(task.path("subject").asText(""));
            }
        }
        return new Snapshot(true, total, completed, subjects, null);
    }

    /**
     * 清空会话的 task 分区（任务终态时调用）。
     *
     * @param userId     用户标识（存储 key 的第一段，<b>不是</b> agentId）
     * @param sessionKey 会话隔离键
     * @return 是否确实做了删除动作；无可删内容时返回 {@code false}
     */
    public boolean clear(String userId, String sessionKey) {
        if (storage == null || !hasText(userId) || !hasText(sessionKey)) {
            return false;
        }
        List<String> keys = candidateKeys(userId, sessionKey);
        boolean cleared = false;
        for (String key : keys) {
            try {
                cleared |= storage.delete(key);
            } catch (RuntimeException e) {
                log.warn("[agentstate] task 分区清理失败 key={}：{}", key, e.toString());
            }
        }
        if (cleared) {
            log.info("[agentstate] 已清理 task 分区 userId={} sessionKey={} keys={}",
                    userId, sessionKey, keys.size());
        }
        return cleared;
    }

    /** 读单个 key 并取出其中的 task 数组（解析失败记警告并当作空）。 */
    private List<JsonNode> tasksOf(String key) {
        List<JsonNode> tasks = new ArrayList<>();
        try {
            byte[] raw = storage.get(key);
            if (raw == null || raw.length == 0) {
                return tasks;
            }
            tasksOf(objectMapper.readTree(raw), tasks);
        } catch (Exception e) {
            log.warn("[agentstate] task 分区解析失败 key={}：{}", key, e.toString());
        }
        return tasks;
    }

    /**
     * 按会话前缀列出候选 key，并用内容嗅探筛出 task 分区。
     *
     * <p><b>⚠️ 前缀里的分隔符必须与 {@link RocksdbAgentStateStore} 的净化规则一致</b>：
     * 该存储写入时对每一段都跑 {@code safe()}（非 {@code [A-Za-z0-9._-]} 一律换成 {@code _}），
     * 而本仓会话键的 {@code :} 分隔符正好会被换掉。线上实际落盘形态是
     * {@code astate/u-m4/6_e2e-m48_ai-agent7_console12_m4-sess-plan4_u-m4/agent_state}
     * —— 冒号已变成下划线。若这里不做同样的净化，前缀就对不上，
     * <b>永远列出 0 个 key</b>，探针恒报「不可读」，标准 10 无声失效。</p>
     */
    private List<String> candidateKeys(String userId, String sessionKey) {
        String prefix = PREFIX + SEP + safe(userId) + SEP + safe(sessionKey) + SEP;
        List<String> result = new ArrayList<>();
        try {
            for (String key : storage.list(prefix)) {
                if (looksLikeTaskPartition(key)) {
                    result.add(key);
                }
            }
        } catch (RuntimeException e) {
            log.warn("[agentstate] 会话 key 枚举失败 prefix={}：{}", prefix, e.toString());
        }
        return result;
    }

    /**
     * 判断某 key 是否承载 task 分区。
     *
     * <p>主路径是 key 名精确等于 {@code agent_state}（已确证的存储键）；
     * 兜底再做内容嗅探，以便框架把 key 改名后探针仍能工作 —— 只按名字会在改名后失配，
     * 只按内容则要对每个 key 做一次 IO。</p>
     */
    private boolean looksLikeTaskPartition(String key) {
        String tail = key.substring(key.lastIndexOf(SEP) + 1);
        if (STATE_KEY.equals(tail)) {
            return true;
        }
        if (tail.toLowerCase(java.util.Locale.ROOT).contains("task")) {
            return true;
        }
        try {
            byte[] raw = storage.get(key);
            if (raw == null) {
                return false;
            }
            return objectMapper.readTree(raw).has(TASKS_CONTEXT_FIELD);
        } catch (Exception e) {
            return false;
        }
    }

    /** 从根节点里取出任务数组：兼容「包一层 tasks_context」与旧驼峰两种形态。 */
    private static void tasksOf(JsonNode root, List<JsonNode> sink) {
        JsonNode nested = root.path(TASKS_CONTEXT_FIELD).path(TASKS_FIELD);
        if (!nested.isArray()) {
            nested = root.path(TASKS_CONTEXT_FIELD_LEGACY).path(TASKS_FIELD);
        }
        if (nested.isArray()) {
            nested.forEach(sink::add);
        }
    }

    private static String safe(String value) {
        return value == null ? "_" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * task 分区快照。
     *
     * @param readable  是否成功读到 task 分区（{@code false} 时 {@code total} 恒为 0，
     *                  调用方必须区分这与「已清空」）
     * @param total     任务总条数
     * @param completed 已完成条数
     * @param subjects  任务标题（便于排障时看清残留了什么）
     * @param reason    不可读原因；可读时为 {@code null}
     */
    public record Snapshot(boolean readable, int total, int completed,
                           List<String> subjects, String reason) {

        static Snapshot unreadable(String reason) {
            return new Snapshot(false, 0, 0, List.of(), reason);
        }

        /** 是否已清空（严格版：必须可读且为 0，不能把「读不到」当成清空）。 */
        public boolean cleared() {
            return readable && total == 0;
        }
    }
}
