package com.zimo.module.feishu.gateway;

import com.zimo.module.feishu.reply.FeishuCardButton;
import com.zimo.module.feishu.reply.FeishuCardTemplateFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FeishuAgentResultCardFactory {
    private static final int DISPLAY_LIMIT = 500;

    private final FeishuCardTemplateFactory cardTemplateFactory;

    public FeishuAgentResultCardFactory(FeishuCardTemplateFactory cardTemplateFactory) {
        this.cardTemplateFactory = Objects.requireNonNull(cardTemplateFactory, "cardTemplateFactory must not be null");
    }

    public String buildCard(FeishuAgentBusinessResult result, boolean archived, String archiveMessage) {
        Objects.requireNonNull(result, "result must not be null");

        String title = hasText(result.getTitle()) ? result.getTitle() : "飞书 Agent 处理结果";
        String body = hasText(result.getSummary()) ? result.getSummary() : result.getMessage();
        if (!hasText(body)) {
            body = result.isSuccess() ? "处理完成" : "处理失败";
        }

        StringBuilder markdown = new StringBuilder(limit(body));
        for (Map.Entry<String, Object> entry : result.getFields().entrySet()) {
            markdown.append("\n")
                    .append("**")
                    .append(limit(entry.getKey()))
                    .append("**：")
                    .append(limit(entry.getValue()));
        }
        markdown.append("\n")
                .append("**归档状态**：")
                .append(limit(resolveArchiveMessage(archived, archiveMessage)));

        List<FeishuCardButton> buttons = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, String> link : result.getLinks().entrySet()) {
            buttons.add(FeishuCardButton.url(
                    limit(link.getKey()),
                    index == 0 ? "primary" : "default",
                    link.getValue()));
            index++;
        }

        return cardTemplateFactory.buildActionCard(limit(title), markdown.toString(), buttons);
    }

    private static String resolveArchiveMessage(boolean archived, String archiveMessage) {
        if (hasText(archiveMessage)) {
            return archiveMessage;
        }
        return archived ? "已写入多维表格" : "未写入多维表格";
    }

    private static String limit(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.length() <= DISPLAY_LIMIT) {
            return text;
        }
        return text.substring(0, DISPLAY_LIMIT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
