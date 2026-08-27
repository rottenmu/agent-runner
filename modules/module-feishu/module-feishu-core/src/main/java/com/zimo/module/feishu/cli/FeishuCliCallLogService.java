package com.zimo.module.feishu.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeishuCliCallLogService {
    private static final Logger log = LoggerFactory.getLogger(FeishuCliCallLogService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int JSON_LIMIT = 4096;
    private static final int OUTPUT_LIMIT = 4096;

    private final FeishuCliCallLogMapper mapper;
    private final boolean enabled;

    public FeishuCliCallLogService(FeishuCliCallLogMapper mapper, boolean enabled) {
        this.mapper = mapper;
        this.enabled = enabled;
    }

    public void record(FeishuCliCommandRequest request, FeishuCliCommandResult result) {
        if (!enabled || mapper == null || request == null || result == null) {
            return;
        }
        try {
            mapper.insert(toEntity(request, result));
        } catch (RuntimeException e) {
            log.warn("Failed to save feishu cli call log", e);
        }
    }

    FeishuCliCallLogEntity toEntity(FeishuCliCommandRequest request, FeishuCliCommandResult result) {
        FeishuCliCallLogEntity entity = new FeishuCliCallLogEntity();
        entity.setBusinessType(request.getBusinessType());
        entity.setMethod(request.getMethod());
        entity.setApiPath(request.getApiPath());
        entity.setCommandSummary(request.getMethod() + " " + request.getApiPath());
        entity.setParamsJson(truncate(writeJson(request.getParams()), JSON_LIMIT));
        entity.setDataJson(truncate(writeJson(request.getData()), JSON_LIMIT));
        entity.setSuccess(result.isSuccess() ? 1 : 0);
        entity.setExitCode(result.getExitCode());
        entity.setAttempts(result.getAttempts());
        entity.setCostMillis(result.getCostMillis());
        entity.setStdout(truncate(result.getStdout(), OUTPUT_LIMIT));
        entity.setStderr(truncate(result.getStderr(), OUTPUT_LIMIT));
        entity.setErrorMessage(truncate(result.getErrorMessage(), 1024));
        return entity;
    }

    private static String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
