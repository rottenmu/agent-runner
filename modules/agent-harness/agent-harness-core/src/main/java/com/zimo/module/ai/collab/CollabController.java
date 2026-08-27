package com.zimo.module.ai.collab;

import cn.dev33.satoken.stp.StpUtil;
import com.zimo.framework.common.ApiResponse;
import com.zimo.module.ai.collab.AgentRoleTemplate;
import com.zimo.module.ai.collab.AgentSession;
import com.zimo.module.ai.collab.AgentSessionMember;
import com.zimo.module.ai.collab.AgentSessionMessage;
import com.zimo.module.ai.collab.AgentTask;
import com.zimo.module.ai.collab.MultiAgentCollaborationService;
import com.zimo.module.ai.management.AiManagedAgent;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 多智能体协同接口：群组会话、任务分发/转交/委派、审批链、子任务回调、冲突处理、角色模板。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
@RestController
@RequestMapping("/api/biz/ai/collab")
public class CollabController {

    private final MultiAgentCollaborationService collabService;

    public CollabController(MultiAgentCollaborationService collabService) {
        this.collabService = collabService;
    }

    /* ---------------- 会话 ---------------- */

    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> sessions() {
        return ApiResponse.ok(collabService.listSessions());
    }

    @PostMapping("/sessions")
    public ApiResponse<AgentSession> createSession(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(collabService.createSession(body.get("name"), body.get("type"), currentUserId()));
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<Map<String, Object>> sessionDetail(@PathVariable Long id) {
        return ApiResponse.ok(collabService.sessionDetail(id));
    }

    @PostMapping("/sessions/{id}/members")
    public ApiResponse<AgentSessionMember> addMember(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ApiResponse.ok(collabService.addMember(id, body.get("memberType"), body.get("memberId"), body.get("role")));
    }

    @DeleteMapping("/members/{id}")
    public ApiResponse<Void> removeMember(@PathVariable Long id) {
        collabService.removeMember(id);
        return ApiResponse.ok();
    }

    /* ---------------- 消息（群组会话/协商沟通） ---------------- */

    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<AgentSessionMessage>> messages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(collabService.listMessages(id, limit));
    }

    @PostMapping("/sessions/{id}/messages")
    public ApiResponse<AgentSessionMessage> sendMessage(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(collabService.sendMessage(id,
                str(body.get("senderType")), str(body.get("senderId")), str(body.get("senderName")),
                str(body.get("contentType")), str(body.get("content")),
                body.get("taskId") == null ? null : Long.valueOf(String.valueOf(body.get("taskId")))));
    }

    /* ---------------- 任务 ---------------- */

    @GetMapping("/tasks")
    public ApiResponse<List<AgentTask>> tasks(@RequestParam Long sessionId) {
        return ApiResponse.ok(collabService.listTasks(sessionId));
    }

    @PostMapping("/tasks")
    public ApiResponse<AgentTask> createTask(@RequestBody Map<String, Object> body) {
        if (!body.containsKey("creatorId")) {
            body.put("creatorId", currentUserId());
        }
        return ApiResponse.ok(collabService.createTask(body));
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<Map<String, Object>> taskDetail(@PathVariable Long id) {
        return ApiResponse.ok(collabService.taskDetail(id));
    }

    /** 执行任务（Agent 执行并回填结果）。 */
    @PostMapping("/tasks/{id}/execute")
    public ApiResponse<Map<String, Object>> executeTask(@PathVariable Long id) {
        return ApiResponse.ok(collabService.executeTask(id));
    }

    /** 审批（上下级审批链）。 */
    @PostMapping("/tasks/{id}/approve")
    public ApiResponse<Map<String, Object>> approveTask(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        boolean approved = body.get("approved") == null || Boolean.parseBoolean(String.valueOf(body.get("approved")));
        return ApiResponse.ok(collabService.approveTask(id,
                str(body.get("operatorType")), str(body.get("operatorId")), approved, str(body.get("comment"))));
    }

    /** 转交（智能体转交）。 */
    @PostMapping("/tasks/{id}/handoff")
    public ApiResponse<AgentTask> handoff(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(collabService.handoff(id, body.get("assigneeType"), body.get("assigneeId")));
    }

    /** 委派（创建子任务，父任务等待回调）。 */
    @PostMapping("/tasks/{id}/delegate")
    public ApiResponse<AgentTask> delegate(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(collabService.delegate(id, body.get("delegateType"), body.get("delegateId"), body.get("instruction")));
    }

    /** 标记冲突。 */
    @PostMapping("/tasks/{id}/conflict")
    public ApiResponse<AgentTask> markConflict(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return ApiResponse.ok(collabService.markConflict(id, body == null ? null : body.get("comment")));
    }

    /** 解决冲突。 */
    @PostMapping("/tasks/{id}/conflict/resolve")
    public ApiResponse<Map<String, Object>> resolveConflict(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ApiResponse.ok(collabService.resolveConflict(id, body.get("resolution"), body.get("comment")));
    }

    /** 取消任务。 */
    @PostMapping("/tasks/{id}/cancel")
    public ApiResponse<AgentTask> cancelTask(@PathVariable Long id) {
        return ApiResponse.ok(collabService.cancelTask(id, currentUserId()));
    }

    /* ---------------- 角色模板 ---------------- */

    @GetMapping("/templates")
    public ApiResponse<List<AgentRoleTemplate>> templates() {
        return ApiResponse.ok(collabService.listTemplates());
    }

    /** 从模板创建 Agent。 */
    @PostMapping("/templates/{id}/create-agent")
    public ApiResponse<AiManagedAgent> createAgentFromTemplate(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String name = body == null ? null : body.get("name");
        return ApiResponse.ok(collabService.createAgentFromTemplate(id, name, currentUserId(), "default"));
    }

    private String currentUserId() {
        try {
            if (StpUtil.isLogin()) {
                return String.valueOf(StpUtil.getLoginIdAsLong());
            }
        } catch (Exception ignored) {
        }
        return "u001";
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
