package com.zimo.module.feishu.channel;

import com.zimo.module.feishu.log.FeishuMessageLogEntity;
import com.zimo.module.feishu.log.FeishuMessageLogService;
import com.zimo.module.feishu.log.FeishuMessageLogStage;
import com.zimo.module.feishu.mapping.FeishuInternalUserSnapshot;
import com.zimo.module.feishu.mapping.FeishuUserMappingService;
import com.zimo.module.feishu.mapping.FeishuUserPermissionBinder;
import com.zimo.module.feishu.reply.FeishuAgentReplyService;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class FeishuChannelMessageListener {
    private final FeishuChannelMessageParser parser;
    private final FeishuUserMappingService mappingService;
    private final FeishuUserPermissionBinder permissionBinder;
    private final FeishuAgentMessageHandler messageHandler;
    private final FeishuAgentReplyService replyService;
    private final FeishuMessageLogService logService;

    public FeishuChannelMessageListener(
            FeishuChannelMessageParser parser,
            FeishuUserMappingService mappingService,
            FeishuUserPermissionBinder permissionBinder,
            FeishuAgentMessageHandler messageHandler,
            FeishuAgentReplyService replyService,
            FeishuMessageLogService logService) {
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.mappingService = Objects.requireNonNull(mappingService, "mappingService must not be null");
        this.permissionBinder = Objects.requireNonNull(permissionBinder, "permissionBinder must not be null");
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler must not be null");
        this.replyService = Objects.requireNonNull(replyService, "replyService must not be null");
        this.logService = Objects.requireNonNull(logService, "logService must not be null");
    }

    public void onMessage(Map<String, Object> payload) {
        record(null, FeishuMessageLogStage.RECEIVED, true);
        FeishuAgentCommandMessage message = null;
        try {
            Optional<FeishuAgentCommandMessage> parsed = parser.parse(payload);
            if (parsed.isEmpty()) {
                return;
            }
            message = parsed.get();
            record(message, FeishuMessageLogStage.PARSED, true);

            Optional<FeishuInternalUserSnapshot> user = mappingService.findInternalUser(message);
            if (user.isEmpty()) {
                if (messageHandler.requiresInternalAccount()) {
                    replyService.replyText(message.getMessageId(), "Feishu user is not bound to an internal account.");
                    record(message, FeishuMessageLogStage.FAILED, false);
                    return;
                }
                try (AutoCloseable ignored = permissionBinder.bind(null)) {
                    messageHandler.handle(message, replyService);
                    record(message, FeishuMessageLogStage.HANDLED, true);
                }
                return;
            }

            try (AutoCloseable ignored = permissionBinder.bind(user.get())) {
                record(message, FeishuMessageLogStage.MAPPED, true);
                messageHandler.handle(message, replyService);
                record(message, FeishuMessageLogStage.HANDLED, true);
            }
        } catch (Exception e) {
            logService.recordFailure(message, FeishuMessageLogStage.FAILED.name(), e);
            throw new IllegalStateException("Failed to handle feishu channel message", e);
        }
    }

    private void record(FeishuAgentCommandMessage message, FeishuMessageLogStage stage, boolean success) {
        FeishuMessageLogEntity entity = new FeishuMessageLogEntity();
        if (message != null) {
            entity.setMessageId(message.getMessageId());
            entity.setChatId(message.getChatId());
            entity.setTenantKey(message.getTenantKey());
            entity.setSenderUserId(message.getSenderUserId());
            entity.setSenderOpenId(message.getSenderOpenId());
            entity.setCommandText(message.getCommandText());
        }
        entity.setStage(stage.name());
        entity.setSuccess(success ? 1 : 0);
        logService.record(entity);
    }
}
