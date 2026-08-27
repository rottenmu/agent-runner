package com.zimo.module.security.controller;

import com.zimo.module.security.entity.SecApproval;
import com.zimo.module.security.entity.SecAuditLog;
import com.zimo.module.security.entity.SecDangerousApi;
import com.zimo.module.security.entity.SecDataScope;
import com.zimo.module.security.entity.SecDept;
import com.zimo.module.security.entity.SecResourcePermission;
import com.zimo.module.security.entity.SecSensitiveWord;
import com.zimo.module.security.entity.SecUserDept;
import com.zimo.module.security.service.ApprovalService;
import com.zimo.module.security.service.AuditLogService;
import com.zimo.module.security.service.ContentSafetyService;
import com.zimo.module.security.service.DataScopeService;
import com.zimo.module.security.service.OrgSyncService;
import com.zimo.module.security.service.ResourcePermissionService;
import cn.dev33.satoken.stp.StpUtil;
import com.zimo.framework.common.ApiResponse;
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
 * 安全中心控制器：组织架构 / 资源权限 / 数据权限 / 审计日志 / 内容安全 / 操作审批。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
@RestController
@RequestMapping("/api/biz/security")
public class SecurityController {

    private final OrgSyncService orgService;
    private final ResourcePermissionService permissionService;
    private final DataScopeService dataScopeService;
    private final AuditLogService auditLogService;
    private final ContentSafetyService safetyService;
    private final ApprovalService approvalService;

    public SecurityController(OrgSyncService orgService,
                              ResourcePermissionService permissionService,
                              DataScopeService dataScopeService,
                              AuditLogService auditLogService,
                              ContentSafetyService safetyService,
                              ApprovalService approvalService) {
        this.orgService = orgService;
        this.permissionService = permissionService;
        this.dataScopeService = dataScopeService;
        this.auditLogService = auditLogService;
        this.safetyService = safetyService;
        this.approvalService = approvalService;
    }

    /* ---------------- 组织架构 ---------------- */

    /** 部门树。 */
    @GetMapping("/org/tree")
    public ApiResponse<List<Map<String, Object>>> orgTree() {
        return ApiResponse.ok(orgService.tree());
    }

    /** 人员部门归属。 */
    @GetMapping("/org/members")
    public ApiResponse<List<Map<String, Object>>> orgMembers() {
        return ApiResponse.ok(orgService.members());
    }

    /** 新增部门。 */
    @PostMapping("/org/depts")
    public ApiResponse<SecDept> createDept(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(orgService.upsert(
                str(body.get("name")),
                body.get("parentId") == null ? null : Long.valueOf(String.valueOf(body.get("parentId"))),
                body.get("sort") == null ? null : Integer.valueOf(String.valueOf(body.get("sort")))));
    }

    /** 挂载用户到部门。 */
    @PostMapping("/org/bind")
    public ApiResponse<SecUserDept> bindUser(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(orgService.bindUser(
                Long.valueOf(String.valueOf(body.get("userId"))),
                Long.valueOf(String.valueOf(body.get("deptId"))),
                str(body.get("position")),
                !Boolean.FALSE.equals(body.get("isPrimary"))));
    }

    /** 手动同步组织架构。 */
    @PostMapping("/org/sync")
    public ApiResponse<Integer> syncOrg(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> departments = (List<Map<String, Object>>) body.getOrDefault("departments", List.of());
        return ApiResponse.ok(orgService.sync(departments));
    }

    /** 删除部门。 */
    @DeleteMapping("/org/depts/{id}")
    public ApiResponse<Void> deleteDept(@PathVariable Long id) {
        orgService.deleteDept(id);
        return ApiResponse.ok(null);
    }

    /* ---------------- 资源权限 ---------------- */

    /** 权限列表。 */
    @GetMapping("/permissions")
    public ApiResponse<List<SecResourcePermission>> listPermissions(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String principalType) {
        return ApiResponse.ok(permissionService.list(resourceType, principalType));
    }

    /** 授予权限。 */
    @PostMapping("/permissions")
    public ApiResponse<SecResourcePermission> grantPermission(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(permissionService.grant(
                str(body.get("principalType")),
                str(body.get("principalId")),
                str(body.get("resourceType")),
                str(body.get("resourceId")),
                str(body.get("action"))));
    }

    /** 撤销权限。 */
    @DeleteMapping("/permissions/{id}")
    public ApiResponse<Void> revokePermission(@PathVariable Long id) {
        permissionService.revoke(id);
        return ApiResponse.ok(null);
    }

    /** 当前用户权限校验（供前端/调试）。 */
    @GetMapping("/permissions/check")
    public ApiResponse<Boolean> checkPermission(
            @RequestParam String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(defaultValue = "view") String action) {
        return ApiResponse.ok(permissionService.check(
                StpUtil.getLoginIdAsString(), StpUtil.getLoginIdAsLong(), resourceType, resourceId, action));
    }

    /* ---------------- 行级数据权限 ---------------- */

