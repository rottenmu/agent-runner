package com.zimo.module.ai.collab;

import com.zimo.framework.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话 fork/resume 接口：基于追加式事件日志的会话键级分叉与断点续跑。
 *
 * <p>fork：从来源会话（conversationId 或 traceId）重建历史 → 新会话键续跑。
 * resume：重新建会话历史 → 同会话键断点续跑。上下文完全派生自事件日志。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-24
 */
@RestController
@RequestMapping("/api/biz/ai/observ/sessions")
public class SessionForkResumeController {

    private final SessionForkResumeService forkResumeService;

    public SessionForkResumeController(SessionForkResumeService forkResumeService) {
        this.forkResumeService = forkResumeService;
    }

    /** 分叉：从来源会话派生新会话并续跑（body: sourceKey/message/tenantId/userId/agentId）。 */
    @PostMapping("/fork")
    public ApiResponse<Map<String, Object>> fork(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(forkResumeService.fork(
                str(body.get("sourceKey")),
                str(body.get("message")),
                str(body.get("tenantId")),
                str(body.get("userId")),
                str(body.get("agentId"))));
    }

    /** 续跑：从同一会话断点续跑（body: sessionId/message/tenantId/userId/agentId）。 */
    @PostMapping("/resume")
    public ApiResponse<Map<String, Object>> resume(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(forkResumeService.resume(
                str(body.get("sessionId")),
                str(body.get("message")),
                str(body.get("tenantId")),
                str(body.get("userId")),
                str(body.get("agentId"))));
    }

    /** 预览：重建指定会话的对话历史。 */
    @GetMapping("/{sessionId}/context")
    public ApiResponse<Map<String, Object>> preview(@PathVariable String sessionId) {
        return ApiResponse.ok(forkResumeService.preview(sessionId));
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}