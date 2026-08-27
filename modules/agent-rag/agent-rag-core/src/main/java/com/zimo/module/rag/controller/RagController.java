package com.zimo.module.rag.controller;

import com.zimo.module.rag.entity.RagChunk;
import com.zimo.module.rag.entity.RagDocument;
import com.zimo.module.rag.entity.RagEvaluation;
import com.zimo.module.rag.entity.RagKbPermission;
import com.zimo.module.rag.entity.RagKnowledgeBase;
import com.zimo.module.rag.entity.RagRetrieveLog;
import com.zimo.module.rag.entity.RagVersion;
import com.zimo.module.rag.service.RagAutoSyncService;
import com.zimo.module.rag.service.RagChatService;
import com.zimo.module.rag.service.RagEvaluationService;
import com.zimo.module.rag.service.RagKbPermissionService;
import com.zimo.module.rag.service.RagKbService;
import com.zimo.module.rag.service.RagPipelineService;
import com.zimo.module.rag.service.RagRetrieveLogService;
import com.zimo.module.rag.service.RagRetrieveService;
import com.zimo.module.rag.service.RagVersionService;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.zimo.framework.common.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 知识库管理接口：知识库管控、权限隔离、文档入库、版本回溯、定时更新、测评、检索日志。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
@RestController
@RequestMapping("/api/biz/rag")
public class RagController {

    private final RagPipelineService pipelineService;
    private final RagRetrieveService retrieveService;
    private final RagKbService kbService;
    private final RagKbPermissionService permissionService;
    private final RagVersionService versionService;
    private final RagAutoSyncService autoSyncService;
    private final RagEvaluationService evaluationService;
    private final RagRetrieveLogService retrieveLogService;
    private final RagChatService chatService;

    public RagController(
            RagPipelineService pipelineService,
            RagRetrieveService retrieveService,
            RagKbService kbService,
            RagKbPermissionService permissionService,
            RagVersionService versionService,
            RagAutoSyncService autoSyncService,
            RagEvaluationService evaluationService,
            RagRetrieveLogService retrieveLogService,
            RagChatService chatService) {
        this.pipelineService = pipelineService;
        this.retrieveService = retrieveService;
        this.kbService = kbService;
        this.permissionService = permissionService;
        this.versionService = versionService;
        this.autoSyncService = autoSyncService;
        this.evaluationService = evaluationService;
        this.retrieveLogService = retrieveLogService;
        this.chatService = chatService;
    }

    /* ---------------- 知识库管控 ---------------- */

    /** 当前用户可访问的知识库（含访问级别）。 */
    @GetMapping("/knowledge-bases")
    public ApiResponse<List<Map<String, Object>>> knowledgeBases() {
        return ApiResponse.ok(kbService.listAccessibleKbs(currentUserId(), isAdmin()));
    }

    /** 创建知识库。 */
    @PostMapping("/knowledge-bases")
    public ApiResponse<RagKnowledgeBase> createKb(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(kbService.create(
                str(body.get("name")),
                str(body.get("description")),
                tags(body),
                str(body.get("visibility")),
                bool(body.get("autoSyncEnabled")),
                str(body.get("autoSyncCron")),
                currentUserId(),
                tenantId()));
    }

    /** 更新知识库（含自动更新配置）。 */
    @PutMapping("/knowledge-bases/{id}")
    public ApiResponse<RagKnowledgeBase> updateKb(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(kbService.update(
                id,
                str(body.get("name")),
                str(body.get("description")),
                tags(body),
                str(body.get("visibility")),
                bool(body.get("enabled")),
                bool(body.get("autoSyncEnabled")),
                str(body.get("autoSyncCron")),
                currentUserId(), isAdmin()));
    }

    /** 删除知识库（级联文档/权限）。 */
    @DeleteMapping("/knowledge-bases/{id}")
    public ApiResponse<Void> deleteKb(@PathVariable Long id) {
        kbService.delete(id, currentUserId(), isAdmin());
        return ApiResponse.ok();
    }

    /** 全部标签（跨知识库统计）。 */
    @GetMapping("/tags")
    public ApiResponse<List<Map<String, Object>>> tags() {
        return ApiResponse.ok(kbService.allTags());
    }

