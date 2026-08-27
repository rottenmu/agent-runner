package com.zimo.module.feishu.config;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zimo.module.feishu.mapper.FeishuConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeishuConfigServiceImplTest {

    @Test
    void createThrowsWhenInsertDoesNotAffectRows() {
        FeishuConfigMapper mapper = mock(FeishuConfigMapper.class);
        when(mapper.insert(any(FeishuConfigEntity.class))).thenReturn(0);
        FeishuConfigServiceImpl service = new FeishuConfigServiceImpl(mapper);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("feishu config was not saved");
    }

    @Test
    void createAllowsOptionalTokenAndEncryptKey() {
        FeishuConfigMapper mapper = mock(FeishuConfigMapper.class);
        when(mapper.insert(any(FeishuConfigEntity.class))).thenReturn(1);
        FeishuConfigServiceImpl service = new FeishuConfigServiceImpl(mapper);
        FeishuConfigRequest request = createRequest();
        request.setVerificationToken(null);
        request.setEncryptKey("");
        request.setTenantKey(null);
        request.setTenantName(null);
        request.setPermissionScopes(null);
        request.setEventSubscriptions(null);

        FeishuConfigResponse response = service.create(request);

        ArgumentCaptor<FeishuConfigEntity> entityCaptor = ArgumentCaptor.forClass(FeishuConfigEntity.class);
        verify(mapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getVerificationToken()).isNull();
        assertThat(entityCaptor.getValue().getEncryptKey()).isEmpty();
        assertThat(response.getVerificationToken()).isEmpty();
        assertThat(response.getEncryptKey()).isEmpty();
    }

    @Test
    void bindsAndUnbindsAgentThroughDedicatedMapperUpdate() {
        FeishuConfigMapper mapper = mock(FeishuConfigMapper.class);
        FeishuConfigEntity entity = activeConfig();
        entity.setAgentId("a-old");
        when(mapper.selectById(10L)).thenReturn(entity);
        when(mapper.updateAgentBinding(10L, "a-new")).thenReturn(1);
        when(mapper.updateAgentBinding(10L, null)).thenReturn(1);
        FeishuConfigServiceImpl service = new FeishuConfigServiceImpl(mapper);

        FeishuConfigResponse bound = service.bindAgent(10L, "  a-new  ");

        assertThat(bound.getAgentId()).isEqualTo("a-new");

        FeishuConfigResponse unbound = service.bindAgent(10L, " ");

        assertThat(unbound.getAgentId()).isNull();
        verify(mapper).updateAgentBinding(10L, "a-new");
        verify(mapper).updateAgentBinding(10L, null);
        verify(mapper, never()).updateById(any(FeishuConfigEntity.class));
    }

    @Test
    void rejectsAgentIdLongerThanSixtyFourCharacters() {
        FeishuConfigMapper mapper = mock(FeishuConfigMapper.class);
        when(mapper.selectById(10L)).thenReturn(activeConfig());
        FeishuConfigServiceImpl service = new FeishuConfigServiceImpl(mapper);

        assertThatThrownBy(() -> service.bindAgent(10L, "a".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId");

        verify(mapper, never()).updateAgentBinding(any(), any());
        verify(mapper, never()).updateById(any(FeishuConfigEntity.class));
    }

    @Test
    void bindAgentThrowsWhenDedicatedUpdateDoesNotAffectExactlyOneRow() {
        FeishuConfigMapper mapper = mock(FeishuConfigMapper.class);
        when(mapper.selectById(10L)).thenReturn(activeConfig());
        when(mapper.updateAgentBinding(10L, "a-new")).thenReturn(0);
        FeishuConfigServiceImpl service = new FeishuConfigServiceImpl(mapper);

        assertThatThrownBy(() -> service.bindAgent(10L, "a-new"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("feishu config");

        verify(mapper, never()).updateById(any(FeishuConfigEntity.class));
    }

    @Test
    void updateKeepsExistingAgentBinding() {
        FeishuConfigMapper mapper = mock(FeishuConfigMapper.class);
        FeishuConfigEntity entity = activeConfig();
        entity.setAgentId("a-bound");
        when(mapper.selectById(10L)).thenReturn(entity);
        FeishuConfigServiceImpl service = new FeishuConfigServiceImpl(mapper);
        FeishuConfigRequest request = createRequest();

        service.update(10L, request);

        assertThat(entity.getAgentId()).isEqualTo("a-bound");
    }

    @Test
    void getActiveConfigSummaryReturnsMaskedEnabledConfig() {
        initTableInfo();
        FeishuConfigMapper mapper = mock(FeishuConfigMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(activeConfig()));
        FeishuConfigServiceImpl service = new FeishuConfigServiceImpl(mapper);

        FeishuConfigResponse response = service.getActiveConfigSummary();

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getAppId()).isEqualTo("cli_original");
        assertThat(response.getAppSecret()).isEqualTo("******");
        assertThat(response.getVerificationToken()).isEqualTo("******");
        assertThat(response.getEncryptKey()).isEqualTo("******");
        assertThat(response.getEnabled()).isEqualTo(1);
        assertThat(response.getTenantKey()).isEqualTo("tenant_a");
        assertThat(response.getTenantName()).isEqualTo("测试租户");
        assertThat(response.getCredentialStatus()).isEqualTo("VALID");
        ArgumentCaptor<Wrapper<FeishuConfigEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("enabled")
                .contains("deleted")
                .contains("LIMIT 1");
    }

    @Test
    void getActiveConfigSummaryReturnsNullWhenNoActiveConfigExists() {
        FeishuConfigMapper mapper = mock(FeishuConfigMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        FeishuConfigServiceImpl service = new FeishuConfigServiceImpl(mapper);

        FeishuConfigResponse response = service.getActiveConfigSummary();

        assertThat(response).isNull();
    }

    @Test
    void disableActiveUpdatesCurrentActiveConfigAndReturnsMaskedResponse() {
        FeishuConfigMapper mapper = mock(FeishuConfigMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(activeConfig()));
        FeishuConfigServiceImpl service = new FeishuConfigServiceImpl(mapper);

        FeishuConfigResponse response = service.disableActive();

        ArgumentCaptor<FeishuConfigEntity> entityCaptor = ArgumentCaptor.forClass(FeishuConfigEntity.class);
        verify(mapper).updateById(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getEnabled()).isEqualTo(0);
        assertThat(response.getEnabled()).isEqualTo(0);
        assertThat(response.getAppSecret()).isEqualTo("******");
        assertThat(response.getVerificationToken()).isEqualTo("******");
        assertThat(response.getEncryptKey()).isEqualTo("******");
    }

    @Test
    void disableActiveReturnsNullWhenNoActiveConfigExists() {
        FeishuConfigMapper mapper = mock(FeishuConfigMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        FeishuConfigServiceImpl service = new FeishuConfigServiceImpl(mapper);

        FeishuConfigResponse response = service.disableActive();

        assertThat(response).isNull();
        verify(mapper, never()).updateById(any(FeishuConfigEntity.class));
    }

    private static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                FeishuConfigEntity.class);
    }

    private static FeishuConfigEntity activeConfig() {
        FeishuConfigEntity entity = new FeishuConfigEntity();
        entity.setId(10L);
        entity.setConfigName("原始配置");
        entity.setAppId("cli_original");
        entity.setAppSecret("secret_original");
        entity.setVerificationToken("token_original");
        entity.setEncryptKey("encrypt_original");
        entity.setEnabled(1);
        entity.setTenantKey("tenant_a");
        entity.setTenantName("测试租户");
        entity.setCredentialStatus("VALID");
        entity.setDeleted(0);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }

    private static FeishuConfigRequest createRequest() {
        FeishuConfigRequest request = new FeishuConfigRequest();
        request.setConfigName("prod");
        request.setAppId("cli_prod");
        request.setAppSecret("secret");
        request.setVerificationToken("token");
        request.setEncryptKey("encrypt");
        request.setEnabled(0);
        return request;
    }
}
