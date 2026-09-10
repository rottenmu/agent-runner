package com.zimo.module.security.autoconfig;

import com.zimo.framework.common.security.NoopSecurityFacade;
import com.zimo.framework.common.security.SecurityFacade;
import com.zimo.module.auth.mapper.SysUserMapper;
import com.zimo.module.auth.security.AuthSessionService;
import com.zimo.module.auth.service.SysRbacService;
import com.zimo.module.security.service.ApprovalService;
import com.zimo.module.security.service.AuditLogService;
import com.zimo.module.security.service.ContentSafetyService;
import com.zimo.module.security.service.DataScopeService;
import com.zimo.module.security.service.OrgSyncService;
import com.zimo.module.security.service.ResourcePermissionService;
import com.zimo.module.security.mapper.SecApprovalMapper;
import com.zimo.module.security.mapper.SecAuditLogMapper;
import com.zimo.module.security.mapper.SecDangerousApiMapper;
import com.zimo.module.security.mapper.SecDataScopeMapper;
import com.zimo.module.security.mapper.SecDeptMapper;
import com.zimo.module.security.mapper.SecResourcePermissionMapper;
import com.zimo.module.security.mapper.SecSensitiveWordMapper;
import com.zimo.module.security.mapper.SecUserDeptMapper;
import com.zimo.module.security.service.SecurityFacadeImpl;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 安全中心自动装配：细粒度 RBAC、组织架构、审计日志、内容安全、操作审批。
 *
 * @author WorkBuddy
 * @since 2026-08-10
 */
@AutoConfiguration
@MapperScan("com.zimo.module.security.mapper")
public class SecurityAutoConfiguration {

    /** 组织架构同步服务（启动初始化种子部门）。 */
    @Bean
    @ConditionalOnMissingBean
    public OrgSyncService orgSyncService(SecDeptMapper deptMapper, SecUserDeptMapper userDeptMapper) {
        OrgSyncService service = new OrgSyncService(deptMapper, userDeptMapper);
        service.initSeed();
        return service;
    }

    /** 审计日志服务（哈希链防篡改）。 */
    @Bean
    @ConditionalOnMissingBean
    public AuditLogService auditLogService(SecAuditLogMapper auditLogMapper) {
        return new AuditLogService(auditLogMapper);
    }

    /** 细粒度资源权限服务。 */
    @Bean
    @ConditionalOnMissingBean
    public ResourcePermissionService resourcePermissionService(
            SecResourcePermissionMapper permissionMapper,
            SecUserDeptMapper userDeptMapper,
            SysRbacService rbacService) {
        return new ResourcePermissionService(permissionMapper, userDeptMapper, rbacService);
    }

    /** 行级数据权限服务（bean 名加前缀避免与 module-sys 冲突）。 */
    @Bean
    @ConditionalOnMissingBean
    public DataScopeService securityDataScopeService(SecDataScopeMapper dataScopeMapper,
                                                     SecUserDeptMapper userDeptMapper,
                                                     SysRbacService rbacService) {
        return new DataScopeService(dataScopeMapper, userDeptMapper, rbacService);
    }

    /** 内容安全服务。 */
    @Bean
    @ConditionalOnMissingBean
    public ContentSafetyService contentSafetyService(
            SecSensitiveWordMapper wordMapper,
            SecDangerousApiMapper apiMapper,
            AuthSessionService sessionService) {
        return new ContentSafetyService(wordMapper, apiMapper, sessionService);
    }

    /** 操作审批服务。 */
    @Bean
    @ConditionalOnMissingBean
    public ApprovalService approvalService(SecApprovalMapper approvalMapper,
                                           AuditLogService auditLogService) {
        return new ApprovalService(approvalMapper, auditLogService);
    }

    /** 安全门面（业务模块 Optional 注入）。 */
    @Bean
    @ConditionalOnMissingBean(SecurityFacade.class)
    public SecurityFacade securityFacade(AuditLogService auditLogService,
                                         ResourcePermissionService permissionService,
                                         ContentSafetyService safetyService,
                                         ApprovalService approvalService,
                                         AuthSessionService sessionService) {
        SecurityFacadeImpl facade = new SecurityFacadeImpl(
                auditLogService, permissionService, safetyService, approvalService, sessionService);
        // 审批通过后执行回调（当前为审计记录；真实业务动作由审批单 payload 驱动）
        approvalService.setCallback((approval, approved) -> {
            auditLogService.record("system", "approval-executor", "approval.execute", "approval",
                    String.valueOf(approval.getId()), "执行审批动作: " + approval.getActionType(),
                    "success", "", "");
        });
        return facade;
    }
}
