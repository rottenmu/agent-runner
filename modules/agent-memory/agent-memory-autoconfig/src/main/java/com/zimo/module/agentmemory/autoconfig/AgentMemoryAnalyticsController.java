package com.zimo.module.agentmemory.autoconfig;

import com.zimo.module.agentmemory.analytics.MemoryAnalyticsService;
import com.zimo.module.agentmemory.memoryfile.MemoryFileService;
import com.zimo.module.agentmemory.memoryfile.MemoryFileStore;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 记忆 OLAP 分析接口（只读，基于 Arrow/Calcite 分析库）。
 *
 * <p>路径前缀 {@code /api/agent-memory/analytics}，仅做离线分析报表查询；
 * 运行时记忆 CRUD 走 {@code /api/ai/memory}（OLTP）。</p>
 */
@RestController
@RequestMapping("/api/agent-memory/analytics")
public class AgentMemoryAnalyticsController {

    private final MemoryAnalyticsService analyticsService;
    private final MemoryFileService fileService;
    private final com.zimo.module.agentmemory.storage.OltpMemoryRepository oltp;

    public AgentMemoryAnalyticsController(
            MemoryAnalyticsService analyticsService, MemoryFileService fileService,
            com.zimo.module.agentmemory.storage.OltpMemoryRepository oltp) {
        this.analyticsService = analyticsService;
        this.fileService = fileService;
        this.oltp = oltp;
    }

    /** 会话聚合统计。 */
    @GetMapping("/session-stats")
    public List<Map<String, Object>> sessionStats() {
        return analyticsService.sessionStats();
    }

