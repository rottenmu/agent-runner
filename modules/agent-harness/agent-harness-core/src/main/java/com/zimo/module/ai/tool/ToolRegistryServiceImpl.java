package com.zimo.module.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.framework.common.security.SecurityFacade;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

/**
 * 工具市场治理服务实现。
 *
 * <p>核心职责只有三件：<b>校验谁在调</b>（租户 + 权限）、<b>转交给正确的执行后端</b>、
 * <b>把每一次调用记下来</b>。三件事的顺序不可调换 —— 先审计后执行会在被拦截时
 * 留下一堆"看似调用过"的脏数据，先执行后审计则会在进程崩掉时丢掉最关键的证据。</p>
 *
 * <p>失败处理有一条铁律：<b>审计写失败绝不影响调用结果</b>。审计是旁路，
 * 旁路挂了就记 WARN，不能让一个日志表把工具能力拖垮。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class ToolRegistryServiceImpl implements ToolRegistryService {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistryServiceImpl.class);

    /** 默认租户：与对话链路保持一致（{@code AiChatPreflight} 同口径）。 */
    private static final String DEFAULT_TENANT = "u001";

    /** 审计字段最大长度：超出截断，避免大参数把审计表撑爆。 */
    private static final int AUDIT_TEXT_LIMIT = 2000;

    private final ToolMetaMapper metaMapper;
    private final GraphToolInvokeLogMapper logMapper;
    private final Map<String, ToolProvider> providers;
    private final ObjectProvider<SecurityFacade> security;
    private final ObjectMapper objectMapper;

    /**
     * 构造工具治理服务。
     *
     * @param metaMapper 工具目录 Mapper
     * @param logMapper 调用审计 Mapper
     * @param providers 已装配的后端列表（按 {@link ToolProvider#type()} 收录）
     * @param security 安全门面，允许缺失
     * @param objectMapper JSON 映射器
     */
    public ToolRegistryServiceImpl(ToolMetaMapper metaMapper,
                                   GraphToolInvokeLogMapper logMapper,
                                   List<ToolProvider> providers,
                                   ObjectProvider<SecurityFacade> security,
                                   ObjectMapper objectMapper) {
        this.metaMapper = metaMapper;
        this.logMapper = logMapper;
        this.providers = indexProviders(providers);
        this.security = security;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /** 后端列表索引化：类型 → 实现。同类型重复时后者覆盖并告警。 */
    private static Map<String, ToolProvider> indexProviders(List<ToolProvider> providers) {
        Map<String, ToolProvider> map = new LinkedHashMap<>();
        if (providers == null) {
            return map;
        }
        for (ToolProvider provider : providers) {
            if (provider == null || !StringUtils.hasText(provider.type())) {
                continue;
            }
            ToolProvider previous = map.put(provider.type().trim().toLowerCase(), provider);
            if (previous != null) {
                log.warn("[tool] 后端类型重复，后者覆盖: type={}, overridden={}",
                        provider.type(), previous.getClass().getSimpleName());
            }
        }
        return map;
    }

    /* ==================== 目录维护 ==================== */

    @Override
    public ToolMeta register(ToolMeta meta) {
        if (meta == null || !StringUtils.hasText(meta.getToolId())) {
            throw new IllegalArgumentException("toolId 不能为空");
        }
        if (!StringUtils.hasText(meta.getTenantId())) {
            meta.setTenantId(DEFAULT_TENANT);
        }
        String type = StringUtils.hasText(meta.getProviderType())
                ? meta.getProviderType().trim().toLowerCase() : "";
        if (providers.get(type) == null) {
            throw new IllegalArgumentException("未装配的工具后端类型: " + meta.getProviderType()
                    + "，已装配=" + providers.keySet());
        }
        ToolMeta existing = findMeta(meta.getTenantId(), meta.getToolId());
        return existing == null ? insertMeta(meta) : mergeMeta(existing, meta);
    }

    /** 新增工具元数据。 */
    private ToolMeta insertMeta(ToolMeta meta) {
        LocalDateTime now = LocalDateTime.now();
        meta.setProviderType(meta.getProviderType().trim().toLowerCase());
        meta.setEnabled(meta.getEnabled() == null || meta.getEnabled());
        meta.setDeleted(0);
        meta.setVersion(StringUtils.hasText(meta.getVersion()) ? meta.getVersion() : "1.0.0");
        meta.setCreateTime(now);
        meta.setUpdateTime(now);
        metaMapper.insert(meta);
        return meta;
    }

    /** 覆盖已有工具元数据（幂等注册：重复注册视为版本更新）。 */
    private ToolMeta mergeMeta(ToolMeta existing, ToolMeta patch) {
        existing.setName(patch.getName());
        existing.setDescription(patch.getDescription());
        existing.setProviderType(patch.getProviderType().trim().toLowerCase());
        existing.setProviderRef(patch.getProviderRef());
        existing.setSchemaJson(patch.getSchemaJson());
        existing.setRequiredPermission(patch.getRequiredPermission());
        existing.setVersion(StringUtils.hasText(patch.getVersion()) ? patch.getVersion() : existing.getVersion());
        if (patch.getEnabled() != null) {
            existing.setEnabled(patch.getEnabled());
        }
        existing.setUpdateTime(LocalDateTime.now());
        metaMapper.updateById(existing);
        return existing;
    }

    @Override
    public List<ToolMeta> list(String tenantId, boolean enabledOnly) {
        LambdaQueryWrapper<ToolMeta> query = new LambdaQueryWrapper<ToolMeta>()
                .eq(ToolMeta::getTenantId, blankTo(tenantId, DEFAULT_TENANT))
                .orderByDesc(ToolMeta::getId);
        if (enabledOnly) {
            query.eq(ToolMeta::getEnabled, true);
        }
        List<ToolMeta> list = metaMapper.selectList(query);
        return list == null ? List.of() : list;
    }

    @Override
    public boolean remove(String tenantId, String toolId) {
        ToolMeta existing = findMeta(blankTo(tenantId, DEFAULT_TENANT), toolId);
        if (existing == null) {
            return false;
        }
        metaMapper.deleteById(existing.getId());
        return true;
    }

    @Override
    public List<String> listToolIds(String tenantId) {
        List<String> ids = new ArrayList<>();
        try {
            for (ToolMeta meta : list(tenantId, true)) {
                if (StringUtils.hasText(meta.getToolId())) {
                    ids.add(meta.getToolId());
                }
            }
        } catch (RuntimeException e) {
            // 工具是增强能力：目录读失败不应让对话/图执行失败
            log.warn("[tool] 工具目录读取失败，按空清单继续: {}", e.getMessage());
        }
        return ids;
    }

    /* ==================== 调用与审计 ==================== */

    @Override
    public ToolCallResult invoke(ToolInvokeRequest request) {
        long start = System.currentTimeMillis();
        if (request == null || !StringUtils.hasText(request.toolId())) {
            return ToolCallResult.failed("toolId 不能为空", 0L);
        }
        String tenantId = blankTo(request.tenantId(), DEFAULT_TENANT);
        ToolMeta meta = findMeta(tenantId, request.toolId());
        if (meta == null || Boolean.FALSE.equals(meta.getEnabled())) {
            return handleMissing(request, tenantId, start);
        }
        if (!permitted(meta)) {
            String reason = "无权限调用工具: " + meta.getToolId();
            audit(request, meta, writeJson(request.args()), "", "guarded", reason, elapsed(start));
            return ToolCallResult.guarded(reason);
        }
        return dispatch(request, meta, start);
    }

    /**
     * 工具在本租户不可用时的处理：区分「跨租户越权」与「确实不存在」。
     *
     * <p>为什么不合并成一句"工具不存在"：跨租户调用是<b>安全事件</b>，需要按拦截
     * （guarded）记录并明确告知调用方归属租户；而"不存在"是配置问题。
     * 混在一起会让越权尝试淹没在正常的配置错误里，审计上就看不出来了。</p>
     */
    private ToolCallResult handleMissing(ToolInvokeRequest request, String tenantId, long start) {
        ToolMeta foreign = findMetaAnyTenant(request.toolId());
        if (foreign != null && !tenantId.equals(foreign.getTenantId())) {
            String reason = "跨租户工具不可调用: " + request.toolId()
                    + "，归属租户=" + foreign.getTenantId();
            audit(request, foreign, writeJson(request.args()), "", "guarded", reason, elapsed(start));
            return ToolCallResult.guarded(reason);
        }
        return ToolCallResult.failed("工具不存在或未启用: " + request.toolId(), elapsed(start));
    }

    /** 转交给后端执行并落审计。 */
    private ToolCallResult dispatch(ToolInvokeRequest request, ToolMeta meta, long start) {
        ToolProvider provider = providers.get(meta.getProviderType());
        if (provider == null) {
            String reason = "工具后端未装配: " + meta.getProviderType();
            audit(request, meta, writeJson(request.args()), "", "failed", reason, elapsed(start));
            return ToolCallResult.failed(reason, elapsed(start));
        }
        ToolInvokeResult result;
        try {
            result = provider.invoke(meta, request.args() == null ? Map.of() : request.args());
        } catch (RuntimeException e) {
            result = ToolInvokeResult.fail("工具执行异常: " + e.getMessage());
        }
        long cost = elapsed(start);
        String status = result.success() ? "success" : "failed";
        audit(request, meta, writeJson(request.args()), result.content(), status, result.error(), cost);
        return result.success()
                ? ToolCallResult.ok(result.content(), cost)
                : ToolCallResult.failed(result.error(), cost);
    }

    /**
     * 权限校验。
     *
     * <p>未配置 {@code requiredPermission} 的工具只受租户隔离约束；
     * 安全门面缺失时退化为"不追加校验"，与对话链路对 {@code SecurityFacade} 的空装配口径一致。</p>
     */
    private boolean permitted(ToolMeta meta) {
        String permission = meta.getRequiredPermission();
        if (!StringUtils.hasText(permission)) {
            return true;
        }
        SecurityFacade facade = security == null ? null : security.getIfAvailable();
        if (facade == null) {
            return true;
        }
        return facade.canAccess("tool", meta.getToolId(), permission.trim());
    }

    /** 写调用审计；失败只告警，绝不向上抛。 */
    private void audit(ToolInvokeRequest request, ToolMeta meta, String argsJson,
                       String resultJson, String status, String error, long latencyMs) {
        try {
            GraphToolInvokeLog entry = new GraphToolInvokeLog();
            entry.setToolId(meta.getToolId());
            entry.setTenantId(meta.getTenantId());
            entry.setUserId(blankTo(request.userId(), DEFAULT_TENANT));
            entry.setRunId(request.runId());
            entry.setNodeId(request.nodeId());
            entry.setArgsJson(truncate(argsJson));
            entry.setResultJson(truncate(resultJson));
            entry.setStatus(status);
            entry.setErrorMsg(truncate(error));
            entry.setLatencyMs(latencyMs);
            entry.setInvokeTs(System.currentTimeMillis());
            logMapper.insert(entry);
        } catch (RuntimeException e) {
            log.warn("[tool] 调用审计写入失败（不影响调用结果）: tool={}, err={}",
                    meta.getToolId(), e.getMessage());
        }
    }

    /* ==================== 内部工具 ==================== */

    /** 按租户 + 工具标识查目录。 */
    private ToolMeta findMeta(String tenantId, String toolId) {
        if (!StringUtils.hasText(toolId)) {
            return null;
        }
        LambdaQueryWrapper<ToolMeta> query = new LambdaQueryWrapper<ToolMeta>()
                .eq(ToolMeta::getTenantId, tenantId)
                .eq(ToolMeta::getToolId, toolId)
                .last("limit 1");
        return metaMapper.selectOne(query);
    }

    /** 跨租户查同标识工具（仅用于识别越权调用）。 */
    private ToolMeta findMetaAnyTenant(String toolId) {
        if (!StringUtils.hasText(toolId)) {
            return null;
        }
        LambdaQueryWrapper<ToolMeta> query = new LambdaQueryWrapper<ToolMeta>()
                .eq(ToolMeta::getToolId, toolId)
                .last("limit 1");
        return metaMapper.selectOne(query);
    }

    private long elapsed(long start) {
        return Math.max(0L, System.currentTimeMillis() - start);
    }

    private String blankTo(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= AUDIT_TEXT_LIMIT ? text : text.substring(0, AUDIT_TEXT_LIMIT) + "...";
    }

    /** JSON 序列化；失败时降级为 {@code toString} 文本，保证审计仍有内容。 */
    private String writeJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
