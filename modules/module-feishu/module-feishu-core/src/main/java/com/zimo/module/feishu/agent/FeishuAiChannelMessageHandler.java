package com.zimo.module.feishu.agent;

import com.zimo.module.feishu.channel.FeishuAgentCommandMessage;
import com.zimo.module.feishu.channel.FeishuAgentMessageHandler;
import com.zimo.module.feishu.config.FeishuConfigProvider;
import com.zimo.module.feishu.file.FeishuFileClient;
import com.zimo.module.feishu.file.FeishuFileDownloadResult;
import com.zimo.module.feishu.reply.FeishuAgentReplyService;
import com.zimo.starter.ai.channel.AiChannelAgentBinding;
import com.zimo.starter.ai.channel.AiChannelHandler;
import com.zimo.starter.ai.channel.AiChannelMessage;
import com.zimo.starter.ai.channel.AiChannelReply;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class FeishuAiChannelMessageHandler implements FeishuAgentMessageHandler {
    private static final String CHANNEL = "feishu";

    private final AiChannelHandler aiChannelHandler;
    private final FeishuProjectCardRenderer projectCardRenderer;
    private final FeishuFileClient fileClient;
    private final FeishuConfigProvider configProvider;

    public FeishuAiChannelMessageHandler(AiChannelHandler aiChannelHandler) {
        this(aiChannelHandler, new FeishuProjectCardRenderer(), null);
    }

    public FeishuAiChannelMessageHandler(
            AiChannelHandler aiChannelHandler,
            FeishuProjectCardRenderer projectCardRenderer) {
        this(aiChannelHandler, projectCardRenderer, null);
    }

    public FeishuAiChannelMessageHandler(
            AiChannelHandler aiChannelHandler,
            FeishuProjectCardRenderer projectCardRenderer,
            FeishuFileClient fileClient) {
        this(aiChannelHandler, projectCardRenderer, fileClient, null);
    }

    /**
     * 创建携带飞书配置绑定信息的 AI 渠道消息处理器。
     *
     * @param aiChannelHandler AI 渠道处理器，不允许为 {@code null}
     * @param projectCardRenderer 项目卡片渲染器，不允许为 {@code null}
     * @param fileClient 飞书文件客户端，不需要处理文件时允许为 {@code null}
     * @param configProvider 飞书配置提供者，不需要传递绑定信息时允许为 {@code null}
     */
    public FeishuAiChannelMessageHandler(
            AiChannelHandler aiChannelHandler,
            FeishuProjectCardRenderer projectCardRenderer,
            FeishuFileClient fileClient,
            FeishuConfigProvider configProvider) {
        this.aiChannelHandler = Objects.requireNonNull(aiChannelHandler, "aiChannelHandler must not be null");
        this.projectCardRenderer = Objects.requireNonNull(projectCardRenderer, "projectCardRenderer must not be null");
        this.fileClient = fileClient;
        this.configProvider = configProvider;
    }

    @Override
    public void handle(FeishuAgentCommandMessage message, FeishuAgentReplyService replyService) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(replyService, "replyService must not be null");

        Map<String, Object> attributes = attributes(message);
        AiChannelReply reply = aiChannelHandler.handle(AiChannelMessage.of(
                CHANNEL,
                message.getTenantKey(),
                message.getStableSenderIdentity(),
                message.getChatId(),
                message.getMessageId(),
                message.getCommandText(),
                attributes));
        String content = reply.content();
        if (projectCardRenderer.supports(content)) {
            replyService.replyCard(message.getMessageId(), projectCardRenderer.render(content));
            return;
        }
        replyService.replyText(message.getMessageId(), content);
    }

    @Override
    public boolean requiresInternalAccount() {
        return false;
    }

    private Map<String, Object> attributes(FeishuAgentCommandMessage message) {
        Map<String, Object> attributes = new LinkedHashMap<>(message.getAttributes());
        attributes.remove("agentId");
        attributes.remove(AiChannelAgentBinding.ATTRIBUTE_NAME);
        String agentId = configProvider == null
                ? null
                : configProvider.getActiveAgentId(message.getTenantKey());
        if (hasText(agentId)) {
            String normalizedAgentId = agentId.trim();
            attributes.put("agentId", normalizedAgentId);
            attributes.put(
                    AiChannelAgentBinding.ATTRIBUTE_NAME,
                    new AiChannelAgentBinding(message.getTenantKey(), normalizedAgentId));
        }
        if (hasText(text(attributes, "fileBase64"))) {
            return attributes;
        }
        String fileKey = text(attributes, "fileKey");
        if (!hasText(fileKey) || fileClient == null) {
            return attributes;
        }
        FeishuFileDownloadResult result = fileClient.downloadMessageFile(message.getMessageId(), fileKey);
        if (!result.success()) {
            attributes.put("fileDownloadError", result.errorMessage());
            return attributes;
        }
        attributes.put("fileBase64", Base64.getEncoder().encodeToString(result.content()));
        if (!hasText(text(attributes, "sourceName")) && hasText(result.fileName())) {
            attributes.put("sourceName", result.fileName());
        }
        if (hasText(result.contentType())) {
            attributes.put("contentType", result.contentType());
        }
        return attributes;
    }

    private String text(Map<String, Object> attributes, String key) {
        Object value = attributes == null ? null : attributes.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
