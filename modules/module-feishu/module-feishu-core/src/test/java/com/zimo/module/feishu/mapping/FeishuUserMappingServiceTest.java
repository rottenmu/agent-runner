package com.zimo.module.feishu.mapping;

import com.zimo.module.feishu.channel.FeishuAgentCommandMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeishuUserMappingServiceTest {

    @Test
    void resolvesByTenantAndFeishuUserIdFirst() {
        FeishuUserMappingMapper mapper = mock(FeishuUserMappingMapper.class);
        when(mapper.selectBestMapping("tenant_1", "user_1", "open_1", "union_1"))
                .thenReturn(entity("tenant_1", "user_1", "open_1", "union_1", 7L, "planner", 1));
        FeishuUserMappingService service = new FeishuUserMappingService(mapper);

        FeishuAgentCommandMessage message = command("tenant_1", "user_1", "open_1", "union_1");

        FeishuInternalUserSnapshot snapshot = service.findInternalUser(message).orElseThrow();

        assertThat(snapshot.getUserId()).isEqualTo(7L);
        assertThat(snapshot.getAccount()).isEqualTo("planner");
        assertThat(snapshot.getOrganizationId()).isEqualTo("factory_1");
        assertThat(snapshot.getPermissions()).containsExactly("pm:project:list", "wms:stock:list");
    }

    @Test
    void ignoresDisabledMapping() {
        FeishuUserMappingMapper mapper = mock(FeishuUserMappingMapper.class);
        when(mapper.selectBestMapping("tenant_1", "user_1", "open_1", "union_1"))
                .thenReturn(entity("tenant_1", "user_1", "open_1", "union_1", 7L, "planner", 0));
        FeishuUserMappingService service = new FeishuUserMappingService(mapper);

        assertThat(service.findInternalUser(command("tenant_1", "user_1", "open_1", "union_1"))).isEmpty();
    }

    @Test
    void normalizesBlankFeishuIdsBeforeSelectingMapping() {
        FeishuUserMappingMapper mapper = mock(FeishuUserMappingMapper.class);
        when(mapper.selectBestMapping("tenant_1", null, "open_1", "union_1"))
                .thenReturn(entity("tenant_1", null, "open_1", "union_1", 7L, "planner", 1));
        FeishuUserMappingService service = new FeishuUserMappingService(mapper);

        assertThat(service.findInternalUser(command("tenant_1", " ", " open_1 ", "union_1"))).isPresent();

        verify(mapper).selectBestMapping("tenant_1", null, "open_1", "union_1");
    }

    @Test
    void skipsLookupWhenAllFeishuIdsAreBlank() {
        FeishuUserMappingMapper mapper = mock(FeishuUserMappingMapper.class);
        FeishuUserMappingService service = new FeishuUserMappingService(mapper);

        assertThat(service.findInternalUser(command("tenant_1", " ", "", null))).isEmpty();

        verifyNoInteractions(mapper);
    }

    private static FeishuAgentCommandMessage command(String tenantKey, String userId, String openId, String unionId) {
        return new FeishuAgentCommandMessage("msg_1", "chat_1", "group", tenantKey,
                userId, openId, unionId, "query", "query", "text", true);
    }

    private static FeishuUserMappingEntity entity(
            String tenantKey,
            String feishuUserId,
            String openId,
            String unionId,
            Long internalUserId,
            String account,
            Integer enabled) {
        FeishuUserMappingEntity entity = new FeishuUserMappingEntity();
        entity.setTenantKey(tenantKey);
        entity.setFeishuUserId(feishuUserId);
        entity.setFeishuOpenId(openId);
        entity.setFeishuUnionId(unionId);
        entity.setInternalUserId(internalUserId);
        entity.setInternalAccount(account);
        entity.setOrganizationId("factory_1");
        entity.setOrganizationName("factory one");
        entity.setDataScope("FACTORY");
        entity.setPermissions("pm:project:list,wms:stock:list");
        entity.setEnabled(enabled);
        return entity;
    }
}
