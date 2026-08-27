package com.zimo.module.feishu.gateway;

import com.zimo.module.feishu.channel.FeishuAgentCommandMessage;
import com.zimo.module.feishu.cli.FeishuCliCommandResult;
import com.zimo.module.feishu.cli.bitable.BitableRecordCreateRequest;
import com.zimo.module.feishu.cli.bitable.FeishuBitableCliService;
import com.zimo.module.feishu.config.FeishuConfigProvider;
import com.zimo.module.feishu.config.FeishuRuntimeConfig;
import com.zimo.module.feishu.reply.FeishuAgentReplyService;
import com.zimo.module.feishu.reply.FeishuStreamReplyOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class FeishuAgentDispatchService {
    private static final Logger log = LoggerFactory.getLogger(FeishuAgentDispatchService.class);

    private static final String HELP_REPLY = "请输入有效指令，例如：项目 XJ100 进度。";
    private static final String CONFIG_REPLY = "飞书应用未完成配置";
    private static final String RATE_LIMIT_REPLY = "请求过于频繁，请稍后再试。";
    private static final String UNKNOWN_ROUTE_REPLY = "暂未匹配到可执行指令，请输入：帮助。";
    private static final String BUSINESS_ERROR_REPLY = "指令执行失败，请稍后再试。";
    private static final String ARCHIVE_SUCCESS = "已写入多维表格";
    private static final String ARCHIVE_SKIPPED = "未写入多维表格";
    private static final String ARCHIVE_FAILED = "多维表格归档失败";

    private final FeishuConfigProvider configProvider;
    private final FeishuAgentCommandRouter router;
    private final FeishuBitableCliService bitableCliService;
    private final FeishuAgentRateLimiter rateLimiter;
    private final FeishuAgentResultCardFactory cardFactory;
    private final boolean progressReplyEnabled;
    private final boolean archiveEnabled;
    private final String archiveAppToken;
    private final String archiveTableId;

    public FeishuAgentDispatchService(
            FeishuConfigProvider configProvider,
            FeishuAgentCommandRouter router,
            FeishuBitableCliService bitableCliService,
            FeishuAgentRateLimiter rateLimiter,
            FeishuAgentResultCardFactory cardFactory,
            boolean progressReplyEnabled,
            boolean archiveEnabled,
            String archiveAppToken,
            String archiveTableId) {
        this.configProvider = Objects.requireNonNull(configProvider, "configProvider must not be null");
        this.router = Objects.requireNonNull(router, "router must not be null");
        this.bitableCliService = Objects.requireNonNull(bitableCliService, "bitableCliService must not be null");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.cardFactory = Objects.requireNonNull(cardFactory, "cardFactory must not be null");
        this.progressReplyEnabled = progressReplyEnabled;
        this.archiveEnabled = archiveEnabled;
        this.archiveAppToken = archiveAppToken;
        this.archiveTableId = archiveTableId;
    }

    public void dispatch(FeishuAgentCommandMessage message, FeishuAgentReplyService replyService) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(replyService, "replyService must not be null");

        String commandText = trimToEmpty(message.getCommandText());
        if (commandText.isEmpty()) {
            replyService.replyText(message.getMessageId(), HELP_REPLY);
            return;
        }
        if (!hasValidActiveConfig()) {
            replyService.replyText(message.getMessageId(), CONFIG_REPLY);
            return;
        }
        if (!rateLimiter.tryAcquire(message.getTenantKey(), message.getSenderUserId())) {
            replyService.replyText(message.getMessageId(), RATE_LIMIT_REPLY);
            return;
        }

        Optional<FeishuAgentBusinessHandler> handler = router.route(commandText);
        if (handler.isEmpty()) {
            replyService.replyText(message.getMessageId(), UNKNOWN_ROUTE_REPLY);
            return;
        }

        try {
            sendProgressReply(message, replyService, commandText);
            FeishuAgentBusinessResult result = handler.get().handle(new FeishuAgentBusinessRequest(message));
            if (result == null) {
                throw new FeishuAgentGatewayException("business result must not be null");
            }

            ArchiveStatus archiveStatus = archiveIfNecessary(message, commandText, result);
            String cardJson = cardFactory.buildCard(result, archiveStatus.archived(), archiveStatus.message());
            replyService.replyCard(message.getMessageId(), cardJson);
        } catch (RuntimeException ex) {
            log.warn("Feishu agent command dispatch failed, messageId={}, tenantKey={}, command={}",
                    message.getMessageId(), message.getTenantKey(), commandText, ex);
            replyService.replyText(message.getMessageId(), BUSINESS_ERROR_REPLY);
        }
    }

    private void sendProgressReply(
            FeishuAgentCommandMessage message,
            FeishuAgentReplyService replyService,
            String commandText) {
        if (!progressReplyEnabled) {
            return;
        }
        try {
            replyService.streamText(
                    message.getMessageId(),
                    "正在处理：" + commandText,
                    new FeishuStreamReplyOptions(80, Duration.ZERO));
        } catch (RuntimeException ex) {
            log.warn("Feishu agent progress reply failed, messageId={}, tenantKey={}, command={}",
                    message.getMessageId(), message.getTenantKey(), commandText, ex);
        }
    }

    private boolean hasValidActiveConfig() {
        FeishuRuntimeConfig config = configProvider.getActiveConfig();
        return config != null && hasText(config.getAppId()) && hasText(config.getAppSecret());
    }

    private ArchiveStatus archiveIfNecessary(
            FeishuAgentCommandMessage message,
            String commandText,
            FeishuAgentBusinessResult result) {
        if (!archiveEnabled || !hasText(archiveAppToken) || !hasText(archiveTableId)) {
            return new ArchiveStatus(false, ARCHIVE_SKIPPED);
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("指令", commandText);
        fields.put("租户", message.getTenantKey());
        fields.put("发送人", message.getSenderUserId());
        fields.put("结果标题", result.getTitle());
        fields.put("结果摘要", hasText(result.getSummary()) ? result.getSummary() : result.getMessage());
        for (Map.Entry<String, Object> entry : result.getArchiveFields().entrySet()) {
            if (!fields.containsKey(entry.getKey())) {
                fields.put(entry.getKey(), entry.getValue());
            }
        }

        try {
            FeishuCliCommandResult archiveResult = bitableCliService.createRecord(
                    new BitableRecordCreateRequest(archiveAppToken, archiveTableId, fields));
            boolean archived = archiveResult != null && archiveResult.isSuccess();
            return new ArchiveStatus(archived, archived ? ARCHIVE_SUCCESS : ARCHIVE_FAILED);
        } catch (RuntimeException ex) {
            log.warn("Feishu agent archive failed, messageId={}, tenantKey={}, command={}",
                    message.getMessageId(), message.getTenantKey(), commandText, ex);
            return new ArchiveStatus(false, ARCHIVE_FAILED);
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record ArchiveStatus(boolean archived, String message) {
    }
}
