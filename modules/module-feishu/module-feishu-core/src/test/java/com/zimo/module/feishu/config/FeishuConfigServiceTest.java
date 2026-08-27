package com.zimo.module.feishu.config;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zimo.module.feishu.mapper.FeishuConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeishuConfigServiceTest {
    private FeishuConfigMapper mapper;
    private FeishuConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                FeishuConfigEntity.class);
        mapper = mock(FeishuConfigMapper.class);
        service = new FeishuConfigServiceImpl(mapper);
    }

    @Test
    void createsConfigAndMasksSecretFieldsInResponse() {
        FeishuConfigRequest request = new FeishuConfigRequest();
        request.setConfigName("生产飞书应用");
        request.setAppId("cli_prod");
        request.setAppSecret("secret_prod");
        request.setVerificationToken("token_prod");
        request.setEncryptKey("encrypt_prod");
        request.setEnabled(1);
        when(mapper.insert(any(FeishuConfigEntity.class))).thenReturn(1);

        FeishuConfigResponse response = service.create(request);

        ArgumentCaptor<FeishuConfigEntity> captor = ArgumentCaptor.forClass(FeishuConfigEntity.class);
        verify(mapper).insert(captor.capture());
        FeishuConfigEntity inserted = captor.getValue();
        assertThat(inserted.getAppSecret()).isEqualTo("secret_prod");
        assertThat(inserted.getVerificationToken()).isEqualTo("token_prod");
        assertThat(inserted.getEncryptKey()).isEqualTo("encrypt_prod");
        assertThat(response.getAppSecret()).isEqualTo("******");
        assertThat(response.getVerificationToken()).isEqualTo("******");
        assertThat(response.getEncryptKey()).isEqualTo("******");
    }

    @Test
    void updateKeepsOriginalSecretsWhenRequestSecretsAreBlank() {
        FeishuConfigEntity existing = existingConfig();
        when(mapper.selectById(10L)).thenReturn(existing);

        FeishuConfigRequest request = new FeishuConfigRequest();
        request.setConfigName("更新后的配置");
        request.setAppId("cli_updated");
        request.setAppSecret("");
        request.setVerificationToken(null);
        request.setEncryptKey(" ");
        request.setEnabled(0);

        service.update(10L, request);

        ArgumentCaptor<FeishuConfigEntity> captor = ArgumentCaptor.forClass(FeishuConfigEntity.class);
        verify(mapper).updateById(captor.capture());
        FeishuConfigEntity updated = captor.getValue();
        assertThat(updated.getAppSecret()).isEqualTo("secret_original");
        assertThat(updated.getVerificationToken()).isEqualTo("token_original");
        assertThat(updated.getEncryptKey()).isEqualTo("encrypt_original");
        assertThat(updated.getConfigName()).isEqualTo("更新后的配置");
        assertThat(updated.getAppId()).isEqualTo("cli_updated");
    }

    @Test
    void enableDisablesOtherConfigsBeforeEnablingTarget() {
        FeishuConfigEntity existing = existingConfig();
        existing.setEnabled(0);
        when(mapper.selectById(10L)).thenReturn(existing);

        service.enable(10L);

        verify(mapper).disableAll();
        ArgumentCaptor<FeishuConfigEntity> captor = ArgumentCaptor.forClass(FeishuConfigEntity.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getEnabled()).isEqualTo(1);
    }

    @Test
    void pageReturnsMaskedRecords() {
        Page<FeishuConfigEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(existingConfig()));
        page.setTotal(1);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        Page<FeishuConfigResponse> result = service.page(1, 10, "生产", "cli", 1, null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getAppSecret()).isEqualTo("******");
    }

    @Test
    void pageFiltersBoundConfigsWhenBoundIsTrue() {
        when(mapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<>(1, 10));

        service.page(1, 10, null, null, null, true);

        ArgumentCaptor<Wrapper<FeishuConfigEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment().toLowerCase())
                .contains("agent_id is not null");
    }

    @Test
    void pageFiltersUnboundConfigsWhenBoundIsFalse() {
        when(mapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<>(1, 10));

        service.page(1, 10, null, null, null, false);

        ArgumentCaptor<Wrapper<FeishuConfigEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment().toLowerCase())
                .contains("agent_id is null")
                .doesNotContain("agent_id is not null");
    }

    @Test
    void pageKeepsOriginalQueryWhenBoundIsNull() {
        when(mapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(new Page<>(1, 10));

        service.page(1, 10, null, null, null, null);

        ArgumentCaptor<Wrapper<FeishuConfigEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectPage(any(Page.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment().toLowerCase())
                .doesNotContain("agent_id");
    }

    @Test
    void updatesCredentialStatusAndReturnsRawEntity() {
        FeishuConfigEntity existing = existingConfig();
        when(mapper.selectById(10L)).thenReturn(existing);

        FeishuConfigEntity raw = service.getRaw(10L);
        service.updateCredentialStatus(10L, "VALID", LocalDateTime.of(2026, 7, 2, 10, 0));

        ArgumentCaptor<FeishuConfigEntity> captor = ArgumentCaptor.forClass(FeishuConfigEntity.class);
        verify(mapper).updateById(captor.capture());
        assertThat(raw.getAppSecret()).isEqualTo("secret_original");
        assertThat(captor.getValue().getCredentialStatus()).isEqualTo("VALID");
        assertThat(captor.getValue().getLastValidateTime()).isEqualTo(LocalDateTime.of(2026, 7, 2, 10, 0));
    }

    private static FeishuConfigEntity existingConfig() {
        FeishuConfigEntity entity = new FeishuConfigEntity();
        entity.setId(10L);
        entity.setConfigName("原始配置");
        entity.setAppId("cli_original");
        entity.setAppSecret("secret_original");
        entity.setVerificationToken("token_original");
        entity.setEncryptKey("encrypt_original");
        entity.setEnabled(1);
        entity.setRemark("备注");
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }
}
