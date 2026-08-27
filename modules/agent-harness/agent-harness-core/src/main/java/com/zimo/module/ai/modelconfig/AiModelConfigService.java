package com.zimo.module.ai.modelconfig;

import com.zimo.framework.common.BizException;
import cn.hutool.core.collection.CollUtil;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * AI 模型配置管理核心业务服务。
 *
 * <p>服务负责模型配置参数校验、默认值补齐、密钥保留规则、响应脱敏、复制配置和模拟连接测试。
 * 真实数据库访问由 {@link AiModelConfigRepository} 的后续实现承担。</p>
 *
 * @author Codex
 * @since 2026-07-23
 */
public class AiModelConfigService {
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("bailian", "custom");
    private static final Set<String> SUPPORTED_ENVS = Set.of("dev", "staging", "prod");
    private static final BigDecimal DEFAULT_TEMPERATURE = new BigDecimal("0.70");
    private static final BigDecimal DEFAULT_TOP_P = new BigDecimal("0.80");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final String STATUS_UNTESTED = "untested";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private static final String MESSAGE_UNTESTED = "尚未测试";

    private final AiModelConfigRepository repository;

    /**
     * 创建模型配置业务服务。
     *
     * @param repository 模型配置仓储，不能为 null
     */
    public AiModelConfigService(AiModelConfigRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * 按查询条件返回模型配置列表。
     *
     * @param query 查询条件，允许为空
     * @return 已脱敏的模型配置列表
     */
    public List<AiModelConfigResponse> list(AiModelConfigQuery query) {
        return repository.find(query).stream().map(this::toResponse).toList();
    }

    /**
     * 按主键查询模型配置。
     *
     * @param id 配置主键，必须存在
     * @return 已脱敏的模型配置
     */
    public AiModelConfigResponse get(long id) {
        return toResponse(requireEntity(id));
    }

    /**
     * 新增模型配置。
     *
     * @param request 新增请求，API Key 必填
     * @return 已脱敏的新增配置
     */
    public AiModelConfigResponse create(AiModelConfigRequest request) {
        validateRequest(request, true);
        AiModelConfigEntity entity = fromRequest(request, null);
        resetTestResult(entity);
        return toResponse(repository.insert(entity));
    }

    /**
     * 编辑模型配置。
     *
     * @param id 配置主键，必须存在
     * @param request 编辑请求，API Key 为空或以 *** 开头时沿用原密钥
     * @return 已脱敏的编辑后配置
     */
    public AiModelConfigResponse update(long id, AiModelConfigRequest request) {
        AiModelConfigEntity existing = requireEntity(id);
        validateRequest(request, false);
        AiModelConfigEntity entity = fromRequest(request, existing);
        return toResponse(repository.update(entity));
    }

    /**
     * 逻辑删除模型配置。
     *
     * @param id 配置主键，必须大于 0
     * @return true 表示删除成功
     */
    public boolean delete(long id) {
        validateId(id);
        if (!repository.logicalDelete(id)) {
            throw notFound();
        }
        return true;
    }

    /**
     * 复制模型配置。
     *
     * @param id 来源配置主键，必须存在
     * @return 已脱敏的新配置
     */
    public AiModelConfigResponse copy(long id) {
        AiModelConfigEntity source = requireEntity(id);
        AiModelConfigEntity copy = cloneForCopy(source);
        return toResponse(repository.insert(copy));
    }

    /**
     * 模拟执行模型连接测试。
     *
     * @param id 配置主键，必须存在
     * @return 连接测试结果
     */
    public AiModelConfigTestResponse testConnection(long id) {
        AiModelConfigEntity entity = requireEntity(id);
        AiModelConfigTestResponse response = simulateConnection(entity);
        repository.updateTestResult(id, response);
        return response;
    }

    /**
     * 导出模型配置。
     *
     * @param query 导出筛选条件，允许为空
     * @return 已脱敏的模型配置列表，不包含原始 API Key
     */
    public List<AiModelConfigResponse> export(AiModelConfigQuery query) {
        return list(query);
    }

    /**
     * 批量导入模型配置。
     *
     * @param request 导入请求，空列表时返回零成功结果
     * @return 导入成功数和跳过数
     */
    public AiModelConfigImportResponse importConfigs(AiModelConfigImportRequest request) {
        List<AiModelConfigRequest> configs = request == null ? List.of() : request.configs();
        int success = importEachConfig(configs);
        return new AiModelConfigImportResponse(success, configs.size() - success);
    }

    private int importEachConfig(List<AiModelConfigRequest> configs) {
        int success = 0;
        for (AiModelConfigRequest config : configs) {
            try {
                create(config);
                success++;
            } catch (RuntimeException ignored) {
                // 批量导入允许单条失败并继续处理后续配置。
            }
        }
        return success;
    }

    private void validateRequest(AiModelConfigRequest request, boolean requireApiKey) {
        if (request == null) {
            throw badRequest("模型配置请求不能为空");
        }
        requireText(request.configName(), "配置名称必填");
        requireText(request.provider(), "模型供应商必填");
        requireText(request.endpoint(), "服务地址必填");
        requireText(request.modelId(), "模型 ID 必填");
        requireText(request.env(), "运行环境必填");
        if (requireApiKey) {
            requireText(request.apiKey(), "API Key 必填");
        }
        validateLength(request);
        validateEnums(request.provider(), request.env());
        validateRuntimeParams(request);
        validateTags(request.tags());
    }

    private void validateLength(AiModelConfigRequest request) {
        validateMaxLength(request.configName(), 128, "配置名称不能超过 128 个字符");
        validateMaxLength(request.endpoint(), 512, "服务地址不能超过 512 个字符");
        validateMaxLength(request.modelId(), 128, "模型 ID 不能超过 128 个字符");
    }

    private void validateEnums(String provider, String env) {
        if (!SUPPORTED_PROVIDERS.contains(provider.trim())) {
            throw badRequest("模型供应商只支持 bailian/custom");
        }
        if (!SUPPORTED_ENVS.contains(env.trim())) {
            throw badRequest("运行环境只支持 dev/staging/prod");
        }
    }

    private void validateRuntimeParams(AiModelConfigRequest request) {
        validateRatio(defaultBigDecimal(request.temperature(), DEFAULT_TEMPERATURE), "temperature 必须在 0 到 1 之间");
        validateRatio(defaultBigDecimal(request.topP(), DEFAULT_TOP_P), "topP 必须在 0 到 1 之间");
        int maxTokens = defaultInt(request.maxTokens(), DEFAULT_MAX_TOKENS);
        if (maxTokens < 256 || maxTokens > 8192) {
            throw badRequest("maxTokens 必须在 256 到 8192 之间");
        }
    }

    private void validateTags(List<String> tags) {
        List<String> cleanedTags = cleanTags(tags);
        if (cleanedTags.size() > 20) {
            throw badRequest("标签数量不能超过 20 个");
        }
        if (cleanedTags.stream().anyMatch(tag -> tag.length() > 32)) {
            throw badRequest("单个标签不能超过 32 个字符");
        }
    }

    private void validateMaxLength(String value, int maxLength, String message) {
        if (StringUtils.hasText(value) && value.trim().length() > maxLength) {
            throw badRequest(message);
        }
    }

    private void validateRatio(BigDecimal value, String message) {
        if (value.compareTo(ZERO) < 0 || value.compareTo(ONE) > 0) {
            throw badRequest(message);
        }
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw badRequest(message);
        }
    }

