package com.zimo.module.ai.tool;

import java.util.List;
import java.util.Map;

/**
 * 工具市场治理服务：注册 / 发现 / 权限校验 / 调用审计。
 *
 * <p>所属模块：agent-harness 运行时内核。本服务是工具能力的<b>唯一治理入口</b>：
 * 图节点、控制台、以及后续任何调用方都必须经它调用工具，才能保证
 * 「权限判断」与「审计留痕」不会被某条新通道绕过。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public interface ToolRegistryService {

    /**
     * 注册或更新工具元数据（按 {@code tenantId + toolId} 幂等覆盖）。
     *
     * @param meta 工具元数据
     * @return 落库后的元数据（含主键）
     * @throws IllegalArgumentException 必填字段缺失或后端类型未装配
     */
    ToolMeta register(ToolMeta meta);

    /**
     * 查询工具目录。
     *
     * @param tenantId 租户标识
     * @param enabledOnly 是否只返回启用的工具
     * @return 工具列表
     */
    List<ToolMeta> list(String tenantId, boolean enabledOnly);

    /**
     * 逻辑删除工具。
     *
     * @param tenantId 租户标识
     * @param toolId 工具业务标识
     * @return 是否删除成功（工具不存在返回 {@code false}）
     */
    boolean remove(String tenantId, String toolId);

    /**
     * 按租户解析可用工具标识（供运行时白名单使用）。
     *
     * <p>解析失败不阻断主流程：工具是增强能力，返回空清单比抛异常更安全。</p>
     *
     * @param tenantId 租户标识
     * @return 已启用的工具标识列表
     */
    List<String> listToolIds(String tenantId);

    /**
     * 调用工具（含权限校验与审计留痕）。
     *
     * <p>约定：<b>权限不足与执行失败都以结果形式返回</b>，不抛异常 ——
     * 调用方（如图节点）据此决定是否降级，而非让整条流程崩掉。</p>
     *
     * @param request 调用请求
     * @return 调用结果
     */
    ToolCallResult invoke(ToolInvokeRequest request);

    /**
     * 工具调用请求。
     *
     * @param toolId 工具业务标识
     * @param tenantId 租户标识
     * @param userId 调用者标识
     * @param args 调用参数
     * @param runId 图运行实例 ID，非图调用为空
     * @param nodeId 图节点 ID，非图调用为空
     */
    record ToolInvokeRequest(String toolId, String tenantId, String userId,
                             Map<String, Object> args, Long runId, String nodeId) {
    }

    /**
     * 工具调用结果。
     *
     * @param success 是否成功
     * @param guarded 是否因权限不足被拦截（{@code true} 时 {@code content} 为空）
     * @param content 成功时的结果文本
     * @param error 失败原因
     * @param latencyMs 耗时（毫秒）
     */
    record ToolCallResult(boolean success, boolean guarded, String content,
                          String error, long latencyMs) {

        /** 成功结果。 */
        public static ToolCallResult ok(String content, long latencyMs) {
            return new ToolCallResult(true, false, content == null ? "" : content, null, latencyMs);
        }

        /** 执行失败结果。 */
        public static ToolCallResult failed(String error, long latencyMs) {
            return new ToolCallResult(false, false, "", error, latencyMs);
        }

        /** 权限拦截结果。 */
        public static ToolCallResult guarded(String reason) {
            return new ToolCallResult(false, true, "", reason, 0L);
        }
    }
}