    /** 手动触发知识库同步。 */
    @PostMapping("/knowledge-bases/{id}/sync")
    public ApiResponse<Map<String, Object>> syncKb(@PathVariable Long id) {
        permissionService.requireManage(currentUserId(), isAdmin(), id);
        return ApiResponse.ok(autoSyncService.syncKb(id));
    }

    /* ---------------- 权限隔离 ---------------- */

    /** 知识库权限列表。 */
    @GetMapping("/knowledge-bases/{kbId}/permissions")
    public ApiResponse<List<RagKbPermission>> permissions(@PathVariable Long kbId) {
        permissionService.requireManage(currentUserId(), isAdmin(), kbId);
        return ApiResponse.ok(permissionService.listByKb(kbId));
    }

    /** 授予权限。 */
    @PostMapping("/knowledge-bases/{kbId}/permissions")
    public ApiResponse<RagKbPermission> grantPermission(
            @PathVariable Long kbId,
            @RequestBody Map<String, String> body) {
        permissionService.requireManage(currentUserId(), isAdmin(), kbId);
        return ApiResponse.ok(permissionService.grant(
                kbId, body.get("principalType"), body.get("principalId"), body.get("accessLevel")));
    }

    /** 撤销权限。 */
    @DeleteMapping("/permissions/{id}")
    public ApiResponse<Void> revokePermission(@PathVariable Long id) {
        permissionService.revoke(id);
        return ApiResponse.ok();
    }

    /* ---------------- 文档管理 ---------------- */

    /** 文档列表（按知识库/标签筛选）。 */
    @GetMapping("/documents")
    public ApiResponse<List<RagDocument>> documents(
            @RequestParam(required = false) Long kbId,
            @RequestParam(required = false) String tag) {
        List<RagDocument> documents = kbService.listDocuments(kbId, tag, currentUserId(), isAdmin());
        if (StrUtil.isNotBlank(tag)) {
            documents = documents.stream()
                    .filter(d -> d.getTags() != null && d.getTags().contains(tag))
                    .toList();
        }
        return ApiResponse.ok(documents);
    }

    /** 注册文档（可归档到知识库 + 标签）。 */
    @PostMapping("/documents")
    public ApiResponse<RagDocument> createDocument(@RequestBody Map<String, Object> body) {
        Long kbId = longValue(body.get("kbId"));
        if (kbId != null) {
            permissionService.requireWrite(currentUserId(), isAdmin(), kbId);
        }
        return ApiResponse.ok(pipelineService.createDocument(
                str(body.get("name")), str(body.get("sourcePath")), kbId, tags(body)));
    }

    /** 执行处理管道（解析→表格→清洗→切片→向量化），支持自定义切片参数。 */
    @PostMapping("/documents/{id}/process")
    public ApiResponse<Map<String, Object>> process(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        RagDocument document = pipelineService.requireDocument(id);
        if (document.getKbId() != null) {
            permissionService.requireWrite(currentUserId(), isAdmin(), document.getKbId());
        }
        Map<String, Object> options = body == null ? Map.of() : body;
        options = new java.util.LinkedHashMap<>(options);
        options.put("createdBy", currentUserId());
        return ApiResponse.ok(pipelineService.processDocument(id, options));
    }

    /** 归档文档到知识库。 */
    @PostMapping("/documents/{id}/archive")
    public ApiResponse<RagDocument> archive(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long kbId = longValue(body.get("kbId"));
        return ApiResponse.ok(kbService.archive(id, kbId, currentUserId(), isAdmin()));
    }

    /** 设置文档标签。 */
    @PutMapping("/documents/{id}/tags")
    public ApiResponse<RagDocument> setTags(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(kbService.setDocumentTags(id, tags(body)));
    }