    private AiModelConfigEntity requireEntity(long id) {
        validateId(id);
        return repository.findById(id).orElseThrow(this::notFound);
    }

    private void validateId(long id) {
        if (id <= 0) {
            throw badRequest("模型配置 ID 必须大于 0");
        }
    }

    private AiModelConfigEntity fromRequest(AiModelConfigRequest request, AiModelConfigEntity existing) {
        AiModelConfigEntity entity = new AiModelConfigEntity();
        LocalDateTime now = LocalDateTime.now();
        applyIdentity(entity, existing, now);
        applyBaseConfig(entity, request);
        applyRuntimeConfig(entity, request);
        entity.setApiKey(resolveApiKey(request.apiKey(), existing));
        entity.setDeleted(false);
        return entity;
    }

    private void applyIdentity(AiModelConfigEntity entity, AiModelConfigEntity existing, LocalDateTime now) {
        if (existing == null) {
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            return;
        }
        entity.setId(existing.getId());
        entity.setCreatedAt(existing.getCreatedAt());
        entity.setUpdatedAt(now);
        entity.setLastTestStatus(defaultText(existing.getLastTestStatus(), STATUS_UNTESTED));
        entity.setLastTestLatency(defaultInt(existing.getLastTestLatency(), 0));
        entity.setLastTestMessage(defaultText(existing.getLastTestMessage(), MESSAGE_UNTESTED));
        entity.setLastTestedAt(existing.getLastTestedAt());
    }

    private void applyBaseConfig(AiModelConfigEntity entity, AiModelConfigRequest request) {
        entity.setConfigName(request.configName().trim());
        entity.setDescription(defaultText(request.description(), ""));
        entity.setProvider(request.provider().trim());
        entity.setEndpoint(request.endpoint().trim());
        entity.setModelId(request.modelId().trim());
        entity.setEnv(request.env().trim());
        entity.setEnabled(request.enabled() == null || request.enabled());
    }

