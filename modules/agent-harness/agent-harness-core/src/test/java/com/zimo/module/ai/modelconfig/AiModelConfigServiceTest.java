package com.zimo.module.ai.modelconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zimo.framework.common.BizException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiModelConfigServiceTest {

    @Test
    void createValidConfigSavesPlainApiKeyAndReturnsMaskedKey() {
        RecordingRepository repository = new RecordingRepository();
        AiModelConfigService service = new AiModelConfigService(repository);
        AiModelConfigRequest request = validRequest("Qwen Plus", "sk-1234567890");

        AiModelConfigResponse response = service.create(request);

        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.get(0).getApiKey()).isEqualTo("sk-1234567890");
        assertThat(response.configName()).isEqualTo("Qwen Plus");
        assertThat(response.apiKeyMasked()).isEqualTo("sk-1****7890");
    }

    @Test
    void createConfigWithoutApiKeyThrowsBadRequest() {
        AiModelConfigService service = new AiModelConfigService(new RecordingRepository());
        AiModelConfigRequest request = validRequest("Qwen Plus", "");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BizException.class)
                .hasMessage("API Key 必填")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void updateConfigWithBlankApiKeyKeepsOriginalSecret() {
        RecordingRepository repository = new RecordingRepository();
        repository.rows.add(entity(9L, "旧配置", "sk-original-key"));
        AiModelConfigService service = new AiModelConfigService(repository);
        AiModelConfigRequest request = validRequest("新配置", "");

        AiModelConfigResponse response = service.update(9L, request);

        assertThat(repository.updated.get(0).getApiKey()).isEqualTo("sk-original-key");
        assertThat(response.configName()).isEqualTo("新配置");
        assertThat(response.apiKeyMasked()).isEqualTo("sk-o****-key");
    }

    @Test
    void updateConfigWithMaskedPlaceholderKeepsOriginalSecret() {
        RecordingRepository repository = new RecordingRepository();
        repository.rows.add(entity(9L, "旧配置", "sk-original-key"));
        AiModelConfigService service = new AiModelConfigService(repository);
        AiModelConfigRequest request = validRequest("新配置", "***");

        service.update(9L, request);

        assertThat(repository.updated.get(0).getApiKey()).isEqualTo("sk-original-key");
    }

    @Test
    void copyConfigResetsTestResultAndAppendsCopySuffix() {
        RecordingRepository repository = new RecordingRepository();
        AiModelConfigEntity source = entity(7L, "主配置", "sk-source-key");
        source.setLastTestStatus("success");
        source.setLastTestLatency(88);
        repository.rows.add(source);
        AiModelConfigService service = new AiModelConfigService(repository);

        AiModelConfigResponse response = service.copy(7L);

        assertThat(repository.saved.get(0).getConfigName()).isEqualTo("主配置 副本");
        assertThat(repository.saved.get(0).getLastTestStatus()).isEqualTo("untested");
        assertThat(response.lastTestStatus()).isEqualTo("untested");
    }

    @Test
    void testConnectionUpdatesConnectionTestStatus() {
        RecordingRepository repository = new RecordingRepository();
        repository.rows.add(entity(11L, "测试配置", "sk-test-key"));
        AiModelConfigService service = new AiModelConfigService(repository);

        AiModelConfigTestResponse response = service.testConnection(11L);

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.latency()).isGreaterThan(0);
        assertThat(repository.testUpdatedIds).containsExactly(11L);
    }

    @Test
    void exportConfigsNeverReturnsPlainApiKey() {
        RecordingRepository repository = new RecordingRepository();
        repository.rows.add(entity(1L, "导出配置", "sk-export-key"));
        AiModelConfigService service = new AiModelConfigService(repository);

        List<AiModelConfigResponse> exported = service.export(new AiModelConfigQuery("dev", null, null, null));

        assertThat(exported).hasSize(1);
        assertThat(exported.get(0).apiKeyMasked()).isEqualTo("sk-e****-key");
    }

    private static AiModelConfigRequest validRequest(String name, String apiKey) {
        return new AiModelConfigRequest(name, "描述", "bailian",
                "https://dashscope.aliyuncs.com/compatible-mode/v1", apiKey, "qwen-plus",
                "dev", true, new BigDecimal("0.70"), new BigDecimal("0.80"),
                4096, List.of("百炼"));
    }

    private static AiModelConfigEntity entity(Long id, String name, String apiKey) {
        AiModelConfigEntity entity = new AiModelConfigEntity();
        entity.setId(id);
        entity.setConfigName(name);
        entity.setDescription("描述");
        entity.setProvider("bailian");
        entity.setEndpoint("https://dashscope.aliyuncs.com/compatible-mode/v1");
        entity.setApiKey(apiKey);
        entity.setModelId("qwen-plus");
        entity.setEnv("dev");
        entity.setEnabled(true);
        entity.setTemperature(new BigDecimal("0.70"));
        entity.setTopP(new BigDecimal("0.80"));
        entity.setMaxTokens(4096);
        entity.setTags(List.of("百炼"));
        entity.setLastTestStatus("untested");
        entity.setLastTestLatency(0);
        entity.setLastTestMessage("尚未测试");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private static final class RecordingRepository implements AiModelConfigRepository {
        private final List<AiModelConfigEntity> rows = new ArrayList<>();
        private final List<AiModelConfigEntity> saved = new ArrayList<>();
        private final List<AiModelConfigEntity> updated = new ArrayList<>();
        private final List<Long> testUpdatedIds = new ArrayList<>();

        @Override
        public List<AiModelConfigEntity> find(AiModelConfigQuery query) {
            return rows;
        }

        @Override
        public Optional<AiModelConfigEntity> findById(long id) {
            return rows.stream().filter(row -> row.getId() == id).findFirst();
        }

        @Override
        public AiModelConfigEntity insert(AiModelConfigEntity entity) {
            entity.setId((long) (rows.size() + saved.size() + 1));
            saved.add(entity);
            rows.add(entity);
            return entity;
        }

        @Override
        public AiModelConfigEntity update(AiModelConfigEntity entity) {
            updated.add(entity);
            return entity;
        }

        @Override
        public boolean logicalDelete(long id) {
            return rows.removeIf(row -> row.getId() == id);
        }

        @Override
        public void updateTestResult(long id, AiModelConfigTestResponse response) {
            testUpdatedIds.add(id);
        }
    }
}
