package com.zimo.framework.ai.agent;

import io.agentscope.core.tool.Toolkit;

/**
 * 能力提供方（对应 dsh 能力 Seam 的 Service Provider 角色）：
 * 向 HarnessAgent 的 Toolkit 贡献一组面向模型的能力（工具）。
 *
 * <p>实现注册为 Spring Bean 后由 {@link AiHarnessAgentFactory} 在装配 agent 时注入；
 * 技能（skillRegistry）与记忆工具（AiMemoryAgentTool）即此类能力的既有实例。</p>
 */
public interface AiCapabilityProvider {

    /** 能力标识（如 memory / skills / filesystem）。 */
    String name();

    /** 向指定 agent 的 Toolkit 贡献能力工具。 */
    void contribute(Toolkit toolkit, AiAgentProfile profile);
}
