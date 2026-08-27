package com.zimo.module.feishu.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zimo.module.feishu.admin.dto.FeishuMessageLogPageRequest;
import com.zimo.module.feishu.channel.FeishuAgentCommandMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class FeishuMessageLogService {
    private static final Logger log = LoggerFactory.getLogger(FeishuMessageLogService.class);

    private final FeishuMessageLogMapper mapper;
    private final boolean enabled;

    public FeishuMessageLogService(FeishuMessageLogMapper mapper, boolean enabled) {
        this.mapper = mapper;
        this.enabled = enabled;
    }

    public void record(FeishuMessageLogEntity entity) {
        if (!enabled || mapper == null || entity == null) {
            return;
        }
        try {
            mapper.insert(entity);
        } catch (RuntimeException e) {
            log.warn("Failed to save feishu message log", e);
        }
    }

    public Page<FeishuMessageLogEntity> page(FeishuMessageLogPageRequest request) {
        long pageNo = normalizePageNo(request == null ? null : request.getPageNo());
        long pageSize = normalizePageSize(request == null ? null : request.getPageSize());
        Page<FeishuMessageLogEntity> page = new Page<>(pageNo, pageSize);
        if (!enabled || mapper == null) {
            return page;
        }
        LambdaQueryWrapper<FeishuMessageLogEntity> wrapper = Wrappers.lambdaQuery(FeishuMessageLogEntity.class)
                .eq(request != null && StringUtils.hasText(request.getTenantKey()),
                        FeishuMessageLogEntity::getTenantKey, request == null ? null : request.getTenantKey())
                .eq(request != null && StringUtils.hasText(request.getChatId()),
                        FeishuMessageLogEntity::getChatId, request == null ? null : request.getChatId())
                .eq(request != null && StringUtils.hasText(request.getSenderUserId()),
                        FeishuMessageLogEntity::getSenderUserId, request == null ? null : request.getSenderUserId())
                .eq(request != null && StringUtils.hasText(request.getStage()),
                        FeishuMessageLogEntity::getStage, request == null ? null : request.getStage())
                .eq(request != null && request.getSuccess() != null,
                        FeishuMessageLogEntity::getSuccess, request == null ? null : request.getSuccess())
                .like(request != null && StringUtils.hasText(request.getCommandText()),
                        FeishuMessageLogEntity::getCommandText, request == null ? null : request.getCommandText())
                .orderByDesc(FeishuMessageLogEntity::getCreateTime);
        return mapper.selectPage(page, wrapper);
    }

    public void recordFailure(FeishuAgentCommandMessage message, String stage, Exception exception) {
        FeishuMessageLogEntity entity = new FeishuMessageLogEntity();
        if (message != null) {
            entity.setMessageId(message.getMessageId());
            entity.setChatId(message.getChatId());
            entity.setTenantKey(message.getTenantKey());
            entity.setSenderUserId(message.getSenderUserId());
            entity.setSenderOpenId(message.getSenderOpenId());
            entity.setCommandText(message.getCommandText());
        }
        entity.setStage(stage == null ? FeishuMessageLogStage.FAILED.name() : stage);
        entity.setSuccess(0);
        entity.setErrorMessage(exception == null ? null : truncate(exception.getMessage(), 1024));
        record(entity);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static long normalizePageNo(Long pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private static long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }
        return Math.min(pageSize, 100L);
    }
}
