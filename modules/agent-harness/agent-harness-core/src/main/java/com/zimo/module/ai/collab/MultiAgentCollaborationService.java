package com.zimo.module.ai.collab;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zimo.framework.common.validation.ValidationUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.ai.AiAgentReply;
import com.zimo.framework.ai.AiAgentService;
import com.zimo.framework.ai.agent.AiAgentProfile;
import com.zimo.framework.ai.agent.AiAgentRouteRequest;
import com.zimo.module.ai.management.AiAgentManagementService;
import com.zimo.module.ai.management.AiManagedAgent;
import com.zimo.module.ai.management.AiManagedAgentRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 多智能体协同编排服务：群组会话、任务分发/转交/委派、协商消息、上下级审批、子任务回调、冲突处理。
 *
 * <p>任务状态机：pending → assigned → in_progress → submitted（需审批）→ approved/rejected
 * 或 → done；冲突时进入 conflict 由人工解决。Agent 任务通过现有智能体路由自动执行。</p>
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
public class MultiAgentCollaborationService {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentCollaborationService.class);
    private static final String CHANNEL = "collab";

    /* 任务状态 */
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ASSIGNED = "assigned";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_SUBMITTED = "submitted";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CONFLICT = "conflict";
    public static final String STATUS_CANCELLED = "cancelled";

    private final AgentSessionMapper sessionMapper;
    private final AgentSessionMemberMapper memberMapper;
    private final AgentTaskMapper taskMapper;
    private final AgentTaskFlowMapper flowMapper;
    private final AgentSessionMessageMapper messageMapper;
    private final AgentRoleTemplateMapper templateMapper;
    private final AiAgentManagementService agentManagement;
    private final AiAgentService aiAgentService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public MultiAgentCollaborationService(
            AgentSessionMapper sessionMapper,
            AgentSessionMemberMapper memberMapper,
            AgentTaskMapper taskMapper,
            AgentTaskFlowMapper flowMapper,
            AgentSessionMessageMapper messageMapper,
            AgentRoleTemplateMapper templateMapper,
            AiAgentManagementService agentManagement,
            AiAgentService aiAgentService,
            ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.memberMapper = memberMapper;
        this.taskMapper = taskMapper;
        this.flowMapper = flowMapper;
        this.messageMapper = messageMapper;
        this.templateMapper = templateMapper;
        this.agentManagement = agentManagement;
        this.aiAgentService = aiAgentService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /* ================= 会话 ================= */

    /** 创建协作会话。 */
    public AgentSession createSession(String name, String type, String ownerId) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("会话名称不能为空");
        }
        AgentSession session = new AgentSession();
        session.setName(name.trim());
        session.setType(StringUtils.hasText(type) ? type : "project");
        session.setStatus("active");
        session.setOwnerId(ownerId);
        LocalDateTime now = LocalDateTime.now();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionMapper.insert(session);
        return session;
    }

    /** 添加成员（agent 或 user）。 */
    public AgentSessionMember addMember(Long sessionId, String memberType, String memberId, String role) {
        requireSession(sessionId);
        if (!StringUtils.hasText(memberId)) {
            throw new IllegalArgumentException("成员标识不能为空");
        }
        AgentSessionMember member = new AgentSessionMember();
        member.setSessionId(sessionId);
        member.setMemberType(StringUtils.hasText(memberType) ? memberType : "agent");
        member.setMemberId(memberId.trim());
        member.setRole(StringUtils.hasText(role) ? role : "participant");
        member.setCreatedAt(LocalDateTime.now());
        memberMapper.insert(member);
        return member;
    }

    /** 移除成员。 */
    public boolean removeMember(Long memberId) {
        return memberMapper.deleteById(memberId) > 0;
    }

    /** 会话列表（含成员数）。 */
    public List<Map<String, Object>> listSessions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AgentSession session : sessionMapper.selectList(Wrappers.<AgentSession>lambdaQuery()
                .orderByDesc(AgentSession::getId))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", session.getId());
            item.put("name", session.getName());
            item.put("type", session.getType());
            item.put("status", session.getStatus());
            item.put("ownerId", session.getOwnerId());
            item.put("createdAt", session.getCreatedAt());
            item.put("memberCount", memberMapper.selectCount(Wrappers.<AgentSessionMember>lambdaQuery()
                    .eq(AgentSessionMember::getSessionId, session.getId())));
            item.put("taskCount", taskMapper.selectCount(Wrappers.<AgentTask>lambdaQuery()
                    .eq(AgentTask::getSessionId, session.getId())));
            result.add(item);
        }
        return result;
    }

    /** 会话详情（含成员）。 */
    public Map<String, Object> sessionDetail(Long sessionId) {
        AgentSession session = requireSession(sessionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", session.getId());
        result.put("name", session.getName());
        result.put("type", session.getType());
        result.put("status", session.getStatus());
        result.put("ownerId", session.getOwnerId());
        result.put("createdAt", session.getCreatedAt());
        result.put("members", listMembers(sessionId));
        return result;
    }

    /** 会话成员。 */
    public List<AgentSessionMember> listMembers(Long sessionId) {
        return memberMapper.selectList(Wrappers.<AgentSessionMember>lambdaQuery()
                .eq(AgentSessionMember::getSessionId, sessionId)
                .orderByAsc(AgentSessionMember::getId));
    }

    /* ================= 消息（群组会话/协商沟通） ================= */

    /** 发送消息（人机协同/协商沟通）。 */
    public AgentSessionMessage sendMessage(Long sessionId, String senderType, String senderId,
                                           String senderName, String contentType, String content, Long taskId) {
        requireSession(sessionId);
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        AgentSessionMessage message = new AgentSessionMessage();
        message.setSessionId(sessionId);
        message.setSenderType(StringUtils.hasText(senderType) ? senderType : "user");
        message.setSenderId(senderId);
        message.setSenderName(StringUtils.hasText(senderName) ? senderName : senderId);
        message.setContentType(StringUtils.hasText(contentType) ? contentType : "text");
        message.setContent(content);
        message.setTaskId(taskId);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        // 若消息发送者是用户且会话内存在默认 Agent，可将消息转为协商（不自动回复，由前端触发任务）
        return message;
    }

    /** 会话消息流。 */
    public List<AgentSessionMessage> listMessages(Long sessionId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return messageMapper.selectList(Wrappers.<AgentSessionMessage>lambdaQuery()
                .eq(AgentSessionMessage::getSessionId, sessionId)
                .orderByAsc(AgentSessionMessage::getId)
                .last("LIMIT " + safeLimit));
    }

    /* ================= 任务 ================= */

    /** 创建任务并分发（assignee 为 Agent 时自动异步执行）。 */
    public AgentTask createTask(Map<String, Object> params) {
        String sessionIdStr = str(params.get("sessionId"));
        if (!StringUtils.hasText(sessionIdStr)) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        Long sessionId = Long.valueOf(sessionIdStr);
        requireSession(sessionId);
        AgentTask task = new AgentTask();
        task.setSessionId(sessionId);
        task.setTitle(str(params.get("title")));
        task.setDescription(str(params.get("description")));
        task.setAssigneeType(str(params.get("assigneeType")).isBlank() ? "agent" : str(params.get("assigneeType")));
        task.setAssigneeId(str(params.get("assigneeId")));
        task.setCreatorType(str(params.get("creatorType")).isBlank() ? "user" : str(params.get("creatorType")));
        task.setCreatorId(str(params.get("creatorId")));
        Object parentId = params.get("parentTaskId");
        task.setParentTaskId(parentId == null || String.valueOf(parentId).isBlank() ? null : Long.valueOf(String.valueOf(parentId)));
        task.setStatus(STATUS_PENDING);
        task.setPriority(str(params.get("priority")).isBlank() ? "normal" : str(params.get("priority")));
        task.setDeadline(str(params.get("deadline")));
        task.setNeedsApproval(params.get("needsApproval") == null
                ? false : Boolean.parseBoolean(String.valueOf(params.get("needsApproval"))));
        task.setApproverType(str(params.get("approverType")).isBlank() ? "user" : str(params.get("approverType")));
        task.setApproverId(str(params.get("approverId")));
        task.setCallbackUrl(str(params.get("callbackUrl")));
        task.setConflictFlag(false);
        LocalDateTime now = LocalDateTime.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        recordFlow(task.getId(), null, STATUS_PENDING, "create", str(params.get("creatorType")), str(params.get("creatorId")),
                str(params.get("title")));
        if (StringUtils.hasText(task.getAssigneeId())) {
            dispatch(task.getId());
        }
        return task;
    }

    /** 任务列表。 */
    public List<AgentTask> listTasks(Long sessionId) {
        return taskMapper.selectList(Wrappers.<AgentTask>lambdaQuery()
                .eq(AgentTask::getSessionId, sessionId)
                .orderByDesc(AgentTask::getId));
    }

    /** 任务详情。 */
    public Map<String, Object> taskDetail(Long taskId) {
        AgentTask task = requireTask(taskId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task", task);
        result.put("flow", listTaskFlow(taskId));
        return result;
    }

    /** 任务流转记录。 */
    public List<AgentTaskFlow> listTaskFlow(Long taskId) {
        return flowMapper.selectList(Wrappers.<AgentTaskFlow>lambdaQuery()
                .eq(AgentTaskFlow::getTaskId, taskId)
                .orderByAsc(AgentTaskFlow::getId));
    }

    /* -------- 任务执行引擎 -------- */

    /** 分发任务：指派给 Agent 自动执行，或指派给用户等待人工。 */
    public void dispatch(Long taskId) {
        AgentTask task = requireTask(taskId);
        changeStatus(task, STATUS_ASSIGNED, "dispatch",
                task.getCreatorType(), task.getCreatorId(), "任务已分发至 " + task.getAssigneeId());
        if ("agent".equals(task.getAssigneeType())) {
            changeStatus(task, STATUS_IN_PROGRESS, "assign",
                    "system", task.getAssigneeId(), "智能体 " + task.getAssigneeId() + " 开始执行");
            CompletableFuture.runAsync(() -> executeTask(taskId));
        } else {
            sendSystemMessage(task.getSessionId(), "任务已分发至用户 " + task.getAssigneeId() + "：「" + task.getTitle() + "」", taskId);
        }
    }

    /** 执行任务（Agent 执行并回填结果）。 */
    public Map<String, Object> executeTask(Long taskId) {
        AgentTask task = requireTask(taskId);
        if (!"agent".equals(task.getAssigneeType())) {
            throw new IllegalArgumentException("任务未指派给智能体");
        }
        try {
            AiManagedAgent agent = agentManagement.findEnabledAgentById(task.getAssigneeId())
                    .orElseThrow(() -> new IllegalArgumentException("智能体不存在或未启用: " + task.getAssigneeId()));
            String prompt = buildTaskPrompt(task);
            AiAgentReply reply = aiAgentService.chat(prompt, routeRequest(agent, task));
            String result = reply == null ? "" : reply.content();
            task.setResult(truncate(result, 6000));
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            if (StrUtil.isBlank(result) || result.startsWith("AI 智能体调用失败")) {
                changeStatus(task, STATUS_FAILED, "execute", "agent", task.getAssigneeId(), "智能体执行失败: " + result);
                sendSystemMessage(task.getSessionId(), "任务「" + task.getTitle() + "」执行失败：" + result, taskId);
                return Map.of("taskId", taskId, "status", STATUS_FAILED);
            }
            if (Boolean.TRUE.equals(task.getNeedsApproval())) {
                changeStatus(task, STATUS_SUBMITTED, "submit", "agent", task.getAssigneeId(), "执行完成，等待审批");
                sendSystemMessage(task.getSessionId(),
                        "任务「" + task.getTitle() + "」已提交审批（审批人: " + task.getApproverId() + "）", taskId);
            } else {
                changeStatus(task, STATUS_DONE, "submit", "agent", task.getAssigneeId(), "执行完成");
                sendSystemMessage(task.getSessionId(), "任务「" + task.getTitle() + "」已完成", taskId);
                afterComplete(task);
            }
            return Map.of("taskId", taskId, "status", task.getStatus(), "result", truncate(result, 500));
        } catch (Exception e) {
            task.setResult("执行异常: " + safeMessage(e));
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            changeStatus(task, STATUS_FAILED, "execute", "system", null, safeMessage(e));
            return Map.of("taskId", taskId, "status", STATUS_FAILED, "error", safeMessage(e));
        }
    }

    /** 构建任务执行提示词（含会话上下文）。 */
    private String buildTaskPrompt(AgentTask task) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("【协作任务】\n");
        prompt.append("标题：").append(task.getTitle()).append("\n");
        if (StringUtils.hasText(task.getDescription())) {
            prompt.append("描述：").append(task.getDescription()).append("\n");
        }
        if (StringUtils.hasText(task.getDeadline())) {
            prompt.append("截止：").append(task.getDeadline()).append("\n");
        }
        if (task.getParentTaskId() != null) {
            AgentTask parent = taskMapper.selectById(task.getParentTaskId());
            if (parent != null) {
                prompt.append("所属父任务：").append(parent.getTitle()).append("\n");
            }
        }
        List<AgentSessionMessage> recent = listMessages(task.getSessionId(), 6);
        if (!recent.isEmpty()) {
            prompt.append("\n【会话上下文】\n");
            for (AgentSessionMessage message : recent) {
                prompt.append(message.getSenderName()).append(": ")
                        .append(truncate(message.getContent(), 300)).append("\n");
            }
        }
        prompt.append("\n请完成上述任务并输出明确的结果。");
        return prompt.toString();
    }

    /* -------- 审批链（上下级审批） -------- */

    /** 审批任务。 */
    public Map<String, Object> approveTask(Long taskId, String operatorType, String operatorId, boolean approved, String comment) {
        AgentTask task = requireTask(taskId);
        if (!STATUS_SUBMITTED.equals(task.getStatus())) {
            throw new IllegalArgumentException("任务当前状态 " + task.getStatus() + "，不可审批（需为 submitted）");
        }
        if (StringUtils.hasText(task.getApproverId()) && !task.getApproverId().equals(operatorId)) {
            throw new SecurityException("当前用户非该任务的指定审批人: " + task.getApproverId());
        }
        String toStatus = approved ? STATUS_APPROVED : STATUS_REJECTED;
        changeStatus(task, toStatus, approved ? "approve" : "reject", operatorType, operatorId,
                StringUtils.hasText(comment) ? comment : (approved ? "审批通过" : "审批驳回"));
        sendSystemMessage(task.getSessionId(),
                "任务「" + task.getTitle() + "」" + (approved ? "已审批通过" : "被驳回") + (StringUtils.hasText(comment) ? "：" + comment : ""),
                taskId);
        if (approved) {
            afterComplete(task);
        } else {
            // 驳回通知发起人
            sendSystemMessage(task.getSessionId(),
                    "任务「" + task.getTitle() + "」被驳回，请处理。", taskId);
        }
        return Map.of("taskId", taskId, "status", toStatus);
    }

    /* -------- 转交 / 委派 -------- */

    /** 智能体转交：更换执行者并重新执行。 */
    public AgentTask handoff(Long taskId, String newAssigneeType, String newAssigneeId) {
        AgentTask task = requireTask(taskId);
        if (!StringUtils.hasText(newAssigneeId)) {
            throw new IllegalArgumentException("新执行者不能为空");
        }
        String from = task.getAssigneeId();
        task.setAssigneeType(StringUtils.hasText(newAssigneeType) ? newAssigneeType : "agent");
        task.setAssigneeId(newAssigneeId.trim());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        changeStatus(task, STATUS_ASSIGNED, "handoff", "system", null, "任务由 " + from + " 转交至 " + newAssigneeId);
        sendSystemMessage(task.getSessionId(), "任务「" + task.getTitle() + "」由 " + from + " 转交至 " + newAssigneeId, taskId);
        if ("agent".equals(task.getAssigneeType())) {
            CompletableFuture.runAsync(() -> executeTask(taskId));
        }
        return task;
    }

    /** 任务委派：创建子任务给被委派者，父任务等待子任务回调。 */
    public AgentTask delegate(Long taskId, String delegateType, String delegateId, String instruction) {
        AgentTask task = requireTask(taskId);
        if (!StringUtils.hasText(delegateId)) {
            throw new IllegalArgumentException("被委派者不能为空");
        }
        Map<String, Object> subTaskParams = new LinkedHashMap<>();
        subTaskParams.put("sessionId", task.getSessionId());
        subTaskParams.put("title", "【委派】" + task.getTitle() + " - " + delegateId);
        subTaskParams.put("description", StringUtils.hasText(instruction) ? instruction : task.getDescription());
        subTaskParams.put("assigneeType", delegateType == null ? "agent" : delegateType);
        subTaskParams.put("assigneeId", delegateId);
        subTaskParams.put("creatorType", "user");
        subTaskParams.put("creatorId", task.getCreatorId());
        subTaskParams.put("parentTaskId", taskId);
        subTaskParams.put("needsApproval", false);
        AgentTask subTask = createTask(subTaskParams);
        // 父任务进入 in_progress 等待子任务
        if (!STATUS_IN_PROGRESS.equals(task.getStatus()) && !STATUS_SUBMITTED.equals(task.getStatus())
                && !STATUS_DONE.equals(task.getStatus())) {
            changeStatus(task, STATUS_IN_PROGRESS, "delegate", "user", task.getCreatorId(),
                    "任务委派给 " + delegateId + "，等待子任务完成");
        }
        sendSystemMessage(task.getSessionId(), "任务「" + task.getTitle() + "」已委派给 " + delegateId + "（子任务 #" + subTask.getId() + "）", taskId);
        return subTask;
    }

    /** 子任务回调：子任务完成后更新父任务。 */
    private void subtaskCallback(Long parentTaskId) {
        if (parentTaskId == null) {
            return;
        }
        AgentTask parent = taskMapper.selectById(parentTaskId);
        if (parent == null) {
            return;
        }
        List<AgentTask> children = taskMapper.selectList(Wrappers.<AgentTask>lambdaQuery()
                .eq(AgentTask::getParentTaskId, parentTaskId));
        boolean allDone = children.stream().allMatch(t -> STATUS_DONE.equals(t.getStatus()) || STATUS_APPROVED.equals(t.getStatus()));
        boolean anyFailed = children.stream().anyMatch(t -> STATUS_FAILED.equals(t.getStatus())
                || STATUS_REJECTED.equals(t.getStatus()) || STATUS_CONFLICT.equals(t.getStatus()));
        StringBuilder summary = new StringBuilder("【子任务汇总】\n");
        for (AgentTask child : children) {
            summary.append("- ").append(child.getTitle()).append(" [").append(child.getStatus()).append("]")
                    .append(StringUtils.hasText(child.getResult()) ? "\n  " + truncate(child.getResult(), 200) : "")
                    .append("\n");
        }
        parent.setResult(summary.toString());
        parent.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(parent);
        if (allDone) {
            if (Boolean.TRUE.equals(parent.getNeedsApproval())) {
                changeStatus(parent, STATUS_SUBMITTED, "subtask_callback", "system", null,
                        "全部子任务完成，等待审批");
            } else {
                changeStatus(parent, STATUS_DONE, "subtask_callback", "system", null, "全部子任务完成");
                sendSystemMessage(parent.getSessionId(), "任务「" + parent.getTitle() + "」子任务全部完成", parent.getId());
                afterComplete(parent);
            }
        } else if (anyFailed) {
            changeStatus(parent, STATUS_CONFLICT, "subtask_callback", "system", null,
                    "存在失败/被驳回子任务，需人工处理");
            sendSystemMessage(parent.getSessionId(),
                    "任务「" + parent.getTitle() + "」存在失败或冲突子任务，需人工处理", parent.getId());
        }
    }

    /* -------- 冲突处理 -------- */

    /** 标记任务冲突（如多个 Agent 结果不一致）。 */
    public AgentTask markConflict(Long taskId, String comment) {
        AgentTask task = requireTask(taskId);
        task.setConflictFlag(true);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        changeStatus(task, STATUS_CONFLICT, "conflict", "system", null,
                StringUtils.hasText(comment) ? comment : "检测到结果冲突，需人工处理");
        sendSystemMessage(task.getSessionId(), "任务「" + task.getTitle() + "」出现冲突，需人工处理", taskId);
        return task;
    }

    /** 解决冲突：approved=采纳结果 / rejected=驳回重做 / retry=重新执行。 */
    public Map<String, Object> resolveConflict(Long taskId, String resolution, String comment) {
        AgentTask task = requireTask(taskId);
        if (!STATUS_CONFLICT.equals(task.getStatus())) {
            throw new IllegalArgumentException("任务当前状态 " + task.getStatus() + "，不可解决冲突");
        }
        String toStatus;
        String action = "conflict_resolve";
        switch (resolution == null ? "" : resolution) {
            case "approved" -> {
                toStatus = STATUS_DONE;
                task.setConflictFlag(false);
            }
            case "rejected" -> toStatus = STATUS_REJECTED;
            case "retry" -> {
                toStatus = STATUS_IN_PROGRESS;
                task.setConflictFlag(false);
            }
            default -> throw new IllegalArgumentException("resolution 需为 approved/rejected/retry");
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        changeStatus(task, toStatus, action, "user", task.getCreatorId(),
                StringUtils.hasText(comment) ? comment : "人工处理冲突，结果: " + resolution);
        sendSystemMessage(task.getSessionId(), "任务「" + task.getTitle() + "」冲突已处理（" + resolution + "）", taskId);
        if (STATUS_DONE.equals(toStatus)) {
            afterComplete(task);
        } else if (STATUS_IN_PROGRESS.equals(toStatus) && "agent".equals(task.getAssigneeType())) {
            CompletableFuture.runAsync(() -> executeTask(taskId));
        }
        return Map.of("taskId", taskId, "status", toStatus);
    }

    /* -------- 任务完成回调 -------- */

    /** 任务完成后的统一回调：父任务汇总 + 外部 URL 回调。 */
    private void afterComplete(AgentTask task) {
        if (task.getParentTaskId() != null) {
            subtaskCallback(task.getParentTaskId());
        }
        if (StringUtils.hasText(task.getCallbackUrl())) {
            CompletableFuture.runAsync(() -> {
                try {
                    Map<String, Object> body = Map.of(
                            "taskId", task.getId(),
                            "title", task.getTitle(),
                            "status", task.getStatus(),
                            "result", task.getResult());
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    restTemplate.postForEntity(task.getCallbackUrl(),
                            new HttpEntity<>(body, headers), String.class);
                } catch (Exception e) {
                    log.warn("任务回调失败: {} - {}", task.getCallbackUrl(), safeMessage(e));
                }
            });
        }
    }

    /** 取消任务。 */
    public AgentTask cancelTask(Long taskId, String operatorId) {
        AgentTask task = requireTask(taskId);
        if (STATUS_DONE.equals(task.getStatus()) || STATUS_APPROVED.equals(task.getStatus())) {
            throw new IllegalArgumentException("任务已完成，不可取消");
        }
        changeStatus(task, STATUS_CANCELLED, "cancel", "user", operatorId, "任务已取消");
        return task;
    }

    /* ================= 角色模板 ================= */

    /** 初始化预置角色模板（审批助手/数据查询助手/报表 Agent/合规检测 Agent）。 */
    public void initSeedTemplates() {
        seedTemplate("审批助手", "approval_assistant", "协助上级对任务/单据进行审批决策，输出审批意见与风险提示",
                "conversation",
                "你是企业的审批助手。根据任务描述与上下文，输出审批意见（通过/驳回/补充材料）并说明理由；"
                        + "对高风险内容给出风险提示。保持客观严谨。",
                List.of());
        seedTemplate("数据查询助手", "data_query_assistant", "查询数据库/数据源并整理业务数据",
                "tool",
                "你是数据查询助手。用户需要数据时，优先使用 query_datasource 工具查询数据源，"
                        + "将结果整理为清晰的表格与结论。",
                List.of("query_datasource"));
        seedTemplate("报表 Agent", "report_agent", "生成结构化业务报表与摘要",
                "tool",
                "你是报表 Agent。根据数据源查询结果生成结构化业务报表（统计口径、汇总指标、趋势说明），"
                        + "输出 Markdown 报表。",
                List.of("query_datasource"));
        seedTemplate("合规检测 Agent", "compliance_agent", "对照制度/知识库检测业务合规性",
                "rag",
                "你是合规检测 Agent。使用 rag_retrieve 检索企业制度知识库，对照检测业务内容是否合规，"
                        + "输出合规结论与违规风险点。",
                List.of("rag_retrieve"));
    }

    private void seedTemplate(String name, String code, String description, String agentType,
                              String persona, List<String> skillIds) {
        AgentRoleTemplate existing = templateMapper.selectOne(Wrappers.<AgentRoleTemplate>lambdaQuery()
                .eq(AgentRoleTemplate::getCode, code));
        if (existing != null) {
            return;
        }
        AgentRoleTemplate template = new AgentRoleTemplate();
        template.setName(name);
        template.setCode(code);
        template.setDescription(description);
        template.setAgentType(agentType);
        template.setPersona(persona);
        template.setSkillIds(toJson(skillIds));
        template.setConfigJson("{}");
        template.setCreatedAt(LocalDateTime.now());
        templateMapper.insert(template);
    }

    /** 角色模板列表。 */
    public List<AgentRoleTemplate> listTemplates() {
        return templateMapper.selectList(Wrappers.<AgentRoleTemplate>lambdaQuery()
                .orderByAsc(AgentRoleTemplate::getId));
    }

    /** 从模板创建 Agent（实例化角色）。 */
    public AiManagedAgent createAgentFromTemplate(Long templateId, String name, String userId, String tenantId) {
        AgentRoleTemplate template = templateMapper.selectById(templateId);
        ValidationUtil.requireNotNull(template, "模板不存在: ");
        AiManagedAgentRequest request = new AiManagedAgentRequest();
        request.setName(StringUtils.hasText(name) ? name : template.getName());
        request.setDesc(template.getDescription());
        request.setPersona(template.getPersona());
        request.setAgentType(template.getAgentType());
        request.setSkillIds(parseStringList(template.getSkillIds()));
        request.setUserId(StringUtils.hasText(userId) ? userId : "u001");
        request.setTenantId(StringUtils.hasText(tenantId) ? tenantId : "default");
        request.setEnabled(true);
        return agentManagement.create(request);
    }

    /* ================= 内部工具 ================= */

    private AiAgentRouteRequest routeRequest(AiManagedAgent agent, AgentTask task) {
        AiAgentProfile profile = new AiAgentProfile(
                agent.id(),
                agent.tenantId() == null ? "default" : agent.tenantId(),
                agent.name(),
                agent.model(),
                agent.persona(),
                agent.skillIds(),
                agent.agentType(),
                agent.agentConfig(),
                true);
        String tenantId = agent.tenantId() == null ? "default" : agent.tenantId();
        return new AiAgentRouteRequest(tenantId, CHANNEL,
                StringUtils.hasText(task.getCreatorId()) ? task.getCreatorId() : "collab-user",
                String.valueOf(task.getSessionId()), profile, null);
    }

    private void changeStatus(AgentTask task, String toStatus, String actionType,
                              String operatorType, String operatorId, String comment) {
        String from = task.getStatus();
        task.setStatus(toStatus);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        recordFlow(task.getId(), from, toStatus, actionType, operatorType, operatorId, comment);
    }

    private void recordFlow(Long taskId, String fromStatus, String toStatus, String actionType,
                            String operatorType, String operatorId, String comment) {
        AgentTaskFlow flow = new AgentTaskFlow();
        flow.setTaskId(taskId);
        flow.setFromStatus(fromStatus);
        flow.setToStatus(toStatus);
        flow.setActionType(actionType);
        flow.setOperatorType(operatorType);
        flow.setOperatorId(operatorId);
        flow.setComment(comment);
        flow.setCreatedAt(LocalDateTime.now());
        flowMapper.insert(flow);
    }

    private void sendSystemMessage(Long sessionId, String content, Long taskId) {
        AgentSessionMessage message = new AgentSessionMessage();
        message.setSessionId(sessionId);
        message.setSenderType("system");
        message.setSenderId("system");
        message.setSenderName("系统");
        message.setContentType("text");
        message.setContent(content);
        message.setTaskId(taskId);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }

    private AgentSession requireSession(Long id) {
        AgentSession session = sessionMapper.selectById(id);
        ValidationUtil.requireNotNull(session, "会话不存在: ");
        return session;
    }

    private AgentTask requireTask(Long id) {
        AgentTask task = taskMapper.selectById(id);
        ValidationUtil.requireNotNull(task, "任务不存在: ");
        return task;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) + "…" : value;
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, List.class);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
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