    /** 用户行为时序（近 N 天消息量）。 */
    @GetMapping("/user-activity")
    public List<Map<String, Object>> userActivity(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "7") int days) {
        return analyticsService.userDailyActivity(userId, Math.max(1, Math.min(days, 90)));
    }

    /** 记忆蒸馏质量评估（L0 事件构成）。 */
    @GetMapping("/distillation-stats")
    public List<Map<String, Object>> distillationStats() {
        return analyticsService.memoryDistillationStats();
    }

    /** 按 traceId 查询完整事件链（溯源）。 */
    @GetMapping("/trace")
    public List<Map<String, Object>> trace(@RequestParam String traceId) {
        return analyticsService.traceEvents(traceId);
    }

    /** 自定义 OLAP SQL（Calcite 查询内存宽表 olap_l0_log）。 */
    @GetMapping("/query")
    public List<Map<String, Object>> query(@RequestParam String sql) {
        return analyticsService.query(sql);
    }

    /**
     * 会话消息分页（记忆总览"会话消息"Tab）：OLAP 宽表按会话过滤 + 分页。
     *
     * @param sessionId 会话 ID（可选；仅允许字母/数字/下划线/连字符，防 SQL 注入）
     * @param page      页码（从 0 开始）
     * @param size      每页条数（默认 50，上限 500）
     */
    @GetMapping("/messages")
    public Map<String, Object> messages(
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int safeSize = Math.max(1, Math.min(size, 500));
        int offset = Math.max(0, page) * safeSize;
        String where = "";
        Object[] args = null;
        if (sessionId != null && !sessionId.isBlank()) {
            where = " WHERE session_id = '" + sessionId.replaceAll("[^a-zA-Z0-9_-]", "") + "'";
        }
        List<Map<String, Object>> rows = analyticsService.query(
                "SELECT trace_id, session_id, role, content, ts, meta_json FROM olap_l0_log"
                        + where + " ORDER BY ts DESC LIMIT " + safeSize + " OFFSET " + offset);
        rows.forEach(row -> row.put("files", parseFiles(row)));
        rows.forEach(row -> row.remove("meta_json"));
        List<Map<String, Object>> all = analyticsService.query(
                "SELECT COUNT(*) AS cnt FROM olap_l0_log" + where);
        long total = all.isEmpty() ? 0L : Long.parseLong(String.valueOf(all.get(0).get("cnt")));
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("rows", rows);
        m.put("total", total);
        m.put("page", page);
        m.put("size", safeSize);
        return m;
    }

    /** 解析 meta_json 中的文件信息（files 数组），非法/缺失返回空列表。 */
    private List<Map<String, Object>> parseFiles(Map<String, Object> row) {
        Object meta = row.get("meta_json");
        if (meta == null) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(String.valueOf(meta));
            com.fasterxml.jackson.databind.JsonNode files = node.path("files");
            if (files.isArray()) {
                List<Map<String, Object>> list = new java.util.ArrayList<>();
                files.forEach(f -> {
                    java.util.LinkedHashMap<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("name", f.path("name").asText(""));
                    item.put("url", f.path("url").asText(""));
                    item.put("size", f.path("size").asLong(0L));
                    list.add(item);
                });
                return list;
            }
        } catch (Exception e) {
            /* 忽略解析失败 */
        }
        return List.of();
    }

    /**
     * 会话消息预览：返回该会话的 L0 消息列表 + 来源记忆文件信息（md 文档名 + 文件地址）。
     *
     * @param sessionId 会话 ID（仅允许字母/数字/下划线/连字符，防 SQL 注入）
     * @param agentId   智能体标识（定位文件兼容模式记忆文件 {baseDir}/{agentId}/MEMORY.md）
     */
    @GetMapping("/session-messages")
    public Map<String, Object> sessionMessages(
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "default") String agentId) {
        String safe = sessionId.replaceAll("[^a-zA-Z0-9_-]", "");
        List<Map<String, Object>> messages = analyticsService.query(
                "SELECT trace_id, session_id, role, content, ts FROM olap_l0_log "
                        + "WHERE session_id = '" + safe + "' ORDER BY ts ASC");
        MemoryFileStore store = fileService.storeFor(agentId);
        String fileDesc = store.location();
        return Map.of(
                "agentId", agentId,
                "sessionId", safe,
                "memoryFileName", "MEMORY.md",
                "memoryFilePath", fileDesc,
                "messages", messages);
    }

    /* ================= 用户画像（L3 Persona）列表 / 提取 / 删除 ================= */

    /** 全量用户画像列表（用户 / 核心摘要 / 背景 / 版本 / 来源）。 */
    @GetMapping("/personas")
    public java.util.List<Map<String, Object>> personas() {
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (com.zimo.module.agentmemory.model.L3Persona p : oltp.listAllPersonas()) {
            java.util.LinkedHashMap<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", p.id());
            row.put("userId", p.userId());
            row.put("content", p.content());
            row.put("personaType", p.personaType());
            row.put("version", p.version());
            row.put("updatedTs", p.updatedTs());
            list.add(row);
        }
        return list;
    }

    /** 提取画像：聚合该用户 L1 记忆生成/更新 persona（版本递增）。 */
    @PostMapping("/personas/{userId}/extract")
    public Map<String, Object> extractPersona(@PathVariable String userId) {
        java.util.List<String> types = java.util.List.of(
                com.zimo.module.agentmemory.model.L1AtomicMemory.TYPE_PREFERENCE,
                com.zimo.module.agentmemory.model.L1AtomicMemory.TYPE_FACT,
                com.zimo.module.agentmemory.model.L1AtomicMemory.TYPE_HABIT,
                "history", "custom");
        java.util.LinkedHashSet<String> facts = new java.util.LinkedHashSet<>();
        for (String type : types) {
            for (com.zimo.module.agentmemory.model.L1AtomicMemory m : oltp.recallAtomicByType(userId, type, 20)) {
                if (m.content() != null && !m.content().isBlank()) {
                    facts.add(m.content().trim());
                }
            }
        }
        String summary = String.join("；", facts);
        com.zimo.module.agentmemory.model.L3Persona existing = oltp.getPersona(userId, "persona");
        int version = existing == null ? 1 : existing.version() + 1;
        com.zimo.module.agentmemory.model.L3Persona persona = new com.zimo.module.agentmemory.model.L3Persona(
                "p-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                userId, "persona",
                summary.isBlank() ? "（暂无可提取的 L1 记忆）" : summary,
                version, System.currentTimeMillis());
        oltp.savePersona(persona);
        return Map.of("userId", userId, "version", version, "content", persona.content());
    }

    /** 删除画像。 */
    @DeleteMapping("/personas/{userId}/{personaType}")
    public Map<String, Object> deletePersona(
            @PathVariable String userId,
            @PathVariable String personaType) {
        oltp.deletePersona(userId, personaType);
        return Map.of("ok", true);
    }
}
