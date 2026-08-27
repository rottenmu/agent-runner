package com.zimo.module.feishu.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuProjectCardRendererTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rendersProjectPayloadAsCardKitTableWithActions() throws Exception {
        FeishuProjectCardRenderer renderer = new FeishuProjectCardRenderer();
        String payload = """
                {
                  "type": "project_view",
                  "title": "Project List",
                  "headers": [
                    {"key": "business_project_code", "label": "Project Code"},
                    {"key": "customer_name", "label": "Customer"},
                    {"key": "metadata", "label": "Metadata"}
                  ],
                  "rows": [
                    {
                      "business_project_code": "XJ00120260704",
                      "customer_name": "Customer A",
                      "metadata": {"priority": "high"},
                      "status": {"text": "Running", "color": "blue"}
                    }
                  ],
                  "buttons": [
                    {"text": "Refresh", "action": "refresh_project_view", "value": {"viewType": "main"}}
                  ]
                }
                """;

        assertThat(renderer.supports(payload)).isTrue();

        JsonNode card = objectMapper.readTree(renderer.render(payload));

        assertThat(card.path("schema").asText()).isEqualTo("2.0");
        assertThat(card.path("config").path("width_mode").asText()).isEqualTo("fill");
        assertThat(card.path("config").path("streaming_mode").asBoolean()).isTrue();
        assertThat(card.path("header").path("title").path("content").asText()).isEqualTo("Project List");
        JsonNode elements = card.path("body").path("elements");
        assertThat(elements).anySatisfy(element -> {
            assertThat(element.path("tag").asText()).isEqualTo("table");
            assertThat(element.path("columns").get(0).path("name").asText()).isEqualTo("business_project_code");
            assertThat(element.path("columns").get(0).path("display_name").asText()).isEqualTo("Project Code");
            assertThat(element.path("rows").get(0).path("business_project_code").asText()).isEqualTo("XJ00120260704");
            assertThat(element.path("rows").get(0).path("metadata").asText()).contains("priority", "high");
        });
        assertThat(elements).anySatisfy(element -> {
            assertThat(element.path("tag").asText()).isEqualTo("column_set");
            assertThat(element.toString()).contains("Running", "blue");
        });
        assertThat(elements).anySatisfy(element -> {
            assertThat(element.path("tag").asText()).isEqualTo("column_set");
            JsonNode button = element.path("columns").get(0).path("elements").get(0);
            assertThat(button.path("tag").asText()).isEqualTo("button");
            assertThat(button.path("text").path("content").asText()).isEqualTo("Refresh");
            assertThat(button.path("behaviors").get(0).path("type").asText()).isEqualTo("callback");
            assertThat(button.path("behaviors").get(0).path("value").path("action").asText())
                    .isEqualTo("refresh_project_view");
        });
    }

    @Test
    void ignoresPlainTextReplies() {
        FeishuProjectCardRenderer renderer = new FeishuProjectCardRenderer();

        assertThat(renderer.supports("plain text")).isFalse();
    }
}
