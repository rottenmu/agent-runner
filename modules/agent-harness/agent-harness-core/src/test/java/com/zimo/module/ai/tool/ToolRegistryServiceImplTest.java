package com.zimo.module.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.common.security.SecurityFacade;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 工具治理服务单测：验证权限拦截、执行转交与审计旁路三条关键语义。
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
class ToolRegistryServiceImplTest {

    /** 构造被测服务（安全门面可定制）。 */
    @SuppressWarnings("unchecked")
    private ToolRegistryServiceImpl service(ToolMetaMapper metaMapper, GraphToolInvokeLogMapper logMapper,
                                            SecurityFacade facade) {
        ObjectProvider<SecurityFacade> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(facade);
        return new ToolRegistryServiceImpl(metaMapper, logMapper,
                List.of(new LocalToolProvider()), provider, new ObjectMapper());
    }

    /** 构造工具元数据。 */
    private ToolMeta meta(String toolId, String tenantId, String ref, String permission) {
        ToolMeta meta = new ToolMeta();
        meta.setToolId(toolId);
        meta.setTenantId(tenantId);
        meta.setProviderType(LocalToolProvider.TYPE);
        meta.setProviderRef(ref);
        meta.setRequiredPermission(permission);
        meta.setEnabled(true);
        return meta;
    }

    @Test
    void executesThroughLocalProviderAndWritesSuccessAudit() {
        ToolMetaMapper metaMapper = mock(ToolMetaMapper.class);
        GraphToolInvokeLogMapper logMapper = mock(GraphToolInvokeLogMapper.class);
        when(metaMapper.selectOne(any())).thenReturn(meta("t_upper", "u001", "upper", null));

        ToolRegistryService.ToolCallResult result = service(metaMapper, logMapper, null).invoke(
                new ToolRegistryService.ToolInvokeRequest("t_upper", "u001", "admin",
                        Map.of("text", "abc"), null, "n1"));

        assertTrue(result.success(), "内置 upper 工具应执行成功");
        assertEquals("ABC", result.content());
        verify(logMapper).insert(any(GraphToolInvokeLog.class));
    }

    @Test
    void guardsWhenPermissionDenied() {
        ToolMetaMapper metaMapper = mock(ToolMetaMapper.class);
        GraphToolInvokeLogMapper logMapper = mock(GraphToolInvokeLogMapper.class);
        when(metaMapper.selectOne(any())).thenReturn(meta("t_secret", "u001", "echo", "tool:invoke"));

        SecurityFacade facade = mock(SecurityFacade.class);
        when(facade.canAccess(eq("tool"), eq("t_secret"), eq("tool:invoke"))).thenReturn(false);

        ToolRegistryService.ToolCallResult result = service(metaMapper, logMapper, facade).invoke(
                new ToolRegistryService.ToolInvokeRequest("t_secret", "u001", "admin",
                        Map.of("text", "x"), null, "n1"));

        assertFalse(result.success(), "无权限时不得执行");
        assertTrue(result.guarded(), "无权限应标记为 guarded 而不是普通失败");
        // 被拦截同样要留痕：否则"谁试图越权"在审计里完全不可见
        verify(logMapper).insert(any(GraphToolInvokeLog.class));
    }

    @Test
    void guardsCrossTenantCall() {
        ToolMetaMapper metaMapper = mock(ToolMetaMapper.class);
        GraphToolInvokeLogMapper logMapper = mock(GraphToolInvokeLogMapper.class);
        // 第一次查询（本租户）返回空，第二次（跨租户）返回他租户的同名工具
        when(metaMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(meta("t_shared", "tenant-b", "echo", null));

        ToolRegistryService.ToolCallResult result = service(metaMapper, logMapper, null).invoke(
                new ToolRegistryService.ToolInvokeRequest("t_shared", "tenant-a", "admin",
                        Map.of("text", "x"), null, null));

        assertTrue(result.guarded(), "跨租户调用必须按拦截处理");
        assertTrue(result.error().contains("tenant-b"), "拦截原因应指明归属租户: " + result.error());
    }

    @Test
    void auditFailureDoesNotBreakInvocation() {
        ToolMetaMapper metaMapper = mock(ToolMetaMapper.class);
        GraphToolInvokeLogMapper logMapper = mock(GraphToolInvokeLogMapper.class);
        when(metaMapper.selectOne(any())).thenReturn(meta("t_echo", "u001", "echo", null));
        when(logMapper.insert(any(GraphToolInvokeLog.class))).thenThrow(new IllegalStateException("db down"));

        ToolRegistryService.ToolCallResult result = service(metaMapper, logMapper, null).invoke(
                new ToolRegistryService.ToolInvokeRequest("t_echo", "u001", "admin",
                        Map.of("text", "hi"), null, null));

        assertTrue(result.success(), "审计写失败不得影响工具调用结果");
        assertEquals("hi", result.content());
    }

    @Test
    void missingToolDoesNotWriteAudit() {
        ToolMetaMapper metaMapper = mock(ToolMetaMapper.class);
        GraphToolInvokeLogMapper logMapper = mock(GraphToolInvokeLogMapper.class);
        when(metaMapper.selectOne(any())).thenReturn(null).thenReturn(null);

        ToolRegistryService.ToolCallResult result = service(metaMapper, logMapper, null).invoke(
                new ToolRegistryService.ToolInvokeRequest("nope", "u001", "admin", Map.of(), null, null));

        assertFalse(result.success());
        assertFalse(result.guarded(), "工具不存在属配置问题，不应被当成权限拦截");
        verify(logMapper, never()).insert(any(GraphToolInvokeLog.class));
    }
}
