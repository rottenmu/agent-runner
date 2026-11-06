package com.zimo.module.ai.tool;

import com.zimo.framework.ai.mcp.ToolBridge;
import com.zimo.framework.common.skill.AiSkillResult;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;

/**
 * MCP 工具后端：把调用转交给 {@code framework-ai} 的 MCP 工具桥 {@link ToolBridge}。
 *
 * <p><b>这正是"复用运行时、不重建运行时"的落点</b>：本工程里 {@code ToolBridge} 的生产实现是
 * {@code module-tools} 的 {@code ToolGovernanceService} —— 它已经承担了工具级权限
 * （{@code tool_agent_permission}）、限流与熔断（{@code ToolGovernor}）、执行器分发
 * （HTTP / Python / 内置）以及运行时审计。harness 只负责把图节点的调用按统一契约送过去，
 * 不复制这些能力。</p>
 *
 * <p><b>为什么用 {@code ObjectProvider} 而不是直接依赖</b>：MCP 工具桥由框架层装配，
 * 未启用工具模块的部署不应因为缺少该 Bean 而起不来；缺失时工具调用返回明确失败，
 * 而不是让整个工具目录不可用。</p>
 *
 * <p><b>为什么必须检查 {@code AiSkillResult.success}</b>：MCP 工具级失败同样以
 * HTTP 200 + 结果体的形式回传，只看状态码会把"参数不合法"当成"调用成功"，
 * 这是最难排查的一类静默错误。</p>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class McpToolProvider implements ToolProvider {

    /** 后端类型标识。 */
    public static final String TYPE = "mcp";

    private final ObjectProvider<ToolBridge> bridgeProvider;

    /**
     * 构造 MCP 工具后端。
     *
     * @param bridgeProvider MCP 工具桥提供者，允许运行时缺失
     */
    public McpToolProvider(ObjectProvider<ToolBridge> bridgeProvider) {
        this.bridgeProvider = bridgeProvider;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ToolInvokeResult invoke(ToolMeta meta, Map<String, Object> args) {
        ToolBridge bridge = bridgeProvider == null ? null : bridgeProvider.getIfAvailable();
        if (bridge == null) {
            return ToolInvokeResult.fail("MCP 工具桥未装配（framework-ai ToolBridge 缺失）");
        }
        try {
            AiSkillResult result = bridge.call(meta.getProviderRef(), args == null ? Map.of() : args);
            if (result == null) {
                return ToolInvokeResult.fail("MCP 工具无返回: " + meta.getProviderRef());
            }
            return result.success()
                    ? ToolInvokeResult.ok(result.content())
                    : ToolInvokeResult.fail(result.content());
        } catch (RuntimeException e) {
            return ToolInvokeResult.fail("MCP 工具调用异常: " + e.getMessage());
        }
    }
}
