package com.zimo.starter.ai.event;

import com.zimo.framework.common.ai.event.AiEventPublisher;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Spring 事件桥：将 AI 运行时事件发布到 ApplicationContext（轻量事件总线）。
 * 消费方以 @EventListener 订阅对应事件类型。
 */
public class SpringAiEventPublisher implements AiEventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringAiEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(Object event) {
        publisher.publishEvent(event);
    }
}
