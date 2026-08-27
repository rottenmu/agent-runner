package com.zimo.starter.ai.skill;

import java.util.Map;
import java.util.Optional;

/**
 * 工具守卫（对齐 DeepSeek Harness tool pipeline monotonic guards）。
 *
 * <p>在钩子之后、审批之前执行：返回非空拒绝原因则短路。
 * 典型用途：单调性检查（同链路重复调用）、频控、参数合法性校验。</p>
 */
public interface ToolGuard {

    /** 检查是否允许执行：返回非空 = 拒绝（原因）。 */
    Optional<String> check(ToolCallContext context, String toolName, Map<String, Object> arguments);
}
