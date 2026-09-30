package com.zimo.framework.ai.agent.memory;

/**
 * 短期会话记忆保留期限读取端口。
 *
 * <p>由应用组装层提供实现，使框架清理任务可读取当前运行设置而不依赖具体业务模块。</p>
 *
 * @author Codex
 * @since 2026-09-29
 */
@FunctionalInterface
public interface ShortTermMemoryRetentionDaysProvider {

    /**
     * 读取当前短期会话记忆保留期限。
     *
     * @return 保留天数，合法范围为 1 至 365；调用方会对无效值执行默认值回退
     */
    int getRetentionDays();
}