    /** 数据范围配置列表。 */
    @GetMapping("/data-scopes")
    public ApiResponse<List<SecDataScope>> listDataScopes(@RequestParam(required = false) String resourceType) {
        return ApiResponse.ok(dataScopeService.list(resourceType));
    }

    /** 配置数据范围。 */
    @PostMapping("/data-scopes")
    public ApiResponse<SecDataScope> setDataScope(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(dataScopeService.setScope(
                str(body.get("principalType")),
                str(body.get("principalId")),
                str(body.get("resourceType")),
                str(body.get("scope"))));
    }

    /** 删除数据范围配置。 */
    @DeleteMapping("/data-scopes/{id}")
    public ApiResponse<Void> deleteDataScope(@PathVariable Long id) {
        dataScopeService.delete(id);
        return ApiResponse.ok(null);
    }

    /** 解析当前用户数据范围。 */
    @GetMapping("/data-scopes/resolve")
    public ApiResponse<Map<String, Object>> resolveScope(@RequestParam(defaultValue = "data") String resourceType) {
        return ApiResponse.ok(dataScopeService.resolve(StpUtil.getLoginIdAsLong(), resourceType));
    }

    /* ---------------- 审计日志 ---------------- */

    /** 审计日志列表。 */
    @GetMapping("/audit-logs")
    public ApiResponse<List<SecAuditLog>> listAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(auditLogService.listLogs(action, actorId, limit));
    }

    /** 审计链完整性校验（防篡改验证）。 */
    @GetMapping("/audit-logs/verify")
    public ApiResponse<List<Map<String, Object>>> verifyAuditChain() {
        return ApiResponse.ok(auditLogService.verifyChain());
    }

    /* ---------------- 内容安全 ---------------- */

    /** 敏感词列表。 */
    @GetMapping("/words")
    public ApiResponse<List<SecSensitiveWord>> listWords() {
        return ApiResponse.ok(safetyService.listWords());
    }

    /** 新增敏感词。 */
    @PostMapping("/words")
    public ApiResponse<SecSensitiveWord> addWord(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(safetyService.addWord(str(body.get("word")), str(body.get("category"))));
    }

    /** 删除敏感词。 */
    @DeleteMapping("/words/{id}")
    public ApiResponse<Void> deleteWord(@PathVariable Long id) {
        safetyService.deleteWord(id);
        return ApiResponse.ok(null);
    }

    /** 高危接口列表。 */
    @GetMapping("/dangerous-apis")
    public ApiResponse<List<SecDangerousApi>> listDangerousApis() {
        return ApiResponse.ok(safetyService.listApis());
    }

    /** 新增高危接口。 */
    @PostMapping("/dangerous-apis")
    public ApiResponse<SecDangerousApi> addDangerousApi(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(safetyService.addApi(
                str(body.get("pattern")), str(body.get("method")), str(body.get("description"))));
    }

    /** 删除高危接口。 */
    @DeleteMapping("/dangerous-apis/{id}")
    public ApiResponse<Void> deleteDangerousApi(@PathVariable Long id) {
        safetyService.deleteApi(id);
        return ApiResponse.ok(null);
    }

    /** 文本安全检测（调试/前端）。 */
    @PostMapping("/check-text")
    public ApiResponse<Map<String, Object>> checkText(@RequestBody Map<String, Object> body) {
        String text = str(body.get("text"));
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("blocked", safetyService.interceptInput(text));
        result.put("sanitized", safetyService.sanitizeOutput(text));
        result.put("hallucination", safetyService.detectHallucination(text,
                body.get("sourceCount") == null ? 0 : Integer.parseInt(String.valueOf(body.get("sourceCount")))));
        return ApiResponse.ok(result);
    }

    /* ---------------- 操作审批 ---------------- */

    /** 审批单列表。 */
    @GetMapping("/approvals")
    public ApiResponse<List<SecApproval>> listApprovals(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String actionType,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(approvalService.list(status, actionType, limit));
    }

    /** 发起审批。 */
    @PostMapping("/approvals")
    public ApiResponse<SecApproval> createApproval(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) body.getOrDefault("payload", Map.of());
        return ApiResponse.ok(approvalService.create(
                str(body.get("actionType")),
                str(body.get("objectType")),
                str(body.get("objectId")),
                payload,
                str(body.get("requesterId"))));
    }

    /** 审批处理（通过执行 / 驳回）。 */
    @PostMapping("/approvals/{id}/handle")
    public ApiResponse<SecApproval> handleApproval(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(approvalService.handle(
                id,
                !Boolean.FALSE.equals(body.get("approved")),
                str(body.get("comment")),
                str(body.get("approverId"))));
    }

    /** 审批详情。 */
    @GetMapping("/approvals/{id}")
    public ApiResponse<SecApproval> approvalDetail(@PathVariable Long id) {
        return ApiResponse.ok(approvalService.detail(id));
    }

    private static String str(Object value) {
        // hutool Convert.toStr：null 安全转换，避免手写判空
        return cn.hutool.core.convert.Convert.toStr(value, "");
    }
}
