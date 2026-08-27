package com.zimo.framework.common.ai.event;

/**
 * 轻量 AI 运行时事件发布器（对应 dsh 三类事件域）。
 *
 * <p>实现（如 Spring 事件桥）由宿主注册为 Bean；消费方以监听器订阅。
 * 事件定义：{@link ConversationTurnEvent}（会话域）、{@link AgentStepEvent}（Agent 域）、
 * {@link ToolCallEvent}（工具/能力域）。</p>
 */
public interface AiEventPublisher {

    /** 发布一个 AI 运行时事件。 */
    void publish(Object event);
}