    private void applyRuntimeConfig(AiModelConfigEntity entity, AiModelConfigRequest request) {
        entity.setTemperature(defaultBigDecimal(request.temperature(), DEFAULT_TEMPERATURE));
        entity.setTopP(defaultBigDecimal(request.topP(), DEFAULT_TOP_P));
        entity.setMaxTokens(defaultInt(request.maxTokens(), DEFAULT_MAX_TOKENS));
        entity.setTags(cleanTags(request.tags()));
    }

    private String resolveApiKey(String requestApiKey, AiModelConfigEntity existing) {
        if (existing != null && shouldKeepSecret(requestApiKey)) {
            return existing.getApiKey();
        }
        return requestApiKey.trim();
    }

    private boolean shouldKeepSecret(String apiKey) {
        return !StringUtils.hasText(apiKey) || apiKey.trim().startsWith("***");
    }

    private AiModelConfigEntity cloneForCopy(AiModelConfigEntity source) {
        AiModelConfigEntity copy = new AiModelConfigEntity();
        LocalDateTime now = LocalDateTime.now();
        copy.setConfigName(defaultText(source.getConfigName(), "") + " 副本");
        copy.setDescription(defaultText(source.getDescription(), ""));
        copy.setProvider(source.getProvider());
        copy.setEndpoint(source.getEndpoint());
        copy.setApiKey(source.getApiKey());
        copy.setModelId(source.getModelId());
        copy.setEnv(source.getEnv());
        copy.setEnabled(source.isEnabled());
        copy.setTemperature(defaultBigDecimal(source.getTemperature(), DEFAULT_TEMPERATURE));
        copy.setTopP(defaultBigDecimal(source.getTopP(), DEFAULT_TOP_P));
        copy.setMaxTokens(defaultInt(source.getMaxTokens(), DEFAULT_MAX_TOKENS));
        copy.setTags(cleanTags(source.getTags()));
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        resetTestResult(copy);
        return copy;
    }

    private void resetTestResult(AiModelConfigEntity entity) {
        entity.setLastTestStatus(STATUS_UNTESTED);
        entity.setLastTestLatency(0);
        entity.setLastTestMessage(MESSAGE_UNTESTED);
        entity.setLastTestedAt(null);
    }

    private AiModelConfigTestResponse simulateConnection(AiModelConfigEntity entity) {
        LocalDateTime testedAt = LocalDateTime.now();
        int latency = simulatedLatency(entity);
        if (hasConnectionFields(entity)) {
            return new AiModelConfigTestResponse(STATUS_SUCCESS, latency, "连接校验通过", testedAt);
        }
        return new AiModelConfigTestResponse(STATUS_FAILED, latency, "连接配置不完整", testedAt);
    }

    private boolean hasConnectionFields(AiModelConfigEntity entity) {
        return StringUtils.hasText(entity.getEndpoint())
                && StringUtils.hasText(entity.getApiKey())
                && StringUtils.hasText(entity.getModelId());
    }

    private int simulatedLatency(AiModelConfigEntity entity) {
        int endpointSize = defaultText(entity.getEndpoint(), "").length();
        int modelSize = defaultText(entity.getModelId(), "").length();
        return Math.max(1, Math.min(999, endpointSize + modelSize));
    }

    private AiModelConfigResponse toResponse(AiModelConfigEntity entity) {
        return new AiModelConfigResponse(
                entity.getId(),
                defaultText(entity.getConfigName(), ""),
                defaultText(entity.getDescription(), ""),
                defaultText(entity.getProvider(), ""),
                defaultText(entity.getEndpoint(), ""),
                maskApiKey(entity.getApiKey()),
                defaultText(entity.getModelId(), ""),
                defaultText(entity.getEnv(), ""),
                entity.isEnabled(),
                defaultBigDecimal(entity.getTemperature(), DEFAULT_TEMPERATURE),
                defaultBigDecimal(entity.getTopP(), DEFAULT_TOP_P),
                defaultInt(entity.getMaxTokens(), DEFAULT_MAX_TOKENS),
                cleanTags(entity.getTags()),
                defaultText(entity.getLastTestStatus(), STATUS_UNTESTED),
                defaultInt(entity.getLastTestLatency(), 0),
                defaultText(entity.getLastTestMessage(), MESSAGE_UNTESTED),
                entity.getLastTestedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private String maskApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return "";
        }
        String value = apiKey.trim();
        if (value.length() <= 8) {
            return "***";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private BigDecimal defaultBigDecimal(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private List<String> cleanTags(List<String> tags) {
        if (CollUtil.isEmpty(tags)) {
            return List.of();
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private BizException badRequest(String message) {
        return new BizException(400, message);
    }

    private BizException notFound() {
        return new BizException(404, "模型配置不存在");
    }
}