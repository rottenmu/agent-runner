package com.zimo.framework.ai.agent;

import java.util.Optional;

/**
 * 运行时 Profile 补丁（对应 dsh profile/patch 覆盖层）：
 * 在路由结果确定后按请求上下文改写 profile（模型/技能/系统提示），
 * 实现不改代码的运行时替换。
 */
public interface AiProfilePatchProvider {

    /** 对原始 profile 应用补丁；返回空表示不修改。 */
    Optional<AiAgentProfile> patch(AiAgentProfile original, AiAgentRouteRequest request);
}
