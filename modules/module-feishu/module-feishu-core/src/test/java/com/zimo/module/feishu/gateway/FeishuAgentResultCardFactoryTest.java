package com.zimo.module.feishu.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zimo.module.feishu.reply.FeishuCardTemplateFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuAgentResultCardFactoryTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void buildsCardWithFieldsLinksAndArchiveStatus() {
        FeishuAgentBusinessResult result = FeishuAgentBusinessResult.success("项目进度", "主项目数据已查询")
                .field("项目编号", "XJ100")
                .field("当前阶段", "制造")
                .link("打开表格", "https://feishu.cn/base/app123")
                .link("打开文档", "https://feishu.cn/docx/doc123");

        String card = new FeishuAgentResultCardFactory(new FeishuCardTemplateFactory())
                .buildCard(result, true, "已写入多维表格");

        assertThat(card).contains("项目进度");
        assertThat(card).contains("主项目数据已查询");
        assertThat(card).contains("**项目编号**：XJ100");
        assertThat(card).contains("**当前阶段**：制造");
        assertThat(card).contains("**归档状态**：已写入多维表格");
        assertThat(card).contains("打开表格");
        assertThat(card).contains("https://feishu.cn/base/app123");
        assertThat(card).contains("打开文档");
        assertThat(card).contains("https://feishu.cn/docx/doc123");
    }

    @Test
    void usesPrimaryTypeForFirstLinkAndDefaultForRest() throws Exception {
        FeishuAgentBusinessResult result = FeishuAgentBusinessResult.success("项目进度", "主项目数据已查询")
                .link("打开表格", "https://feishu.cn/base/app123")
                .link("打开文档", "https://feishu.cn/docx/doc123");

        String card = new FeishuAgentResultCardFactory(new FeishuCardTemplateFactory())
                .buildCard(result, true, "已写入多维表格");
        JsonNode actions = OBJECT_MAPPER.readTree(card)
                .path("elements")
                .get(1)
                .path("actions");

        assertThat(actions).hasSize(2);
        assertThat(actions.get(0).path("type").asText()).isEqualTo("primary");
        assertThat(actions.get(0).path("text").path("content").asText()).isEqualTo("打开表格");
        assertThat(actions.get(0).path("url").asText()).isEqualTo("https://feishu.cn/base/app123");
        assertThat(actions.get(1).path("type").asText()).isEqualTo("default");
        assertThat(actions.get(1).path("text").path("content").asText()).isEqualTo("打开文档");
        assertThat(actions.get(1).path("url").asText()).isEqualTo("https://feishu.cn/docx/doc123");
    }

    @Test
    void usesFailureMessageAndTruncatesDisplayedValueToFiveHundredCharacters() {
        String longValue = "A".repeat(600);
        FeishuAgentBusinessResult result = FeishuAgentBusinessResult.failure("查询失败", "业务接口异常")
                .field("错误详情", longValue);

        String card = new FeishuAgentResultCardFactory(new FeishuCardTemplateFactory())
                .buildCard(result, false, "归档未执行");

        assertThat(card).contains("业务接口异常");
        assertThat(card).contains("**错误详情**：" + "A".repeat(500));
        assertThat(card).doesNotContain("A".repeat(501));
        assertThat(card).contains("**归档状态**：归档未执行");
    }
}
