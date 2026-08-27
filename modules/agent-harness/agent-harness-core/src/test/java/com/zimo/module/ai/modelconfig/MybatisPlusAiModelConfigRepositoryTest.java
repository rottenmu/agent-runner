package com.zimo.module.ai.modelconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zimo.module.ai.modelconfig.mapper.AiModelConfigMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MybatisPlusAiModelConfigRepositoryTest {

    @BeforeAll
    static void initializeMybatisPlusMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                AiModelConfigEntity.class);
    }

    @Test
    void findDelegatesFilteredQueryToMapper() {
        AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(entity(1L)));

        List<AiModelConfigEntity> rows = repository(mapper)
                .find(new AiModelConfigQuery("prod", "bailian", "enabled", "qwen"));

        assertThat(rows).extracting(AiModelConfigEntity::getId).containsExactly(1L);
        verify(mapper).selectList(any());
    }

    @Test
    void findAcceptsNullQuery() {
        AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());

        assertThat(repository(mapper).find(null)).isEmpty();
    }

    @Test
    void insertSetsTimestampsAndDelegatesToMapper() {
        AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);
        AiModelConfigEntity entity = entity(null);

        repository(mapper).insert(entity);

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isEqualTo(entity.getCreatedAt());
        verify(mapper).insert(entity);
    }

    @Test
    void updateRefreshesTimestampAndDelegatesToMapper() {
        AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);
        AiModelConfigEntity entity = entity(7L);
        LocalDateTime previous = LocalDateTime.of(2026, 7, 23, 9, 0);
        entity.setUpdatedAt(previous);

        repository(mapper).update(entity);

        assertThat(entity.getUpdatedAt()).isAfter(previous);
        verify(mapper).updateById(entity);
    }

    @Test
    void logicalDeleteUsesMybatisPlusLogicalDelete() {
        AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);
        when(mapper.deleteById(9L)).thenReturn(1);

        assertThat(repository(mapper).logicalDelete(9L)).isTrue();
        verify(mapper).deleteById(9L);
    }

    @Test
    void updateTestResultUsesUpdateWrapper() {
        AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);
        when(mapper.update(isNull(), any())).thenReturn(1);
        LocalDateTime testedAt = LocalDateTime.of(2026, 7, 23, 10, 30);

        repository(mapper).updateTestResult(
                9L,
                new AiModelConfigTestResponse("success", 35, "连接成功", testedAt));

        verify(mapper).update(isNull(), any());
    }

    private static MybatisPlusAiModelConfigRepository repository(AiModelConfigMapper mapper) {
        return new MybatisPlusAiModelConfigRepository(mapper);
    }

    private static AiModelConfigEntity entity(Long id) {
        AiModelConfigEntity entity = new AiModelConfigEntity();
        entity.setId(id);
        entity.setConfigName("项目模型");
        entity.setDescription("项目工作台默认模型");
        entity.setProvider("bailian");
        entity.setEndpoint("https://example.invalid/v1");
        entity.setApiKey("test-key");
        entity.setModelId("qwen-plus");
        entity.setEnv("prod");
        entity.setEnabled(true);
        entity.setTemperature(new BigDecimal("0.70"));
        entity.setTopP(new BigDecimal("0.80"));
        entity.setMaxTokens(4096);
        entity.setTags(List.of("项目", "百炼"));
        entity.setLastTestStatus("untested");
        entity.setLastTestLatency(0);
        entity.setLastTestMessage("尚未测试");
        return entity;
    }
}