    /** 文档切片预览。 */
    @GetMapping("/documents/{id}/chunks")
    public ApiResponse<List<RagChunk>> chunks(@PathVariable Long id) {
        return ApiResponse.ok(pipelineService.chunksByDocument(id));
    }

    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        pipelineService.deleteDocument(id);
        return ApiResponse.ok();
    }

    /* ---------------- 版本回溯 ---------------- */

    /** 文档版本列表。 */
    @GetMapping("/documents/{id}/versions")
    public ApiResponse<List<RagVersion>> versions(@PathVariable Long id) {
        return ApiResponse.ok(versionService.listByDocument(id));
    }

    /** 回滚到指定版本。 */
    @PostMapping("/versions/{versionId}/rollback")
    public ApiResponse<Map<String, Object>> rollback(@PathVariable Long versionId) {
        int restored = versionService.rollback(versionId, currentUserId(), isAdmin());
        return ApiResponse.ok(Map.of("restored", restored));
    }

    /* ---------------- 测评 ---------------- */

    @GetMapping("/evaluations")
    public ApiResponse<List<RagEvaluation>> evaluations(
            @RequestParam(required = false) Long kbId) {
        return ApiResponse.ok(evaluationService.list(kbId));
    }

    @PostMapping("/evaluations")
    public ApiResponse<RagEvaluation> createEvaluation(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(evaluationService.create(
                longValue(body.get("kbId")), str(body.get("query")), str(body.get("expectedDoc"))));
    }

    @PutMapping("/evaluations/{id}")
    public ApiResponse<RagEvaluation> updateEvaluation(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(evaluationService.update(id, str(body.get("query")), str(body.get("expectedDoc"))));
    }

    @DeleteMapping("/evaluations/{id}")
    public ApiResponse<Void> deleteEvaluation(@PathVariable Long id) {
        evaluationService.delete(id);
        return ApiResponse.ok();
    }

    /** 运行测评（命中率/平均分）。 */
    @PostMapping("/evaluations/run")
    public ApiResponse<Map<String, Object>> runEvaluations(@RequestBody Map<String, Object> body) {
        Long kbId = longValue(body.get("kbId"));
        int topK = body.get("topK") instanceof Number n ? n.intValue() : 8;
        return ApiResponse.ok(evaluationService.run(kbId, topK));
    }

    /* ---------------- 检索日志 ---------------- */

    @GetMapping("/retrieve-logs")
    public ApiResponse<List<RagRetrieveLog>> retrieveLogs(
            @RequestParam(required = false) Long kbId,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(retrieveLogService.list(kbId, source, limit));
    }

    /* ---------------- 检索与重排 ---------------- */

    /** 知识库问答：检索相关片段 + 大模型生成答案（带来源引用）。 */
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String question = body == null ? null : str(body.get("question"));
        Long kbId = longValue(body.get("kbId"));
        int topK = body != null && body.get("topK") instanceof Number n ? n.intValue() : 6;
        boolean rerank = body == null || body.get("rerank") == null
                ? true
                : Boolean.parseBoolean(String.valueOf(body.get("rerank")));
        if (kbId != null) {
            permissionService.requireRead(currentUserId(), isAdmin(), kbId);
        }
        return ApiResponse.ok(chatService.chat(question, kbId, topK, rerank));
    }

    /** 检索知识库：向量召回 + 可选 Rerank，校验知识库访问权限并记录日志。 */
    @PostMapping("/retrieve")
    public ApiResponse<List<Map<String, Object>>> retrieve(@RequestBody Map<String, Object> body) {
        String query = body == null ? null : str(body.get("query"));
        Long kbId = longValue(body.get("kbId"));
        Long docId = longValue(body.get("docId"));
        int topK = body != null && body.get("topK") instanceof Number n ? n.intValue() : 8;
        boolean rerank = body == null || body.get("rerank") == null
                ? true
                : Boolean.parseBoolean(String.valueOf(body.get("rerank")));
        if (kbId != null) {
            permissionService.requireRead(currentUserId(), isAdmin(), kbId);
        }
        long start = System.currentTimeMillis();
        List<Map<String, Object>> results = retrieveService.retrieve(query, kbId, docId, topK, rerank);
        long latency = System.currentTimeMillis() - start;
        retrieveLogService.record(kbId, query, docId, topK, rerank,
                results.size(), latency, "console", currentUserId());
        return ApiResponse.ok(results);
    }

    /* ---------------- 上下文 ---------------- */

    private String currentUserId() {
        try {
            if (StpUtil.isLogin()) {
                return String.valueOf(StpUtil.getLoginIdAsLong());
            }
        } catch (Exception ignored) {
        }
        return "anonymous";
    }

    private boolean isAdmin() {
        try {
            return StpUtil.isLogin() && StpUtil.hasRole("admin");
        } catch (Exception e) {
            return false;
        }
    }

    private String tenantId() {
        return "default";
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Boolean bool(Object value) {
        return value == null ? null : Boolean.parseBoolean(String.valueOf(value));
    }

    private Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> tags(Map<String, Object> body) {
        if (body == null) {
            return List.of();
        }
        Object raw = body.get("tags");
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
