package com.zimo.framework.ai.agent;

import com.zimo.framework.common.ai.event.AiEventPublisher;
import com.zimo.framework.common.ai.event.ToolCallEvent;

/**
 * 工具事件发布钩子：将工具调用发布到事件总线（ToolCallEvent，能力域）。
 */
public class EventPublishingToolExecutionListener implements ToolExecutionListener {

    private final AiEventPublisher publisher;

    public EventPublishingToolExecutionListener(AiEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public boolean onPreExecute(String toolName, Object params) {
        publisher.publish(new ToolCallEvent(toolName, "pre", params, null, null, System.currentTimeMillis()));
        return true;
    }

    @Override
    public void onPostExecute(String toolName, Object params, Object result) {
        publisher.publish(new ToolCallEvent(toolName, "post", params, result, null, System.currentTimeMillis()));
    }

    @Override
    public void onError(String toolName, Object params, Throwable error) {
        publisher.publish(new ToolCallEvent(toolName, "error", params, null,
                error == null ? null : error.getMessage(), System.currentTimeMillis()));
    }
}
