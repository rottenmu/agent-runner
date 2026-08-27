package com.zimo.module.feishu.reply;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuCardTemplateFactoryTest {

    @Test
    void buildsActionCardWithButtonsInOriginalOrder() {
        FeishuCardTemplateFactory factory = new FeishuCardTemplateFactory();

        String card = factory.buildActionCard("Project Risk", "**High risk**",
                List.of(
                        FeishuCardButton.url("View Project", "primary", "https://example.com/project"),
                        FeishuCardButton.value("Acknowledge", "default", Map.of("action", "ack"))
                ));

        assertThat(card).contains("\"title\"");
        assertThat(card).contains("Project Risk");
        assertThat(card).contains("View Project");
        assertThat(card).contains("Acknowledge");
        assertThat(card.indexOf("View Project")).isLessThan(card.indexOf("Acknowledge"));
        assertThat(card).contains("https://example.com/project");
        assertThat(card).contains("\"action\":\"ack\"");
    }
}
